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
package org.apache.cassandra.optimization;

import org.apache.cassandra.config.ReadWriteOptimizationsConfig;
import org.apache.cassandra.optimization.concurrent.VirtualThreadExecutor;
import org.apache.cassandra.optimization.io.ZeroCopyBufferPool;
import org.apache.cassandra.optimization.memory.NUMAMemoryAllocator;
import org.apache.cassandra.optimization.memory.PrefetchOptimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for read/write path optimizations.
 * Initializes and coordinates all optimization components based on configuration.
 */
public class ReadWriteOptimizationsManager
{
    private static final Logger logger = LoggerFactory.getLogger(ReadWriteOptimizationsManager.class);

    private static volatile ReadWriteOptimizationsManager instance;

    private final ReadWriteOptimizationsConfig config;
    private VirtualThreadExecutor virtualThreadExecutor;
    private ZeroCopyBufferPool bufferPool;
    private NUMAMemoryAllocator numaAllocator;
    private PrefetchOptimizer prefetchOptimizer;

    private ReadWriteOptimizationsManager(ReadWriteOptimizationsConfig config)
    {
        this.config = config;
        initialize();
    }

    /**
     * Get the singleton instance
     */
    public static ReadWriteOptimizationsManager getInstance()
    {
        if (instance == null)
        {
            throw new IllegalStateException("ReadWriteOptimizationsManager not initialized");
        }
        return instance;
    }

    /**
     * Initialize the manager with configuration
     */
    public static void initialize(ReadWriteOptimizationsConfig config)
    {
        if (instance != null)
        {
            logger.warn("ReadWriteOptimizationsManager already initialized");
            return;
        }

        instance = new ReadWriteOptimizationsManager(config);
    }

    /**
     * Initialize all optimization components
     */
    private void initialize()
    {
        logger.info("Initializing read/write path optimizations");

        if (config.virtual_threads.enabled)
        {
            initializeVirtualThreads();
        }

        if (config.direct_buffers.enabled)
        {
            initializeDirectBuffers();
        }

        if (config.memory.numa_aware)
        {
            initializeNUMA();
        }

        if (config.memory.prefetch_distance > 0)
        {
            initializePrefetch();
        }

        logger.info("Read/write path optimizations initialized successfully");
    }

    /**
     * Initialize virtual thread executor
     */
    private void initializeVirtualThreads()
    {
        logger.info("Initializing virtual thread executor");

        int maxConcurrency = config.virtual_threads.max_virtual_threads;
        virtualThreadExecutor = new VirtualThreadExecutor(maxConcurrency);

        logger.info("Virtual thread executor initialized with max concurrency: {}", maxConcurrency);
    }

    /**
     * Initialize direct buffer pool
     */
    private void initializeDirectBuffers()
    {
        logger.info("Initializing direct buffer pool");

        long poolSize = ReadWriteOptimizationsConfig.parseSize(config.direct_buffers.pool_size);
        int chunkSize = (int) ReadWriteOptimizationsConfig.parseSize(config.direct_buffers.chunk_size);

        bufferPool = new ZeroCopyBufferPool(poolSize, chunkSize);

        logger.info("Direct buffer pool initialized: pool_size={} bytes, chunk_size={} bytes",
                   poolSize, chunkSize);
    }

    /**
     * Initialize NUMA allocator
     */
    private void initializeNUMA()
    {
        logger.info("Initializing NUMA-aware allocator");

        numaAllocator = new NUMAMemoryAllocator();

        if (numaAllocator.isNUMAAvailable())
        {
            logger.info("NUMA allocator initialized with {} nodes", numaAllocator.getNumNodes());
        }
        else
        {
            logger.info("NUMA not available, using standard allocation");
        }
    }

    /**
     * Initialize prefetch optimizer
     */
    private void initializePrefetch()
    {
        logger.info("Initializing prefetch optimizer");

        int prefetchDistance = config.memory.prefetch_distance * 1024; // Convert KB to bytes
        int prefetchSize = 64 * 1024; // 64KB

        prefetchOptimizer = new PrefetchOptimizer(prefetchDistance, prefetchSize);

        logger.info("Prefetch optimizer initialized: distance={} bytes", prefetchDistance);
    }

    /**
     * Shutdown all optimization components
     */
    public void shutdown()
    {
        logger.info("Shutting down read/write path optimizations");

        if (virtualThreadExecutor != null)
        {
            virtualThreadExecutor.shutdown();
        }

        if (bufferPool != null)
        {
            bufferPool.shutdown();
        }

        logger.info("Read/write path optimizations shut down");
    }

    // Getters for components

    public VirtualThreadExecutor getVirtualThreadExecutor()
    {
        return virtualThreadExecutor;
    }

    public ZeroCopyBufferPool getBufferPool()
    {
        return bufferPool;
    }

    public NUMAMemoryAllocator getNUMAAllocator()
    {
        return numaAllocator;
    }

    public PrefetchOptimizer getPrefetchOptimizer()
    {
        return prefetchOptimizer;
    }

    public ReadWriteOptimizationsConfig getConfig()
    {
        return config;
    }

    /**
     * Check if virtual threads are enabled
     */
    public boolean isVirtualThreadsEnabled()
    {
        return config.virtual_threads.enabled && virtualThreadExecutor != null;
    }

    /**
     * Check if direct buffers are enabled
     */
    public boolean isDirectBuffersEnabled()
    {
        return config.direct_buffers.enabled && bufferPool != null;
    }

    /**
     * Check if NUMA is enabled
     */
    public boolean isNUMAEnabled()
    {
        return config.memory.numa_aware && numaAllocator != null;
    }

    /**
     * Check if SIMD is enabled
     */
    public boolean isSIMDEnabled()
    {
        return config.simd.enabled;
    }
}
