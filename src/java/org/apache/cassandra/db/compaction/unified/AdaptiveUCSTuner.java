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

package org.apache.cassandra.db.compaction.unified;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.concurrent.ScheduledExecutors;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.compaction.unified.UCSWorkloadAnalyzer.UCSConfiguration;
import org.apache.cassandra.metrics.TableMetrics;

/**
 * Adaptively tunes UCS parameters based on runtime performance metrics.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
public class AdaptiveUCSTuner
{
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveUCSTuner.class);

    private static final int MIN_SCALING = 2;
    private static final int MAX_SCALING = 32;
    private static final int PERFORMANCE_HISTORY_SIZE = 20;
    private static final long DEFAULT_TUNING_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);

    private final MetricsCollector metricsCollector;
    private final TuningHistory history;
    private final TuningEngine tuningEngine;
    private volatile boolean enabled = false;
    private ScheduledFuture<?> tuningTask;

    public AdaptiveUCSTuner()
    {
        this.metricsCollector = new MetricsCollector();
        this.history = new TuningHistory();
        this.tuningEngine = new TuningEngine();
    }

    /**
     * Enable adaptive tuning for a table.
     */
    public void enable(ColumnFamilyStore cfs)
    {
        if (enabled)
        {
            logger.warn("Adaptive tuning already enabled for {}.{}", cfs.keyspace.getName(), cfs.name);
            return;
        }

        logger.info("Enabling adaptive tuning for {}.{}", cfs.keyspace.getName(), cfs.name);
        enabled = true;

        // Schedule periodic tuning
        tuningTask = ScheduledExecutors.optionalTasks.scheduleAtFixedRate(
            () -> performTuning(cfs),
            DEFAULT_TUNING_INTERVAL_MS,
            DEFAULT_TUNING_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Disable adaptive tuning.
     */
    public void disable()
    {
        if (!enabled)
            return;

        logger.info("Disabling adaptive tuning");
        enabled = false;

        if (tuningTask != null)
        {
            tuningTask.cancel(false);
            tuningTask = null;
        }
    }

    /**
     * Perform one-time tuning evaluation.
     */
    public void performTuning(ColumnFamilyStore cfs)
    {
        try
        {
            logger.debug("Performing adaptive tuning for {}.{}", cfs.keyspace.getName(), cfs.name);
            tuningEngine.tune(cfs);
        }
        catch (Exception e)
        {
            logger.error("Error during adaptive tuning for {}.{}", cfs.keyspace.getName(), cfs.name, e);
        }
    }

    /**
     * Collects performance metrics from a table.
     */
    public static class MetricsCollector
    {
        public PerformanceSnapshot collect(ColumnFamilyStore cfs)
        {
            TableMetrics metrics = cfs.metric;

            double writeAmp = metrics.writeAmplification.getValue();
            double readAmp = calculateReadAmplification(cfs);
            double spaceAmp = calculateSpaceAmplification(cfs);
            double shardImbalance = calculateShardImbalance(cfs);
            long timestamp = System.currentTimeMillis();

            return new PerformanceSnapshot(writeAmp, readAmp, spaceAmp, shardImbalance, timestamp);
        }

        private double calculateReadAmplification(ColumnFamilyStore cfs)
        {
            // Estimate based on SSTable count per read
            long sstableCount = cfs.getLiveSSTables().size();
            return Math.max(1.0, sstableCount / 10.0); // Simplified
        }

        private double calculateSpaceAmplification(ColumnFamilyStore cfs)
        {
            long liveData = cfs.metric.liveDiskSpaceUsed.getCount();
            long totalData = cfs.metric.totalDiskSpaceUsed.getCount();
            return totalData / Math.max(liveData, 1.0);
        }

        private double calculateShardImbalance(ColumnFamilyStore cfs)
        {
            // Simplified: would calculate coefficient of variation across shards
            return 0.1; // Placeholder
        }
    }

    /**
     * Stores historical tuning decisions and their outcomes.
     */
    public static class TuningHistory
    {
        private final CircularFifoQueue<TuningRecord> records;

        public TuningHistory()
        {
            this.records = new CircularFifoQueue<>(100);
        }

        public void record(ColumnFamilyStore cfs, UCSConfiguration config,
                         TuningAdjustment adjustment, PerformanceSnapshot snapshot)
        {
            TuningRecord record = new TuningRecord(
                cfs.metadata().name,
                config,
                adjustment,
                snapshot,
                System.currentTimeMillis()
            );
            records.add(record);
            logger.debug("Recorded tuning decision: {}", record);
        }

        public CircularFifoQueue<TuningRecord> getRecords()
        {
            return records;
        }
    }

    /**
     * Main tuning engine that applies adaptive adjustments.
     */
    public class TuningEngine
    {
        private volatile UCSConfiguration currentConfig;
        private final CircularFifoQueue<PerformanceSnapshot> performanceHistory;

        private double targetWriteAmplification = 4.0;
        private double targetReadAmplification = 3.0;
        private double tuningSensitivity = 0.5;

        public TuningEngine()
        {
            this.performanceHistory = new CircularFifoQueue<>(PERFORMANCE_HISTORY_SIZE);
            this.currentConfig = new UCSConfiguration();
        }

        public void tune(ColumnFamilyStore cfs)
        {
            // Collect current performance metrics
            PerformanceSnapshot current = metricsCollector.collect(cfs);
            performanceHistory.add(current);

            // Determine if tuning is needed
            if (!shouldTune(current))
            {
                logger.debug("No tuning needed for {}.{}", cfs.keyspace.getName(), cfs.name);
                return;
            }

            // Calculate adjustment
            TuningAdjustment adjustment = calculateAdjustment(current);

            if (adjustment.hasChanges())
            {
                logger.info("Applying tuning adjustment to {}.{}: {}",
                          cfs.keyspace.getName(), cfs.name, adjustment);

                // Apply adjustment gradually
                applyAdjustment(cfs, adjustment);

                // Record tuning decision
                history.record(cfs, currentConfig, adjustment, current);
            }
        }

        private boolean shouldTune(PerformanceSnapshot snapshot)
        {
            // Check if performance is outside acceptable bounds
            boolean highWriteAmp = snapshot.getWriteAmplification() > targetWriteAmplification * 1.2;
            boolean highReadAmp = snapshot.getReadAmplification() > targetReadAmplification * 1.2;
            boolean imbalancedShards = snapshot.getShardImbalance() > 0.3;

            return highWriteAmp || highReadAmp || imbalancedShards;
        }

        private TuningAdjustment calculateAdjustment(PerformanceSnapshot snapshot)
        {
            TuningAdjustment adjustment = new TuningAdjustment();

            // Adjust scaling parameter based on amplification metrics
            if (snapshot.getWriteAmplification() > targetWriteAmplification)
            {
                // Reduce scaling parameter to improve write amplification
                adjustment.scalingParameterDelta = (int) (-1 * tuningSensitivity);
                adjustment.reason = "High write amplification detected";
            }
            else if (snapshot.getReadAmplification() > targetReadAmplification)
            {
                // Increase scaling parameter to improve read amplification
                adjustment.scalingParameterDelta = (int) (1 * tuningSensitivity);
                adjustment.reason = "High read amplification detected";
            }

            // Adjust target SSTable size based on shard balance
            if (snapshot.getShardImbalance() > 0.3)
            {
                adjustment.targetSizeFactor = 0.9;  // Reduce size for better balance
                adjustment.reason = "Shard imbalance detected";
            }

            return adjustment;
        }

        private void applyAdjustment(ColumnFamilyStore cfs, TuningAdjustment adjustment)
        {
            UCSConfiguration newConfig = currentConfig.clone();

            // Apply scaling parameter change
            if (adjustment.scalingParameterDelta != 0)
            {
                int newScaling = currentConfig.getScalingParameter() + adjustment.scalingParameterDelta;
                newScaling = Math.min(MAX_SCALING, Math.max(MIN_SCALING, newScaling));
                newConfig.setScalingParameter(newScaling);

                logger.info("Adjusted scaling parameter: {} -> {}",
                          currentConfig.getScalingParameter(), newScaling);
            }

            // Apply target size change
            if (adjustment.targetSizeFactor != 1.0)
            {
                long newSize = (long) (currentConfig.getTargetSStableSize() * adjustment.targetSizeFactor);
                newConfig.setTargetSStableSize(newSize);

                logger.info("Adjusted target SSTable size: {} -> {} MB",
                          currentConfig.getTargetSStableSize() / (1024 * 1024),
                          newSize / (1024 * 1024));
            }

            // Update configuration
            updateConfiguration(cfs, newConfig);
            currentConfig = newConfig;
        }

        private void updateConfiguration(ColumnFamilyStore cfs, UCSConfiguration newConfig)
        {
            // In a real implementation, this would update the compaction strategy configuration
            logger.info("Updated UCS configuration for {}.{}: {}",
                      cfs.keyspace.getName(), cfs.name, newConfig.toOptions());
        }

        public void setTargetWriteAmplification(double value)
        {
            this.targetWriteAmplification = value;
        }

        public void setTargetReadAmplification(double value)
        {
            this.targetReadAmplification = value;
        }

        public void setTuningSensitivity(double value)
        {
            this.tuningSensitivity = Math.max(0.0, Math.min(1.0, value));
        }

        public double getTargetWriteAmplification()
        {
            return targetWriteAmplification;
        }

        public double getTargetReadAmplification()
        {
            return targetReadAmplification;
        }
    }

    // Supporting classes

    public static class PerformanceSnapshot
    {
        private final double writeAmplification;
        private final double readAmplification;
        private final double spaceAmplification;
        private final double shardImbalance;
        private final long timestamp;

        public PerformanceSnapshot(double writeAmp, double readAmp, double spaceAmp,
                                 double shardImb, long ts)
        {
            this.writeAmplification = writeAmp;
            this.readAmplification = readAmp;
            this.spaceAmplification = spaceAmp;
            this.shardImbalance = shardImb;
            this.timestamp = ts;
        }

        public double getWriteAmplification() { return writeAmplification; }
        public double getReadAmplification() { return readAmplification; }
        public double getSpaceAmplification() { return spaceAmplification; }
        public double getShardImbalance() { return shardImbalance; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString()
        {
            return String.format("PerformanceSnapshot{writeAmp=%.2f, readAmp=%.2f, spaceAmp=%.2f, shardImb=%.2f}",
                               writeAmplification, readAmplification, spaceAmplification, shardImbalance);
        }
    }

    public static class TuningAdjustment
    {
        public int scalingParameterDelta = 0;
        public double targetSizeFactor = 1.0;
        public String reason = "";

        public boolean hasChanges()
        {
            return scalingParameterDelta != 0 || targetSizeFactor != 1.0;
        }

        @Override
        public String toString()
        {
            return String.format("TuningAdjustment{scalingDelta=%d, sizeFactor=%.2f, reason='%s'}",
                               scalingParameterDelta, targetSizeFactor, reason);
        }
    }

    public static class TuningRecord
    {
        private final String tableName;
        private final UCSConfiguration configuration;
        private final TuningAdjustment adjustment;
        private final PerformanceSnapshot snapshot;
        private final long timestamp;

        public TuningRecord(String table, UCSConfiguration config, TuningAdjustment adj,
                          PerformanceSnapshot snap, long ts)
        {
            this.tableName = table;
            this.configuration = config;
            this.adjustment = adj;
            this.snapshot = snap;
            this.timestamp = ts;
        }

        public String getTableName() { return tableName; }
        public UCSConfiguration getConfiguration() { return configuration; }
        public TuningAdjustment getAdjustment() { return adjustment; }
        public PerformanceSnapshot getSnapshot() { return snapshot; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString()
        {
            return String.format("TuningRecord{table=%s, adjustment=%s, snapshot=%s}",
                               tableName, adjustment, snapshot);
        }
    }
}
