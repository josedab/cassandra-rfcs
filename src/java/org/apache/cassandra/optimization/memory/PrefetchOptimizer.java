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
package org.apache.cassandra.optimization.memory;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memory prefetch optimizer for sequential scans.
 * Hints the kernel to prefetch data before it's needed.
 */
public class PrefetchOptimizer
{
    private static final Logger logger = LoggerFactory.getLogger(PrefetchOptimizer.class);

    private static final int DEFAULT_PREFETCH_DISTANCE = 256 * 1024; // 256KB
    private static final int DEFAULT_PREFETCH_SIZE = 64 * 1024; // 64KB

    private final int prefetchDistance;
    private final int prefetchSize;

    public PrefetchOptimizer()
    {
        this(DEFAULT_PREFETCH_DISTANCE, DEFAULT_PREFETCH_SIZE);
    }

    public PrefetchOptimizer(int prefetchDistance, int prefetchSize)
    {
        this.prefetchDistance = prefetchDistance;
        this.prefetchSize = prefetchSize;

        logger.info("PrefetchOptimizer initialized with distance: {} bytes, size: {} bytes",
                    prefetchDistance, prefetchSize);
    }

    /**
     * Scan a file with prefetching
     */
    public void scanWithPrefetch(FileChannel channel, ScanCallback callback) throws IOException
    {
        long position = 0;
        long fileLength = channel.size();

        while (position < fileLength)
        {
            // Prefetch next block
            if (position + prefetchDistance < fileLength)
            {
                prefetch(channel, position + prefetchDistance);
            }

            // Read and process current block
            int toRead = (int) Math.min(prefetchSize, fileLength - position);
            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                position,
                toRead
            );

            callback.process(buffer);

            position += toRead;
        }
    }

    /**
     * Prefetch data at a specific position
     */
    public void prefetch(FileChannel channel, long position) throws IOException
    {
        try
        {
            // Map the region we want to prefetch
            long size = Math.min(prefetchSize, channel.size() - position);
            if (size <= 0)
                return;

            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                position,
                size
            );

            // Force load into memory
            buffer.load();

            logger.debug("Prefetched {} bytes at position {}", size, position);
        }
        catch (IOException e)
        {
            logger.debug("Prefetch failed at position {}", position, e);
            // Don't fail the operation, prefetch is just a hint
        }
    }

    /**
     * Prefetch a memory-mapped buffer
     */
    public void prefetchBuffer(MappedByteBuffer buffer)
    {
        try
        {
            buffer.load();
        }
        catch (Exception e)
        {
            logger.debug("Buffer prefetch failed", e);
        }
    }

    /**
     * Sequential prefetch for a range
     */
    public void prefetchRange(FileChannel channel, long start, long end) throws IOException
    {
        long position = start;

        while (position < end)
        {
            prefetch(channel, position);
            position += prefetchSize;
        }
    }

    /**
     * Callback interface for scanning
     */
    public interface ScanCallback
    {
        void process(MappedByteBuffer buffer) throws IOException;
    }

    /**
     * Get prefetch distance
     */
    public int getPrefetchDistance()
    {
        return prefetchDistance;
    }

    /**
     * Get prefetch size
     */
    public int getPrefetchSize()
    {
        return prefetchSize;
    }
}
