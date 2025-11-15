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
package org.apache.cassandra.config;

/**
 * Configuration for read/write path optimizations as specified in RFC-0006.
 * Controls virtual threads, direct buffers, lock-free structures, SIMD, and memory optimizations.
 */
public class ReadWriteOptimizationsConfig
{
    /**
     * Virtual thread configuration
     */
    public VirtualThreadsConfig virtual_threads = new VirtualThreadsConfig();

    /**
     * Direct buffer configuration
     */
    public DirectBuffersConfig direct_buffers = new DirectBuffersConfig();

    /**
     * Lock-free data structures configuration
     */
    public LockFreeConfig lock_free = new LockFreeConfig();

    /**
     * SIMD optimizations configuration
     */
    public SIMDConfig simd = new SIMDConfig();

    /**
     * Memory optimizations configuration
     */
    public MemoryConfig memory = new MemoryConfig();

    /**
     * Virtual threads configuration
     */
    public static class VirtualThreadsConfig
    {
        public boolean enabled = false;
        public int carrier_thread_count = 0; // 0 = auto-detect
        public int max_virtual_threads = 10000;
        public String stack_size = "256KB";
    }

    /**
     * Direct buffers configuration
     */
    public static class DirectBuffersConfig
    {
        public boolean enabled = false;
        public String pool_size = "1GB";
        public String chunk_size = "64KB";
        public boolean zero_copy = true;
    }

    /**
     * Lock-free data structures configuration
     */
    public static class LockFreeConfig
    {
        public boolean enabled = false;
        public String memtable_implementation = "lock_free";
        public String cache_implementation = "lock_free";
    }

    /**
     * SIMD optimizations configuration
     */
    public static class SIMDConfig
    {
        public boolean enabled = false;
        public String vector_species = "PREFERRED"; // PREFERRED, MAX, 256, 512
        public boolean crc_vectorized = true;
        public boolean compression_vectorized = true;
    }

    /**
     * Memory optimizations configuration
     */
    public static class MemoryConfig
    {
        public boolean numa_aware = false;
        public boolean huge_pages = false;
        public int prefetch_distance = 256; // In KB
    }

    /**
     * Check if any optimizations are enabled
     */
    public boolean isAnyEnabled()
    {
        return virtual_threads.enabled ||
               direct_buffers.enabled ||
               lock_free.enabled ||
               simd.enabled ||
               memory.numa_aware ||
               memory.huge_pages;
    }

    /**
     * Parse pool size string to bytes
     */
    public static long parseSize(String size)
    {
        if (size == null || size.isEmpty())
            return 0;

        String upper = size.toUpperCase();
        long multiplier = 1;

        if (upper.endsWith("KB"))
        {
            multiplier = 1024;
            upper = upper.substring(0, upper.length() - 2);
        }
        else if (upper.endsWith("MB"))
        {
            multiplier = 1024 * 1024;
            upper = upper.substring(0, upper.length() - 2);
        }
        else if (upper.endsWith("GB"))
        {
            multiplier = 1024 * 1024 * 1024;
            upper = upper.substring(0, upper.length() - 2);
        }

        return Long.parseLong(upper.trim()) * multiplier;
    }
}
