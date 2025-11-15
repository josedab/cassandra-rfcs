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

import java.nio.ByteBuffer;

import jdk.internal.vm.annotation.Contended;

/**
 * Cache-line optimized data structures to prevent false sharing.
 * Ensures frequently accessed variables reside on separate cache lines.
 */
public class CacheLineOptimized
{
    private static final int CACHE_LINE_SIZE = 64;

    /**
     * Padded atomic long to prevent false sharing
     */
    @Contended
    public static class PaddedAtomicLong
    {
        private volatile long value;

        // Padding to prevent false sharing (total 64 bytes)
        private long p1, p2, p3, p4, p5, p6, p7;

        public PaddedAtomicLong()
        {
            this(0);
        }

        public PaddedAtomicLong(long initialValue)
        {
            this.value = initialValue;
        }

        public long get()
        {
            return value;
        }

        public void set(long newValue)
        {
            value = newValue;
        }

        public long incrementAndGet()
        {
            return ++value;
        }

        public long getAndIncrement()
        {
            return value++;
        }

        public long addAndGet(long delta)
        {
            value += delta;
            return value;
        }
    }

    /**
     * Cache-line aligned buffer
     */
    public static class AlignedBuffer
    {
        private final ByteBuffer buffer;
        private final long alignedAddress;

        public AlignedBuffer(int size)
        {
            // Allocate with extra space for alignment
            ByteBuffer unaligned = ByteBuffer.allocateDirect(size + CACHE_LINE_SIZE);

            // Get the native address
            long address = 0;
            if (unaligned instanceof sun.nio.ch.DirectBuffer)
            {
                address = ((sun.nio.ch.DirectBuffer) unaligned).address();
            }

            // Calculate aligned address
            this.alignedAddress = (address + CACHE_LINE_SIZE - 1) & ~(CACHE_LINE_SIZE - 1);

            // Create a slice at the aligned position
            int offset = (int) (alignedAddress - address);
            unaligned.position(offset);
            unaligned.limit(offset + size);
            this.buffer = unaligned.slice();
        }

        public ByteBuffer getBuffer()
        {
            return buffer;
        }

        public long getAlignedAddress()
        {
            return alignedAddress;
        }

        public void clear()
        {
            buffer.clear();
        }
    }

    /**
     * Cache-line padded object base class
     */
    @Contended
    public static abstract class PaddedObject
    {
        // Padding to ensure object doesn't share cache line
        private long p1, p2, p3, p4, p5, p6, p7, p8;
    }

    /**
     * Get the cache line size for this system
     */
    public static int getCacheLineSize()
    {
        return CACHE_LINE_SIZE;
    }

    /**
     * Align a value to the cache line size
     */
    public static long alignToCacheLine(long value)
    {
        return (value + CACHE_LINE_SIZE - 1) & ~(CACHE_LINE_SIZE - 1);
    }

    /**
     * Check if a value is cache-line aligned
     */
    public static boolean isAligned(long value)
    {
        return (value & (CACHE_LINE_SIZE - 1)) == 0;
    }
}
