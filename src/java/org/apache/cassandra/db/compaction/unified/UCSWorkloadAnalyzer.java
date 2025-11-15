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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.metrics.TableMetrics;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.db.ColumnFamilyStore;

/**
 * Analyzes workload patterns and provides optimal UCS configuration recommendations.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
public class UCSWorkloadAnalyzer
{
    private static final Logger logger = LoggerFactory.getLogger(UCSWorkloadAnalyzer.class);

    private final WorkloadProfiler profiler;
    private final ConfigurationOptimizer optimizer;
    private final PerformancePredictor predictor;

    public UCSWorkloadAnalyzer()
    {
        this.profiler = new WorkloadProfiler();
        this.optimizer = new ConfigurationOptimizer();
        this.predictor = new PerformancePredictor();
    }

    /**
     * Analyzes a table's workload and recommends optimal UCS configuration.
     *
     * @param cfs The ColumnFamilyStore to analyze
     * @param period The duration to analyze
     * @return Analysis result with workload profile and recommendations
     */
    public AnalysisResult analyze(ColumnFamilyStore cfs, Duration period)
    {
        logger.info("Starting workload analysis for {}.{} over {}",
                    cfs.keyspace.getName(), cfs.name, period);

        // Collect workload metrics
        WorkloadProfile profile = profiler.profile(cfs, period);

        // Classify workload type
        WorkloadType type = classifyWorkload(profile);

        // Generate optimal configuration
        UCSConfiguration optimal = optimizer.optimize(profile, type);

        // Predict performance impact
        PerformancePrediction prediction = predictor.predict(
            cfs.metadata(),
            optimal,
            profile
        );

        logger.info("Workload analysis complete. Type: {}, Confidence: {}",
                    type, profile.getConfidence());

        return AnalysisResult.builder()
            .workloadProfile(profile)
            .workloadType(type)
            .currentEfficiency(calculateCurrentEfficiency(cfs))
            .recommendedConfiguration(optimal)
            .expectedImprovement(prediction)
            .migrationPlan(generateMigrationPlan(cfs, optimal))
            .build();
    }

    private WorkloadType classifyWorkload(WorkloadProfile profile)
    {
        double readWriteRatio = profile.getReadRate() / Math.max(profile.getWriteRate(), 1.0);
        double ttlRatio = profile.getTTLOperations() / Math.max(profile.getTotalOperations(), 1.0);

        if (ttlRatio > 0.8)
        {
            return WorkloadType.TIME_SERIES;
        }
        else if (readWriteRatio > 10)
        {
            return WorkloadType.READ_HEAVY;
        }
        else if (readWriteRatio < 0.1)
        {
            return WorkloadType.WRITE_HEAVY;
        }
        else
        {
            return WorkloadType.MIXED;
        }
    }

    private EfficiencyMetrics calculateCurrentEfficiency(ColumnFamilyStore cfs)
    {
        TableMetrics metrics = cfs.metric;

        return new EfficiencyMetrics(
            metrics.writeAmplification.getValue(),
            metrics.readLatency.getSnapshot().get99thPercentile(),
            calculateSpaceAmplification(cfs)
        );
    }

    private double calculateSpaceAmplification(ColumnFamilyStore cfs)
    {
        long liveData = cfs.metric.liveDiskSpaceUsed.getCount();
        long totalData = cfs.metric.totalDiskSpaceUsed.getCount();

        return totalData / Math.max(liveData, 1.0);
    }

    private MigrationPlan generateMigrationPlan(ColumnFamilyStore cfs, UCSConfiguration config)
    {
        return new MigrationPlan(cfs.metadata(), config);
    }

    /**
     * Profiles workload characteristics over a given period.
     */
    public static class WorkloadProfiler
    {
        public WorkloadProfile profile(ColumnFamilyStore cfs, Duration period)
        {
            TableMetrics metrics = cfs.metric;

            long writeRate = (long) metrics.writeLatency.getOneMinuteRate();
            long readRate = (long) metrics.readLatency.getOneMinuteRate();
            long ttlOps = estimateTTLOperations(cfs);
            long totalOps = writeRate + readRate;

            double avgPartitionSize = calculateAveragePartitionSize(cfs);
            double p99PartitionSize = calculateP99PartitionSize(cfs);
            double partitionSizeVariance = calculatePartitionSizeVariance(cfs);
            double deletionRate = estimateDeletionRate(cfs);
            double confidence = calculateConfidence(writeRate, readRate);

            return new WorkloadProfile(
                writeRate,
                readRate,
                totalOps,
                ttlOps,
                avgPartitionSize,
                p99PartitionSize,
                partitionSizeVariance,
                deletionRate,
                confidence
            );
        }

        private long estimateTTLOperations(ColumnFamilyStore cfs)
        {
            // Estimate based on tombstone metrics
            return (long) (cfs.metric.droppedMutations.getCount() * 0.1);
        }

        private double calculateAveragePartitionSize(ColumnFamilyStore cfs)
        {
            long totalSize = cfs.metric.totalDiskSpaceUsed.getCount();
            long estimatedKeys = cfs.metric.estimatedPartitionCount.getValue();
            return totalSize / Math.max(estimatedKeys, 1.0);
        }

        private double calculateP99PartitionSize(ColumnFamilyStore cfs)
        {
            // Estimate P99 as 3x average (simplified)
            return calculateAveragePartitionSize(cfs) * 3.0;
        }

        private double calculatePartitionSizeVariance(ColumnFamilyStore cfs)
        {
            // Simplified variance calculation
            return 100.0; // Default moderate variance
        }

        private double estimateDeletionRate(ColumnFamilyStore cfs)
        {
            long tombstones = cfs.metric.tombstoneScannedHistogram.getSnapshot().getMax();
            long reads = cfs.metric.readLatency.latency.getCount();
            return tombstones / Math.max(reads, 1.0);
        }

        private double calculateConfidence(long writeRate, long readRate)
        {
            // Higher confidence with more operations
            long totalOps = writeRate + readRate;
            if (totalOps > 10000) return 0.95;
            if (totalOps > 1000) return 0.85;
            if (totalOps > 100) return 0.70;
            return 0.50;
        }
    }

    /**
     * Optimizes UCS configuration based on workload profile.
     */
    public static class ConfigurationOptimizer
    {
        private static final int MIN_SCALING = 2;
        private static final int MAX_SCALING = 32;
        private static final long DEFAULT_TARGET_SIZE_MB = 160;

        public UCSConfiguration optimize(WorkloadProfile profile, WorkloadType type)
        {
            UCSConfiguration config = new UCSConfiguration();

            // Calculate optimal scaling parameter
            int scalingParameter = calculateOptimalScalingParameter(profile, type);
            config.setScalingParameter(scalingParameter);

            // Determine target SSTable size
            long targetSize = calculateTargetSStableSize(profile);
            config.setTargetSStableSize(targetSize);

            // Set sharding parameters
            int numShards = calculateOptimalShards(profile);
            config.setNumShards(numShards);

            // Configure tombstone compaction
            boolean aggressiveTombstone = profile.getDeletionRate() > 0.2;
            config.setAggressiveTombstoneCompaction(aggressiveTombstone);

            logger.debug("Optimized configuration: scaling={}, targetSize={}MB, shards={}",
                        scalingParameter, targetSize / (1024 * 1024), numShards);

            return config;
        }

        private int calculateOptimalScalingParameter(WorkloadProfile profile, WorkloadType type)
        {
            // Base scaling on workload characteristics
            int baseScaling = switch (type)
            {
                case WRITE_HEAVY -> 4;   // Lower scaling for write-heavy
                case READ_HEAVY -> 16;   // Higher scaling for read-heavy
                case TIME_SERIES -> 8;   // Moderate for time-series
                case MIXED -> 10;        // Balanced for mixed
            };

            // Adjust based on partition size distribution
            double sizeVariance = profile.getPartitionSizeVariance();
            if (sizeVariance > 100)
            {
                baseScaling = (int) (baseScaling * 1.5);
            }

            return Math.min(MAX_SCALING, Math.max(MIN_SCALING, baseScaling));
        }

        private long calculateTargetSStableSize(WorkloadProfile profile)
        {
            long baseSizeMB = DEFAULT_TARGET_SIZE_MB;

            // Adjust based on average partition size
            if (profile.getAveragePartitionSize() > 1024 * 1024) // > 1MB
            {
                baseSizeMB = 320; // Larger SSTables for large partitions
            }
            else if (profile.getAveragePartitionSize() < 10 * 1024) // < 10KB
            {
                baseSizeMB = 80; // Smaller SSTables for small partitions
            }

            return baseSizeMB * 1024 * 1024;
        }

        private int calculateOptimalShards(WorkloadProfile profile)
        {
            // Base shards on write rate
            long writeRate = profile.getWriteRate();
            if (writeRate > 100000) return 8;
            if (writeRate > 50000) return 4;
            if (writeRate > 10000) return 2;
            return 1;
        }
    }

    /**
     * Predicts performance impact of configuration changes.
     */
    public static class PerformancePredictor
    {
        public PerformancePrediction predict(TableMetadata table, UCSConfiguration config, WorkloadProfile profile)
        {
            // Simplified prediction model
            double writeAmpImprovement = estimateWriteAmpImprovement(config, profile);
            double readAmpImprovement = estimateReadAmpImprovement(config, profile);
            double spaceAmpImprovement = estimateSpaceAmpImprovement(config, profile);

            return new PerformancePrediction(
                writeAmpImprovement,
                readAmpImprovement,
                spaceAmpImprovement
            );
        }

        private double estimateWriteAmpImprovement(UCSConfiguration config, WorkloadProfile profile)
        {
            // Lower scaling parameter = better write amp
            return -0.15 - (config.getScalingParameter() / 100.0);
        }

        private double estimateReadAmpImprovement(UCSConfiguration config, WorkloadProfile profile)
        {
            // Higher scaling parameter = better read amp
            return -0.20 + (config.getScalingParameter() / 200.0);
        }

        private double estimateSpaceAmpImprovement(UCSConfiguration config, WorkloadProfile profile)
        {
            return -0.10;
        }
    }

    // Supporting classes

    public enum WorkloadType
    {
        WRITE_HEAVY, READ_HEAVY, MIXED, TIME_SERIES
    }

    public static class WorkloadProfile
    {
        private final long writeRate;
        private final long readRate;
        private final long totalOperations;
        private final long ttlOperations;
        private final double averagePartitionSize;
        private final double p99PartitionSize;
        private final double partitionSizeVariance;
        private final double deletionRate;
        private final double confidence;

        public WorkloadProfile(long writeRate, long readRate, long totalOps, long ttlOps,
                             double avgPartSize, double p99PartSize, double partSizeVar,
                             double delRate, double conf)
        {
            this.writeRate = writeRate;
            this.readRate = readRate;
            this.totalOperations = totalOps;
            this.ttlOperations = ttlOps;
            this.averagePartitionSize = avgPartSize;
            this.p99PartitionSize = p99PartSize;
            this.partitionSizeVariance = partSizeVar;
            this.deletionRate = delRate;
            this.confidence = conf;
        }

        public long getWriteRate() { return writeRate; }
        public long getReadRate() { return readRate; }
        public long getTotalOperations() { return totalOperations; }
        public long getTTLOperations() { return ttlOperations; }
        public double getAveragePartitionSize() { return averagePartitionSize; }
        public double getP99PartitionSize() { return p99PartitionSize; }
        public double getPartitionSizeVariance() { return partitionSizeVariance; }
        public double getDeletionRate() { return deletionRate; }
        public double getConfidence() { return confidence; }
    }

    public static class UCSConfiguration
    {
        private int scalingParameter = 10;
        private long targetSStableSize = 160 * 1024 * 1024;
        private int numShards = 1;
        private boolean aggressiveTombstoneCompaction = false;

        public int getScalingParameter() { return scalingParameter; }
        public void setScalingParameter(int value) { this.scalingParameter = value; }

        public long getTargetSStableSize() { return targetSStableSize; }
        public void setTargetSStableSize(long value) { this.targetSStableSize = value; }

        public int getNumShards() { return numShards; }
        public void setNumShards(int value) { this.numShards = value; }

        public boolean isAggressiveTombstoneCompaction() { return aggressiveTombstoneCompaction; }
        public void setAggressiveTombstoneCompaction(boolean value) { this.aggressiveTombstoneCompaction = value; }

        public Map<String, String> toOptions()
        {
            Map<String, String> options = new HashMap<>();
            options.put("scaling_parameter", String.valueOf(scalingParameter));
            options.put("target_sstable_size", String.valueOf(targetSStableSize));
            options.put("num_shards", String.valueOf(numShards));
            return options;
        }

        public UCSConfiguration clone()
        {
            UCSConfiguration copy = new UCSConfiguration();
            copy.scalingParameter = this.scalingParameter;
            copy.targetSStableSize = this.targetSStableSize;
            copy.numShards = this.numShards;
            copy.aggressiveTombstoneCompaction = this.aggressiveTombstoneCompaction;
            return copy;
        }
    }

    public static class EfficiencyMetrics
    {
        private final double writeAmplification;
        private final double readLatency;
        private final double spaceAmplification;

        public EfficiencyMetrics(double writeAmp, double readLat, double spaceAmp)
        {
            this.writeAmplification = writeAmp;
            this.readLatency = readLat;
            this.spaceAmplification = spaceAmp;
        }

        public double getWriteAmplification() { return writeAmplification; }
        public double getReadLatency() { return readLatency; }
        public double getSpaceAmplification() { return spaceAmplification; }
    }

    public static class PerformancePrediction
    {
        private final double writeAmpImprovement;
        private final double readAmpImprovement;
        private final double spaceAmpImprovement;

        public PerformancePrediction(double writeImp, double readImp, double spaceImp)
        {
            this.writeAmpImprovement = writeImp;
            this.readAmpImprovement = readImp;
            this.spaceAmpImprovement = spaceImp;
        }

        public double getWriteAmpImprovement() { return writeAmpImprovement; }
        public double getReadAmpImprovement() { return readAmpImprovement; }
        public double getSpaceAmpImprovement() { return spaceAmpImprovement; }
    }

    public static class MigrationPlan
    {
        private final TableMetadata table;
        private final UCSConfiguration targetConfig;

        public MigrationPlan(TableMetadata table, UCSConfiguration config)
        {
            this.table = table;
            this.targetConfig = config;
        }

        public TableMetadata getTable() { return table; }
        public UCSConfiguration getTargetConfiguration() { return targetConfig; }
    }

    public static class AnalysisResult
    {
        private final WorkloadProfile workloadProfile;
        private final WorkloadType workloadType;
        private final EfficiencyMetrics currentEfficiency;
        private final UCSConfiguration recommendedConfiguration;
        private final PerformancePrediction expectedImprovement;
        private final MigrationPlan migrationPlan;

        private AnalysisResult(Builder builder)
        {
            this.workloadProfile = builder.workloadProfile;
            this.workloadType = builder.workloadType;
            this.currentEfficiency = builder.currentEfficiency;
            this.recommendedConfiguration = builder.recommendedConfiguration;
            this.expectedImprovement = builder.expectedImprovement;
            this.migrationPlan = builder.migrationPlan;
        }

        public static Builder builder() { return new Builder(); }

        public WorkloadProfile getWorkloadProfile() { return workloadProfile; }
        public WorkloadType getWorkloadType() { return workloadType; }
        public EfficiencyMetrics getCurrentEfficiency() { return currentEfficiency; }
        public UCSConfiguration getRecommendedConfiguration() { return recommendedConfiguration; }
        public PerformancePrediction getExpectedImprovement() { return expectedImprovement; }
        public MigrationPlan getMigrationPlan() { return migrationPlan; }

        public static class Builder
        {
            private WorkloadProfile workloadProfile;
            private WorkloadType workloadType;
            private EfficiencyMetrics currentEfficiency;
            private UCSConfiguration recommendedConfiguration;
            private PerformancePrediction expectedImprovement;
            private MigrationPlan migrationPlan;

            public Builder workloadProfile(WorkloadProfile val) { workloadProfile = val; return this; }
            public Builder workloadType(WorkloadType val) { workloadType = val; return this; }
            public Builder currentEfficiency(EfficiencyMetrics val) { currentEfficiency = val; return this; }
            public Builder recommendedConfiguration(UCSConfiguration val) { recommendedConfiguration = val; return this; }
            public Builder expectedImprovement(PerformancePrediction val) { expectedImprovement = val; return this; }
            public Builder migrationPlan(MigrationPlan val) { migrationPlan = val; return this; }

            public AnalysisResult build() { return new AnalysisResult(this); }
        }
    }
}
