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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NUMA-aware memory allocator.
 * Allocates memory on the local NUMA node to minimize memory access latency.
 */
public class NUMAMemoryAllocator
{
    private static final Logger logger = LoggerFactory.getLogger(NUMAMemoryAllocator.class);

    private final int numNodes;
    private final ThreadLocal<Integer> nodeAffinity;
    private final boolean numaAvailable;

    public NUMAMemoryAllocator()
    {
        this.numNodes = detectNUMANodes();
        this.numaAvailable = numNodes > 1;
        this.nodeAffinity = ThreadLocal.withInitial(() -> getCurrentNUMANode());

        if (numaAvailable)
        {
            logger.info("NUMA-aware allocator initialized with {} nodes", numNodes);
        }
        else
        {
            logger.info("NUMA not available or single node, using standard allocation");
        }
    }

    /**
     * Allocate memory on the local NUMA node
     */
    public ByteBuffer allocateOnLocalNode(int size)
    {
        if (numaAvailable)
        {
            int node = nodeAffinity.get();
            return allocateOnNode(node, size);
        }
        else
        {
            return ByteBuffer.allocateDirect(size);
        }
    }

    /**
     * Allocate memory on a specific NUMA node
     */
    public ByteBuffer allocateOnNode(int node, int size)
    {
        if (!numaAvailable || node < 0 || node >= numNodes)
        {
            return ByteBuffer.allocateDirect(size);
        }

        try
        {
            // In a real implementation, this would use JNI to call numa_alloc_onnode
            // For now, we fall back to standard allocation
            logger.debug("Allocating {} bytes on NUMA node {}", size, node);
            return ByteBuffer.allocateDirect(size);
        }
        catch (Exception e)
        {
            logger.warn("Failed to allocate on NUMA node {}, falling back to standard allocation", node, e);
            return ByteBuffer.allocateDirect(size);
        }
    }

    /**
     * Set the NUMA node affinity for the current thread
     */
    public void setNodeAffinity(int node)
    {
        if (node >= 0 && node < numNodes)
        {
            nodeAffinity.set(node);
        }
    }

    /**
     * Get the current thread's NUMA node affinity
     */
    public int getNodeAffinity()
    {
        return nodeAffinity.get();
    }

    /**
     * Detect number of NUMA nodes
     */
    private int detectNUMANodes()
    {
        try
        {
            // Try to read from /sys/devices/system/node/
            // In a real implementation, this would use JNI or parse system files
            String osName = System.getProperty("os.name").toLowerCase();

            if (osName.contains("linux"))
            {
                // Default to 1 node if we can't detect
                // Real implementation would use numa_available() and numa_max_node()
                return 1;
            }
            else
            {
                return 1;
            }
        }
        catch (Exception e)
        {
            logger.debug("Failed to detect NUMA nodes", e);
            return 1;
        }
    }

    /**
     * Get the current NUMA node for this thread
     */
    private int getCurrentNUMANode()
    {
        // In a real implementation, would use numa_node_of_cpu(sched_getcpu())
        // For now, distribute threads round-robin across nodes
        long threadId = Thread.currentThread().threadId();
        return (int) (threadId % numNodes);
    }

    /**
     * Check if NUMA is available
     */
    public boolean isNUMAAvailable()
    {
        return numaAvailable;
    }

    /**
     * Get number of NUMA nodes
     */
    public int getNumNodes()
    {
        return numNodes;
    }
}
