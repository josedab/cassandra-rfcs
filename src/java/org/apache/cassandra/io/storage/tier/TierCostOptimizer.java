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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.io.sstable.format.SSTableReader;

/**
 * Optimizer for calculating and optimizing tier placement costs.
 */
public class TierCostOptimizer
{
    private static final Logger logger = LoggerFactory.getLogger(TierCostOptimizer.class);

    private final TierRegistry tierRegistry;
    private final AccessTracker accessTracker;

    public TierCostOptimizer(TierRegistry tierRegistry, AccessTracker accessTracker)
    {
        this.tierRegistry = tierRegistry;
        this.accessTracker = accessTracker;
    }

    /**
     * Calculate the total monthly cost for current tier distribution.
     *
     * @param sstables collection of SSTables to analyze
     * @return total monthly cost in dollars
     */
    public double calculateCurrentCost(Collection<SSTableReader> sstables)
    {
        double totalCost = 0.0;

        for (SSTableReader sstable : sstables)
        {
            StorageTier tier = getTierForSSTable(sstable);
            if (tier == null)
            {
                continue;
            }

            long sizeBytes = sstable.onDiskLength();
            double sizeGB = sizeBytes / (1024.0 * 1024.0 * 1024.0);

            // Storage cost
            double storageCost = sizeGB * tier.getCostPerGB();

            // Access cost
            AccessStats stats = accessTracker.getStats(sstable);
            double accessFrequency = stats != null ? stats.getRecentAccessRate() : 0.0;
            double accessCost = sizeGB * tier.getAccessCostPerGB() * accessFrequency * 30; // 30 days

            totalCost += storageCost + accessCost;
        }

        return totalCost;
    }

    /**
     * Generate cost optimization recommendations.
     *
     * @param sstables collection of SSTables to analyze
     * @return list of optimization recommendations
     */
    public List<OptimizationRecommendation> generateRecommendations(Collection<SSTableReader> sstables)
    {
        List<OptimizationRecommendation> recommendations = new ArrayList<>();

        for (SSTableReader sstable : sstables)
        {
            StorageTier currentTier = getTierForSSTable(sstable);
            if (currentTier == null)
            {
                continue;
            }

            StorageTier optimalTier = findOptimalTier(sstable);
            if (optimalTier != null && !optimalTier.equals(currentTier))
            {
                double currentCost = calculateCostForPlacement(sstable, currentTier);
                double optimalCost = calculateCostForPlacement(sstable, optimalTier);
                double savings = currentCost - optimalCost;

                if (savings > 0)
                {
                    recommendations.add(new OptimizationRecommendation(
                        sstable,
                        currentTier,
                        optimalTier,
                        savings
                    ));
                }
            }
        }

        // Sort by potential savings (highest first)
        recommendations.sort(Comparator.comparingDouble(OptimizationRecommendation::getSavings).reversed());

        return recommendations;
    }

    /**
     * Find the optimal tier for an SSTable based on cost and access patterns.
     *
     * @param sstable the SSTable to analyze
     * @return the optimal tier
     */
    private StorageTier findOptimalTier(SSTableReader sstable)
    {
        List<StorageTier> tiers = tierRegistry.getTiersByPriority();
        StorageTier optimalTier = null;
        double minCost = Double.MAX_VALUE;

        for (StorageTier tier : tiers)
        {
            if (!tier.isAvailable())
            {
                continue;
            }

            double cost = calculateCostForPlacement(sstable, tier);
            if (cost < minCost)
            {
                minCost = cost;
                optimalTier = tier;
            }
        }

        return optimalTier;
    }

    /**
     * Calculate the monthly cost of placing an SSTable in a specific tier.
     *
     * @param sstable the SSTable
     * @param tier the tier
     * @return monthly cost in dollars
     */
    private double calculateCostForPlacement(SSTableReader sstable, StorageTier tier)
    {
        long sizeBytes = sstable.onDiskLength();
        double sizeGB = sizeBytes / (1024.0 * 1024.0 * 1024.0);

        // Storage cost (monthly)
        double storageCost = sizeGB * tier.getCostPerGB();

        // Access cost (monthly)
        AccessStats stats = accessTracker.getStats(sstable);
        double accessFrequency = stats != null ? stats.getRecentAccessRate() : 0.0;
        double accessCost = sizeGB * tier.getAccessCostPerGB() * accessFrequency * 30; // 30 days

        // Latency penalty (convert latency to cost - higher latency = lower effective value)
        double expectedLatency = tier.getExpectedLatency(StorageTier.OperationType.READ);
        double latencyPenalty = expectedLatency * accessFrequency * 0.001; // Small penalty per ms

        return storageCost + accessCost + latencyPenalty;
    }

    /**
     * Get the current tier for an SSTable.
     *
     * @param sstable the SSTable
     * @return the tier, or null if not found
     */
    private StorageTier getTierForSSTable(SSTableReader sstable)
    {
        // In a real implementation, we would track which tier each SSTable is in
        // For now, return the default tier
        return tierRegistry.getDefaultTier();
    }

    /**
     * Optimization recommendation.
     */
    public static class OptimizationRecommendation
    {
        private final SSTableReader sstable;
        private final StorageTier currentTier;
        private final StorageTier recommendedTier;
        private final double monthlySavings;

        public OptimizationRecommendation(SSTableReader sstable,
                                         StorageTier currentTier,
                                         StorageTier recommendedTier,
                                         double monthlySavings)
        {
            this.sstable = sstable;
            this.currentTier = currentTier;
            this.recommendedTier = recommendedTier;
            this.monthlySavings = monthlySavings;
        }

        public SSTableReader getSSTable()
        {
            return sstable;
        }

        public StorageTier getCurrentTier()
        {
            return currentTier;
        }

        public StorageTier getRecommendedTier()
        {
            return recommendedTier;
        }

        public double getSavings()
        {
            return monthlySavings;
        }

        @Override
        public String toString()
        {
            return String.format("Move %s from %s to %s (save $%.2f/month)",
                               sstable.descriptor, currentTier.getName(),
                               recommendedTier.getName(), monthlySavings);
        }
    }
}
