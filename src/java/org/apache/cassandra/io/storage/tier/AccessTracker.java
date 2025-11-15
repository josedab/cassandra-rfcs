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

import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.storage.tier.MigrationScheduler.MigrationReason;

/**
 * Tracks access patterns for SSTables to support tier placement decisions.
 */
public class AccessTracker
{
    private static final Logger logger = LoggerFactory.getLogger(AccessTracker.class);

    private final Map<SSTableReader, AccessStats> accessStats;
    private final ScheduledExecutorService scheduler;
    private final MigrationScheduler migrationScheduler;
    private final TierRegistry tierRegistry;
    private final AccessTrackerConfig config;
    private final double sampleRate;

    public AccessTracker(MigrationScheduler migrationScheduler,
                        TierRegistry tierRegistry,
                        AccessTrackerConfig config)
    {
        this.accessStats = new ConcurrentHashMap<>();
        this.migrationScheduler = migrationScheduler;
        this.tierRegistry = tierRegistry;
        this.config = config;
        this.sampleRate = config.getSampleRate();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "AccessTracker");
            t.setDaemon(true);
            return t;
        });

        startPeriodicAnalysis();
    }

    /**
     * Record an access to an SSTable.
     *
     * @param sstable the SSTable being accessed
     * @param type the type of access
     */
    public void recordAccess(SSTableReader sstable, AccessType type)
    {
        // Sampling to reduce overhead
        if (Math.random() > sampleRate)
        {
            return;
        }

        AccessStats stats = accessStats.computeIfAbsent(sstable, k -> new AccessStats());
        stats.recordAccess(type);

        // Check if immediate promotion is needed
        if (shouldPromote(stats))
        {
            considerPromotion(sstable, stats);
        }
    }

    /**
     * Get access statistics for an SSTable.
     *
     * @param sstable the SSTable
     * @return the access statistics, or null if not tracked
     */
    public AccessStats getStats(SSTableReader sstable)
    {
        return accessStats.get(sstable);
    }

    /**
     * Remove tracking for an SSTable.
     *
     * @param sstable the SSTable
     */
    public void removeSSTable(SSTableReader sstable)
    {
        accessStats.remove(sstable);
    }

    /**
     * Shutdown the access tracker.
     */
    public void shutdown()
    {
        scheduler.shutdown();
        try
        {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS))
            {
                scheduler.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void startPeriodicAnalysis()
    {
        scheduler.scheduleAtFixedRate(
            this::analyzeDemotions,
            1, // initial delay
            config.getAnalysisIntervalMinutes(),
            TimeUnit.MINUTES
        );
    }

    private void analyzeDemotions()
    {
        logger.debug("Analyzing tier demotions");

        for (Map.Entry<SSTableReader, AccessStats> entry : accessStats.entrySet())
        {
            SSTableReader sstable = entry.getKey();
            AccessStats stats = entry.getValue();

            if (shouldDemote(stats))
            {
                considerDemotion(sstable, stats);
            }

            // Age the statistics
            stats.age();
        }
    }

    private boolean shouldPromote(AccessStats stats)
    {
        // Promote if access rate exceeds threshold
        double accessRate = stats.getRecentAccessRate();
        return accessRate > config.getPromotionThreshold();
    }

    private boolean shouldDemote(AccessStats stats)
    {
        // Demote if access rate is below threshold
        double accessRate = stats.getRecentAccessRate();
        return accessRate < config.getDemotionThreshold();
    }

    private void considerPromotion(SSTableReader sstable, AccessStats stats)
    {
        StorageTier currentTier = getCurrentTier(sstable);
        if (currentTier == null)
        {
            return;
        }

        StorageTier targetTier = tierRegistry.getHigherTier(currentTier);
        if (targetTier != null && targetTier.isAvailable())
        {
            logger.info("Promoting SSTable {} from {} to {} (access rate: {})",
                       sstable.descriptor, currentTier.getName(), targetTier.getName(),
                       stats.getRecentAccessRate());

            migrationScheduler.scheduleMigration(
                sstable,
                currentTier,
                targetTier,
                MigrationReason.HIGH_ACCESS_FREQUENCY
            );
        }
    }

    private void considerDemotion(SSTableReader sstable, AccessStats stats)
    {
        StorageTier currentTier = getCurrentTier(sstable);
        if (currentTier == null)
        {
            return;
        }

        StorageTier targetTier = tierRegistry.getLowerTier(currentTier);
        if (targetTier != null && targetTier.isAvailable())
        {
            logger.info("Demoting SSTable {} from {} to {} (access rate: {})",
                       sstable.descriptor, currentTier.getName(), targetTier.getName(),
                       stats.getRecentAccessRate());

            migrationScheduler.scheduleMigration(
                sstable,
                currentTier,
                targetTier,
                MigrationReason.LOW_ACCESS_FREQUENCY
            );
        }
    }

    private StorageTier getCurrentTier(SSTableReader sstable)
    {
        // In a real implementation, we would track which tier each SSTable is in
        // For now, return the default tier
        return tierRegistry.getDefaultTier();
    }

    /**
     * Access type enumeration.
     */
    public enum AccessType
    {
        READ,
        WRITE,
        COMPACTION
    }

    /**
     * Configuration for access tracker.
     */
    public static class AccessTrackerConfig
    {
        private final double sampleRate;
        private final double promotionThreshold;
        private final double demotionThreshold;
        private final int analysisIntervalMinutes;

        public AccessTrackerConfig(double sampleRate,
                                  double promotionThreshold,
                                  double demotionThreshold,
                                  int analysisIntervalMinutes)
        {
            this.sampleRate = sampleRate;
            this.promotionThreshold = promotionThreshold;
            this.demotionThreshold = demotionThreshold;
            this.analysisIntervalMinutes = analysisIntervalMinutes;
        }

        public double getSampleRate()
        {
            return sampleRate;
        }

        public double getPromotionThreshold()
        {
            return promotionThreshold;
        }

        public double getDemotionThreshold()
        {
            return demotionThreshold;
        }

        public int getAnalysisIntervalMinutes()
        {
            return analysisIntervalMinutes;
        }

        public static AccessTrackerConfig defaults()
        {
            return new AccessTrackerConfig(
                0.01,  // 1% sample rate
                10.0,  // 10 accesses per minute for promotion
                0.1,   // 0.1 accesses per minute for demotion
                60     // Analyze every hour
            );
        }
    }
}
