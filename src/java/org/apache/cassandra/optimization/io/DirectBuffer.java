/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.optimization.io;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct buffer wrapper for zero-copy I/O operations.
 * Provides efficient memory management and transfer capabilities.
 */
public class DirectBuffer
{
    private static final Logger logger = LoggerFactory.getLogger(DirectBuffer.class);
    private static final Cleaner cleaner = Cleaner.create();

    private final ByteBuffer buffer;
    private final long address;
    private final int size;
    private boolean released = false;

    public DirectBuffer(int size)
    {
        this.size = size;
        this.buffer = ByteBuffer.allocateDirect(size);

        // Get native address if available
        if (buffer instanceof sun.nio.ch.DirectBuffer)
        {
            this.address = ((sun.nio.ch.DirectBuffer) buffer).address();
        }
        else
        {
            this.address = 0;
        }

        // Register cleaner
        cleaner.register(this, new Deallocator(buffer));
    }

    /**
     * Transfer data to a writable channel using zero-copy if possible
     */
    public void transferTo(WritableByteChannel channel) throws IOException
    {
        if (released)
            throw new IllegalStateException("Buffer has been released");

        buffer.flip();

        // Use zero-copy transfer for file channels
        if (channel instanceof FileChannel fc)
        {
            try
            {
                long position = fc.position();
                fc.transferFrom(new ByteBufferChannel(buffer), position, buffer.remaining());
            }
            catch (Exception e)
            {
                // Fallback to regular write
                channel.write(buffer);
            }
        }
        else
        {
            // Regular write for non-file channels
            channel.write(buffer);
        }
    }

    /**
     * Transfer data from a readable channel
     */
    public void transferFrom(ReadableByteChannel channel) throws IOException
    {
        if (released)
            throw new IllegalStateException("Buffer has been released");

        buffer.clear();
        int bytesRead = channel.read(buffer);

        if (bytesRead < 0)
            throw new IOException("End of stream");
    }

    /**
     * Get the underlying ByteBuffer
     */
    public ByteBuffer getBuffer()
    {
        if (released)
            throw new IllegalStateException("Buffer has been released");
        return buffer;
    }

    /**
     * Get native memory address
     */
    public long getAddress()
    {
        return address;
    }

    /**
     * Get buffer size
     */
    public int getSize()
    {
        return size;
    }

    /**
     * Clear the buffer for reuse
     */
    public void clear()
    {
        if (!released)
            buffer.clear();
    }

    /**
     * Release the buffer
     */
    public void release()
    {
        released = true;
    }

    /**
     * Deallocator for cleaning up direct buffers
     */
    private static class Deallocator implements Runnable
    {
        private final ByteBuffer buffer;

        Deallocator(ByteBuffer buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public void run()
        {
            if (buffer instanceof sun.nio.ch.DirectBuffer)
            {
                try
                {
                    ((sun.nio.ch.DirectBuffer) buffer).cleaner().clean();
                }
                catch (Exception e)
                {
                    logger.warn("Error cleaning direct buffer", e);
                }
            }
        }
    }

    /**
     * Simple channel wrapper for ByteBuffer
     */
    private static class ByteBufferChannel implements ReadableByteChannel
    {
        private final ByteBuffer buffer;
        private boolean open = true;

        ByteBufferChannel(ByteBuffer buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException
        {
            if (!open)
                throw new IOException("Channel closed");

            int remaining = buffer.remaining();
            if (remaining == 0)
                return -1;

            int toTransfer = Math.min(remaining, dst.remaining());

            ByteBuffer slice = buffer.slice();
            slice.limit(toTransfer);
            dst.put(slice);
            buffer.position(buffer.position() + toTransfer);

            return toTransfer;
        }

        @Override
        public boolean isOpen()
        {
            return open;
        }

        @Override
        public void close()
        {
            open = false;
        }
    }
}
