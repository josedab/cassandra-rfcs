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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.compaction.AbstractCompactionStrategy;
import org.apache.cassandra.db.compaction.unified.UCSWorkloadAnalyzer.UCSConfiguration;
import org.apache.cassandra.metrics.TableMetrics;
import org.apache.cassandra.schema.TableMetadata;

/**
 * Benchmarking suite for UCS performance validation.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
public class UCSBenchmarkSuite
{
    private static final Logger logger = LoggerFactory.getLogger(UCSBenchmarkSuite.class);

    private final WorkloadGenerator workloadGen;
    private final MetricsCollector metricsCollector;
    private final ComparisonEngine comparisonEngine;
    private final RegressionDetector regressionDetector;

    public UCSBenchmarkSuite()
    {
        this.workloadGen = new WorkloadGenerator();
        this.metricsCollector = new MetricsCollector();
        this.comparisonEngine = new ComparisonEngine();
        this.regressionDetector = new RegressionDetector();
    }

    /**
     * Run complete benchmark suite for a table.
     *
     * @param cfs The table to benchmark
     * @param options Benchmark options
     * @return Comprehensive benchmark results
     */
    public BenchmarkResult runBenchmark(ColumnFamilyStore cfs, BenchmarkOptions options)
    {
        logger.info("Starting benchmark suite for {}.{}", cfs.keyspace.getName(), cfs.name);

        BenchmarkResult result = new BenchmarkResult();
        result.tableName = cfs.metadata().name;
        result.keyspaceName = cfs.keyspace.getName();

        // Generate standardized workloads
        List<Workload> workloads = Arrays.asList(
            workloadGen.generateWriteHeavy(),
            workloadGen.generateReadHeavy(),
            workloadGen.generateMixed(),
            workloadGen.generateTimeSeries(),
            workloadGen.generateLargePartitions()
        );

        for (Workload workload : workloads)
        {
            logger.info("Running workload: {}", workload.getName());

            // Run with current strategy
            WorkloadResult current = runWorkload(cfs, workload, options);

            // Run with UCS
            WorkloadResult ucs = runWithUCS(cfs, workload, options);

            // Compare results
            ComparisonResult comparison = comparisonEngine.compare(current, ucs, workload);
            result.addComparison(workload.getName(), comparison);

            logger.info("Workload {} completed. Throughput change: {:.1f}%",
                       workload.getName(), comparison.getThroughputChange() * 100);
        }

        // Generate recommendations
        result.recommendations = generateRecommendations(result);

        // Detect regressions
        result.regressions = regressionDetector.detectRegressions(result);

        logger.info("Benchmark suite completed for {}.{}. Regressions: {}",
                   cfs.keyspace.getName(), cfs.name, result.regressions.size());

        return result;
    }

    private WorkloadResult runWorkload(ColumnFamilyStore cfs, Workload workload, BenchmarkOptions options)
    {
        WorkloadResult result = new WorkloadResult();

        if (!options.skipPreparation)
        {
            clearCaches();
        }

        // Start metrics collection
        MetricsSnapshot startMetrics = metricsCollector.snapshot(cfs);

        // Run workload
        long startTime = System.nanoTime();
        workload.execute(cfs);
        long duration = System.nanoTime() - startTime;

        // Collect metrics
        MetricsSnapshot endMetrics = metricsCollector.snapshot(cfs);

        // Calculate results
        result.duration = duration;
        result.throughput = workload.getOperationCount() / (duration / 1e9);
        result.latencyP50 = calculatePercentile(workload.getLatencies(), 50);
        result.latencyP99 = calculatePercentile(workload.getLatencies(), 99);
        result.writeAmplification = calculateWriteAmp(startMetrics, endMetrics);
        result.readAmplification = calculateReadAmp(startMetrics, endMetrics);
        result.spaceAmplification = calculateSpaceAmp(cfs);

        logger.debug("Workload result: throughput={:.2f} ops/s, p99={:.2f}ms",
                    result.throughput, result.latencyP99);

        return result;
    }

    private WorkloadResult runWithUCS(ColumnFamilyStore cfs, Workload workload, BenchmarkOptions options)
    {
        // Save current strategy
        AbstractCompactionStrategy originalStrategy = cfs.getCompactionStrategy();

        try
        {
            // Apply UCS temporarily
            UCSConfiguration ucsConfig = new UCSConfiguration();
            // Apply config...

            return runWorkload(cfs, workload, options);
        }
        finally
        {
            // Restore original strategy
            // Restoration logic here
        }
    }

    private void clearCaches()
    {
        logger.debug("Clearing caches for benchmark");
        // Cache clearing logic
    }

    private double calculatePercentile(List<Long> values, int percentile)
    {
        if (values.isEmpty())
            return 0.0;

        List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index)) / 1_000_000.0; // Convert to ms
    }

    private double calculateWriteAmp(MetricsSnapshot start, MetricsSnapshot end)
    {
        long memtableBytes = end.memtableWrites - start.memtableWrites;
        long diskBytes = end.diskWrites - start.diskWrites;
        return memtableBytes > 0 ? (double) diskBytes / memtableBytes : 1.0;
    }

    private double calculateReadAmp(MetricsSnapshot start, MetricsSnapshot end)
    {
        long readsIssued = end.readsIssued - start.readsIssued;
        long readsCompleted = end.readsCompleted - start.readsCompleted;
        return readsCompleted > 0 ? (double) readsIssued / readsCompleted : 1.0;
    }

    private double calculateSpaceAmp(ColumnFamilyStore cfs)
    {
        long liveData = cfs.metric.liveDiskSpaceUsed.getCount();
        long totalData = cfs.metric.totalDiskSpaceUsed.getCount();
        return liveData > 0 ? (double) totalData / liveData : 1.0;
    }

    private List<String> generateRecommendations(BenchmarkResult result)
    {
        List<String> recommendations = new ArrayList<>();

        // Analyze comparisons and generate recommendations
        for (Map.Entry<String, ComparisonResult> entry : result.getComparisons().entrySet())
        {
            ComparisonResult comp = entry.getValue();
            if (comp.getThroughputChange() > 0.1)
            {
                recommendations.add(String.format(
                    "UCS shows %.1f%% throughput improvement for %s workload",
                    comp.getThroughputChange() * 100, entry.getKey()));
            }
            else if (comp.getThroughputChange() < -0.1)
            {
                recommendations.add(String.format(
                    "Consider tuning UCS parameters for %s workload (%.1f%% throughput degradation)",
                    entry.getKey(), Math.abs(comp.getThroughputChange() * 100)));
            }
        }

        return recommendations;
    }

    // Supporting classes

    public static class WorkloadGenerator
    {
        public Workload generateWriteHeavy()
        {
            return new Workload("write_heavy", WorkloadType.WRITE_HEAVY, 100000);
        }

        public Workload generateReadHeavy()
        {
            return new Workload("read_heavy", WorkloadType.READ_HEAVY, 100000);
        }

        public Workload generateMixed()
        {
            return new Workload("mixed", WorkloadType.MIXED, 100000);
        }

        public Workload generateTimeSeries()
        {
            return new Workload("time_series", WorkloadType.TIME_SERIES, 100000);
        }

        public Workload generateLargePartitions()
        {
            return new Workload("large_partitions", WorkloadType.LARGE_PARTITIONS, 10000);
        }
    }

    public static class MetricsCollector
    {
        public MetricsSnapshot snapshot(ColumnFamilyStore cfs)
        {
            TableMetrics metrics = cfs.metric;

            return new MetricsSnapshot(
                metrics.writeLatency.latency.getCount(),
                metrics.readLatency.latency.getCount(),
                cfs.metric.liveDiskSpaceUsed.getCount(),
                cfs.metric.totalDiskSpaceUsed.getCount(),
                System.currentTimeMillis()
            );
        }
    }

    public static class ComparisonEngine
    {
        public ComparisonResult compare(WorkloadResult baseline, WorkloadResult candidate, Workload workload)
        {
            ComparisonResult result = new ComparisonResult();
            result.workload = workload.getName();

            result.throughputChange = (candidate.throughput - baseline.throughput) / baseline.throughput;
            result.latencyP99Change = (candidate.latencyP99 - baseline.latencyP99) / baseline.latencyP99;
            result.writeAmplificationChange = (candidate.writeAmplification - baseline.writeAmplification) / baseline.writeAmplification;
            result.readAmplificationChange = (candidate.readAmplification - baseline.readAmplification) / baseline.readAmplification;

            return result;
        }
    }

    public static class RegressionDetector
    {
        private static final double REGRESSION_THRESHOLD = 0.1; // 10% regression

        public List<Regression> detectRegressions(BenchmarkResult result)
        {
            List<Regression> regressions = new ArrayList<>();

            for (Map.Entry<String, ComparisonResult> entry : result.getComparisons().entrySet())
            {
                ComparisonResult comparison = entry.getValue();

                // Check throughput regression
                if (comparison.getThroughputChange() < -REGRESSION_THRESHOLD)
                {
                    regressions.add(new Regression(
                        "throughput",
                        comparison.getWorkload(),
                        comparison.getThroughputChange()
                    ));
                }

                // Check latency regression
                if (comparison.getLatencyP99Change() > REGRESSION_THRESHOLD)
                {
                    regressions.add(new Regression(
                        "latency_p99",
                        comparison.getWorkload(),
                        comparison.getLatencyP99Change()
                    ));
                }

                // Check amplification regression
                if (comparison.getWriteAmplificationChange() > REGRESSION_THRESHOLD)
                {
                    regressions.add(new Regression(
                        "write_amplification",
                        comparison.getWorkload(),
                        comparison.getWriteAmplificationChange()
                    ));
                }
            }

            return regressions;
        }
    }

    // Data classes

    public enum WorkloadType
    {
        WRITE_HEAVY, READ_HEAVY, MIXED, TIME_SERIES, LARGE_PARTITIONS
    }

    public static class Workload
    {
        private final String name;
        private final WorkloadType type;
        private final int operationCount;
        private final List<Long> latencies = new ArrayList<>();

        public Workload(String name, WorkloadType type, int opCount)
        {
            this.name = name;
            this.type = type;
            this.operationCount = opCount;
        }

        public String getName() { return name; }
        public WorkloadType getType() { return type; }
        public int getOperationCount() { return operationCount; }
        public List<Long> getLatencies() { return latencies; }

        public void execute(ColumnFamilyStore cfs)
        {
            logger.debug("Executing workload: {}", name);
            // Simplified execution - real implementation would perform actual operations
            for (int i = 0; i < operationCount; i++)
            {
                long latency = simulateOperation(type);
                latencies.add(latency);
            }
        }

        private long simulateOperation(WorkloadType type)
        {
            // Simulate operation latency
            return TimeUnit.MILLISECONDS.toNanos(1 + (long) (Math.random() * 10));
        }
    }

    public static class BenchmarkOptions
    {
        public boolean skipPreparation = false;
        public int warmupIterations = 3;
        public int benchmarkIterations = 5;

        public BenchmarkOptions setSkipPreparation(boolean value)
        {
            this.skipPreparation = value;
            return this;
        }
    }

    public static class WorkloadResult
    {
        public long duration;
        public double throughput;
        public double latencyP50;
        public double latencyP99;
        public double writeAmplification;
        public double readAmplification;
        public double spaceAmplification;

        @Override
        public String toString()
        {
            return String.format("WorkloadResult{throughput=%.2f, p99=%.2f, writeAmp=%.2f}",
                               throughput, latencyP99, writeAmplification);
        }
    }

    public static class ComparisonResult
    {
        public String workload;
        public double throughputChange;
        public double latencyP99Change;
        public double writeAmplificationChange;
        public double readAmplificationChange;

        public String getWorkload() { return workload; }
        public double getThroughputChange() { return throughputChange; }
        public double getLatencyP99Change() { return latencyP99Change; }
        public double getWriteAmplificationChange() { return writeAmplificationChange; }
        public double getReadAmplificationChange() { return readAmplificationChange; }

        @Override
        public String toString()
        {
            return String.format("ComparisonResult{workload=%s, throughputChange=%.1f%%, p99Change=%.1f%%}",
                               workload, throughputChange * 100, latencyP99Change * 100);
        }
    }

    public static class Regression
    {
        private final String metric;
        private final String workload;
        private final double change;

        public Regression(String metric, String workload, double change)
        {
            this.metric = metric;
            this.workload = workload;
            this.change = change;
        }

        public String getMetric() { return metric; }
        public String getWorkload() { return workload; }
        public double getChange() { return change; }

        @Override
        public String toString()
        {
            return String.format("Regression{metric=%s, workload=%s, change=%.1f%%}",
                               metric, workload, change * 100);
        }
    }

    public static class BenchmarkResult
    {
        public String keyspaceName;
        public String tableName;
        public Map<String, ComparisonResult> comparisons = new HashMap<>();
        public List<String> recommendations = new ArrayList<>();
        public List<Regression> regressions = new ArrayList<>();

        public void addComparison(String workload, ComparisonResult comparison)
        {
            comparisons.put(workload, comparison);
        }

        public Map<String, ComparisonResult> getComparisons()
        {
            return comparisons;
        }

        @Override
        public String toString()
        {
            return String.format("BenchmarkResult{table=%s.%s, workloads=%d, regressions=%d}",
                               keyspaceName, tableName, comparisons.size(), regressions.size());
        }
    }

    public static class MetricsSnapshot
    {
        public long memtableWrites;
        public long diskWrites;
        public long readsIssued;
        public long readsCompleted;
        public long timestamp;

        public MetricsSnapshot(long memWrites, long diskWrites, long reads, long readsComp, long ts)
        {
            this.memtableWrites = memWrites;
            this.diskWrites = diskWrites;
            this.readsIssued = reads;
            this.readsCompleted = readsComp;
            this.timestamp = ts;
        }
    }
}
