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

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.io.sstable.format.SSTableReader;

/**
 * Main engine for managing tiered storage operations.
 */
public class TierManagementEngine
{
    private static final Logger logger = LoggerFactory.getLogger(TierManagementEngine.class);
    private static final TierManagementEngine instance = new TierManagementEngine();

    private final TierRegistry tierRegistry;
    private final MigrationScheduler migrationScheduler;
    private final AccessTracker accessTracker;
    private final TierSelector tierSelector;

    private volatile boolean enabled = false;

    private TierManagementEngine()
    {
        this.tierRegistry = TierRegistry.instance();
        this.migrationScheduler = new MigrationScheduler(MigrationScheduler.MigrationConfig.defaults());
        this.accessTracker = new AccessTracker(
            migrationScheduler,
            tierRegistry,
            AccessTracker.AccessTrackerConfig.defaults()
        );
        this.tierSelector = new TierSelector();
    }

    public static TierManagementEngine instance()
    {
        return instance;
    }

    /**
     * Enable tiered storage.
     */
    public void enable()
    {
        enabled = true;
        logger.info("Tiered storage enabled");
    }

    /**
     * Disable tiered storage.
     */
    public void disable()
    {
        enabled = false;
        logger.info("Tiered storage disabled");
    }

    /**
     * Check if tiered storage is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Get the tier registry.
     *
     * @return the tier registry
     */
    public TierRegistry getTierRegistry()
    {
        return tierRegistry;
    }

    /**
     * Get the migration scheduler.
     *
     * @return the migration scheduler
     */
    public MigrationScheduler getMigrationScheduler()
    {
        return migrationScheduler;
    }

    /**
     * Get the access tracker.
     *
     * @return the access tracker
     */
    public AccessTracker getAccessTracker()
    {
        return accessTracker;
    }

    /**
     * Get the tier selector.
     *
     * @return the tier selector
     */
    public TierSelector getTierSelector()
    {
        return tierSelector;
    }

    /**
     * Shutdown the tier management engine.
     */
    public void shutdown()
    {
        migrationScheduler.shutdown();
        accessTracker.shutdown();
        logger.info("Tier management engine shutdown");
    }

    /**
     * Selects appropriate tiers for SSTables based on policies and access patterns.
     */
    public class TierSelector
    {
        /**
         * Select a tier for a new SSTable based on table policy.
         *
         * @param sstableAge age of the SSTable in milliseconds
         * @param estimatedSize estimated size of the SSTable
         * @return the selected tier
         */
        public StorageTier selectTierForNewSSTable(long sstableAge, long estimatedSize)
        {
            if (!enabled)
            {
                return tierRegistry.getDefaultTier();
            }

            // Age-based selection
            List<StorageTier> tiers = tierRegistry.getTiersByPriority();

            for (StorageTier tier : tiers)
            {
                if (sstableAge <= tier.getConfig().getMaxAge())
                {
                    if (tier.isAvailable() && tier.getAvailableSpace() > estimatedSize)
                    {
                        return tier;
                    }
                }
            }

            // Fallback to default tier
            return tierRegistry.getDefaultTier();
        }

        /**
         * Select the optimal tier for reading from a collection of SSTables.
         *
         * @param sstables the SSTables to read from
         * @return the tier with most data
         */
        public StorageTier selectTierForRead(Collection<SSTableReader> sstables)
        {
            if (!enabled || sstables.isEmpty())
            {
                return tierRegistry.getDefaultTier();
            }

            // Group SSTables by tier and select tier with most data
            Map<StorageTier, List<SSTableReader>> byTier = new HashMap<>();

            for (SSTableReader sstable : sstables)
            {
                StorageTier tier = getTierForSSTable(sstable);
                byTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(sstable);
            }

            // Select tier with most data to minimize cross-tier reads
            return byTier.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                    (list1, list2) -> Long.compare(
                        list1.stream().mapToLong(SSTableReader::onDiskLength).sum(),
                        list2.stream().mapToLong(SSTableReader::onDiskLength).sum())))
                .map(Map.Entry::getKey)
                .orElse(tierRegistry.getDefaultTier());
        }

        /**
         * Get the tier for a specific SSTable.
         *
         * @param sstable the SSTable
         * @return the tier containing this SSTable
         */
        private StorageTier getTierForSSTable(SSTableReader sstable)
        {
            // In a real implementation, we would track which tier each SSTable is in
            // For now, return the default tier
            return tierRegistry.getDefaultTier();
        }
    }
}
