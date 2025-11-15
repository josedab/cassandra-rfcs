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

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zero-copy buffer pool for direct buffer management.
 * Maintains a pool of direct ByteBuffers to avoid allocation overhead
 * and enable zero-copy I/O operations.
 */
public class ZeroCopyBufferPool
{
    private static final Logger logger = LoggerFactory.getLogger(ZeroCopyBufferPool.class);

    private final Queue<DirectBuffer> pool;
    private final long totalCapacity;
    private final AtomicLong allocated;
    private final int chunkSize;

    public ZeroCopyBufferPool(long totalCapacity, int chunkSize)
    {
        this.totalCapacity = totalCapacity;
        this.chunkSize = chunkSize;
        this.pool = new ConcurrentLinkedQueue<>();
        this.allocated = new AtomicLong(0);

        logger.info("ZeroCopyBufferPool initialized with capacity: {} bytes, chunk size: {} bytes",
                    totalCapacity, chunkSize);
    }

    /**
     * Acquire a buffer from the pool
     */
    public DirectBuffer acquire()
    {
        DirectBuffer buffer = pool.poll();

        if (buffer == null)
        {
            // Check if we can allocate more
            if (allocated.get() + chunkSize <= totalCapacity)
            {
                buffer = new DirectBuffer(chunkSize);
                allocated.addAndGet(chunkSize);
                logger.debug("Allocated new direct buffer, total allocated: {} bytes", allocated.get());
            }
            else
            {
                throw new IllegalStateException("Buffer pool exhausted");
            }
        }

        buffer.clear();
        return buffer;
    }

    /**
     * Return a buffer to the pool
     */
    public void release(DirectBuffer buffer)
    {
        if (buffer != null)
        {
            buffer.clear();
            pool.offer(buffer);
        }
    }

    /**
     * Get pool statistics
     */
    public PoolStats getStats()
    {
        return new PoolStats(
            allocated.get(),
            totalCapacity,
            pool.size(),
            chunkSize
        );
    }

    /**
     * Shutdown the pool and release all buffers
     */
    public void shutdown()
    {
        DirectBuffer buffer;
        while ((buffer = pool.poll()) != null)
        {
            buffer.release();
        }
        allocated.set(0);
    }

    public static class PoolStats
    {
        public final long allocatedBytes;
        public final long totalCapacity;
        public final int availableBuffers;
        public final int chunkSize;

        public PoolStats(long allocatedBytes, long totalCapacity, int availableBuffers, int chunkSize)
        {
            this.allocatedBytes = allocatedBytes;
            this.totalCapacity = totalCapacity;
            this.availableBuffers = availableBuffers;
            this.chunkSize = chunkSize;
        }

        @Override
        public String toString()
        {
            return String.format("PoolStats{allocated=%d, capacity=%d, available=%d, chunkSize=%d}",
                               allocatedBytes, totalCapacity, availableBuffers, chunkSize);
        }
    }
}
