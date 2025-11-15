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
package org.apache.cassandra.io.storage.tier;

import java.util.concurrent.CompletableFuture;

import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.sstable.format.SSTableWriter;
import org.apache.cassandra.io.sstable.metadata.MetadataComponent;

/**
 * Abstract base class for storage tiers in Cassandra's tiered storage system.
 *
 * A storage tier represents a logical storage layer (e.g., NVMe, SSD, HDD, object storage)
 * with specific performance characteristics and cost attributes. Each tier can store
 * SSTables and provides operations for reading, writing, and deleting data.
 */
public abstract class StorageTier
{
    protected final String name;
    protected final int priority;
    protected final TierConfiguration config;
    protected final TierMetrics metrics;

    protected StorageTier(String name, int priority, TierConfiguration config)
    {
        this.name = name;
        this.priority = priority;
        this.config = config;
        this.metrics = new TierMetrics(name);
    }

    /**
     * Write an SSTable to this tier.
     *
     * @param writer the SSTable writer
     * @param metadata the SSTable metadata
     * @return a future containing the SSTable reader for the written data
     */
    public abstract CompletableFuture<SSTableReader> writeSSTable(SSTableWriter writer,
                                                                   MetadataComponent metadata);

    /**
     * Read an SSTable from this tier.
     *
     * @param descriptor the SSTable descriptor
     * @return a future containing the SSTable reader
     */
    public abstract CompletableFuture<SSTableReader> readSSTable(Descriptor descriptor);

    /**
     * Delete an SSTable from this tier.
     *
     * @param descriptor the SSTable descriptor
     * @return a future that completes when deletion is done
     */
    public abstract CompletableFuture<Void> deleteSSTable(Descriptor descriptor);

    /**
     * Check if this tier is available for read/write operations.
     *
     * @return true if the tier is available
     */
    public abstract boolean isAvailable();

    /**
     * Get the available space in this tier (in bytes).
     *
     * @return available space in bytes, or Long.MAX_VALUE if unlimited
     */
    public abstract long getAvailableSpace();

    /**
     * Get the expected latency for an operation type.
     *
     * @param op the operation type
     * @return expected latency in milliseconds
     */
    public abstract double getExpectedLatency(OperationType op);

    /**
     * Get the tier name.
     *
     * @return the tier name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Get the tier priority (lower is higher priority).
     *
     * @return the tier priority
     */
    public int getPriority()
    {
        return priority;
    }

    /**
     * Get the tier configuration.
     *
     * @return the tier configuration
     */
    public TierConfiguration getConfig()
    {
        return config;
    }

    /**
     * Get the tier metrics.
     *
     * @return the tier metrics
     */
    public TierMetrics getMetrics()
    {
        return metrics;
    }

    /**
     * Get the cost per GB for storing data in this tier.
     *
     * @return cost per GB in dollars
     */
    public double getCostPerGB()
    {
        return config.getCostPerGB();
    }

    /**
     * Get the access cost per GB for reading data from this tier.
     *
     * @return access cost per GB in dollars
     */
    public double getAccessCostPerGB()
    {
        return config.getAccessCostPerGB();
    }

    /**
     * Operation types for latency estimation.
     */
    public enum OperationType
    {
        READ,
        WRITE,
        DELETE
    }
}
