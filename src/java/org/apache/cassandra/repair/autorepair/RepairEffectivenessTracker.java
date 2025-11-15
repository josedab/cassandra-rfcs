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

package org.apache.cassandra.repair.autorepair;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Tracks repair effectiveness metrics and provides analysis.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairEffectivenessTracker
{
    private static final Logger logger = LoggerFactory.getLogger(RepairEffectivenessTracker.class);

    private final Map<String, RepairMetrics> metricsByTable = new ConcurrentHashMap<>();
    private final List<RepairMetricEntry> metricHistory = new ArrayList<>();
    private final MeterRegistry meterRegistry;

    public RepairEffectivenessTracker(MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record completion of a repair operation
     */
    public void recordRepairCompletion(RepairResult result)
    {
        String tableKey = result.getKeyspace() + "." + result.getTable();
        RepairMetrics metrics = metricsByTable.computeIfAbsent(tableKey,
            k -> new RepairMetrics(meterRegistry, result.getKeyspace(), result.getTable()));

        // Update metrics
        metrics.recordRepair(result);

        // Store detailed entry for historical analysis
        double efficiency = calculateEfficiency(result);
        RepairMetricEntry entry = RepairMetricEntry.builder()
                                                   .keyspace(result.getKeyspace())
                                                   .table(result.getTable())
                                                   .timestamp(System.currentTimeMillis())
                                                   .bytesRepaired(result.getBytesRepaired())
                                                   .bytesValidated(result.getBytesValidated())
                                                   .discrepanciesFound(result.getDiscrepanciesFound())
                                                   .duration(result.getDuration())
                                                   .efficiency(efficiency)
                                                   .merkleTrees(result.getMerkleTreeCount())
                                                   .streamingSessions(result.getStreamingSessionCount())
                                                   .build();

        synchronized (metricHistory)
        {
            metricHistory.add(entry);
        }

        logger.debug("Recorded repair completion for {}.{}: efficiency={:.2f}%",
                    result.getKeyspace(), result.getTable(), efficiency * 100);
    }

    /**
     * Calculate repair efficiency (1.0 = perfect, 0.0 = all data needed repair)
     */
    @VisibleForTesting
    public double calculateEfficiency(RepairResult result)
    {
        if (result.getBytesValidated() == 0)
            return 1.0;

        // Efficiency = 1 - (bytes_repaired / bytes_validated)
        // Higher is better (fewer repairs needed)
        return 1.0 - (double) result.getBytesRepaired() / result.getBytesValidated();
    }

    /**
     * Generate effectiveness report for a time period
     */
    public EffectivenessReport generateReport(Duration period)
    {
        Instant start = Instant.now().minus(period);
        Collection<RepairMetricEntry> entries = queryMetrics(start, Instant.now());

        long totalBytesRepaired = sumBytesRepaired(entries);
        long totalBytesValidated = sumBytesValidated(entries);
        double averageEfficiency = calculateAverageEfficiency(entries);
        Map<String, List<RepairMetricEntry>> byKeyspace = groupByKeyspace(entries);
        List<Double> discrepancyTrend = calculateDiscrepancyTrend(entries);
        List<String> recommendations = generateRecommendations(entries);

        return EffectivenessReport.builder()
                                  .period(period)
                                  .totalBytesRepaired(totalBytesRepaired)
                                  .totalBytesValidated(totalBytesValidated)
                                  .averageEfficiency(averageEfficiency)
                                  .repairsByKeyspace(byKeyspace)
                                  .discrepancyTrend(discrepancyTrend)
                                  .recommendations(recommendations)
                                  .build();
    }

    /**
     * Query metric entries within a time range
     */
    public Collection<RepairMetricEntry> queryMetrics(Instant start, Instant end)
    {
        synchronized (metricHistory)
        {
            return metricHistory.stream()
                               .filter(e -> e.getTimestamp() >= start.toEpochMilli() &&
                                           e.getTimestamp() <= end.toEpochMilli())
                               .collect(Collectors.toList());
        }
    }

    private long sumBytesRepaired(Collection<RepairMetricEntry> entries)
    {
        return entries.stream().mapToLong(RepairMetricEntry::getBytesRepaired).sum();
    }

    private long sumBytesValidated(Collection<RepairMetricEntry> entries)
    {
        return entries.stream().mapToLong(RepairMetricEntry::getBytesValidated).sum();
    }

    private double calculateAverageEfficiency(Collection<RepairMetricEntry> entries)
    {
        if (entries.isEmpty())
            return 1.0;

        return entries.stream()
                     .mapToDouble(RepairMetricEntry::getEfficiency)
                     .average()
                     .orElse(1.0);
    }

    private Map<String, List<RepairMetricEntry>> groupByKeyspace(Collection<RepairMetricEntry> entries)
    {
        return entries.stream()
                     .collect(Collectors.groupingBy(RepairMetricEntry::getKeyspace));
    }

    private List<Double> calculateDiscrepancyTrend(Collection<RepairMetricEntry> entries)
    {
        // Calculate discrepancy rate over time
        List<Double> trend = new ArrayList<>();
        List<RepairMetricEntry> sortedEntries = entries.stream()
                                                       .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                                                       .collect(Collectors.toList());

        for (RepairMetricEntry entry : sortedEntries)
        {
            double discrepancyRate = entry.getBytesValidated() > 0 ?
                (double) entry.getBytesRepaired() / entry.getBytesValidated() : 0.0;
            trend.add(discrepancyRate);
        }

        return trend;
    }

    private List<String> generateRecommendations(Collection<RepairMetricEntry> entries)
    {
        List<String> recommendations = new ArrayList<>();
        double avgEfficiency = calculateAverageEfficiency(entries);

        if (avgEfficiency < 0.95)
        {
            recommendations.add("Efficiency below 95% - consider increasing repair frequency");
        }

        long totalRepaired = sumBytesRepaired(entries);
        long totalValidated = sumBytesValidated(entries);

        if (totalValidated > 0 && (double) totalRepaired / totalValidated > 0.1)
        {
            recommendations.add("High repair rate detected - investigate data consistency issues");
        }

        return recommendations;
    }

    /**
     * Per-table repair metrics
     */
    public static class RepairMetrics
    {
        private final Counter bytesRepaired;
        private final Counter bytesValidated;
        private final Counter discrepanciesFound;
        private final Timer repairDuration;
        private final AtomicLong lastEfficiency = new AtomicLong(Double.doubleToLongBits(1.0));

        public RepairMetrics(MeterRegistry registry, String keyspace, String table)
        {
            String[] tags = new String[]{"keyspace", keyspace, "table", table};

            this.bytesRepaired = Counter.builder("cassandra.repair.bytes_repaired")
                                       .tags(tags)
                                       .register(registry);

            this.bytesValidated = Counter.builder("cassandra.repair.bytes_validated")
                                        .tags(tags)
                                        .register(registry);

            this.discrepanciesFound = Counter.builder("cassandra.repair.discrepancies")
                                            .tags(tags)
                                            .register(registry);

            this.repairDuration = Timer.builder("cassandra.repair.duration")
                                      .tags(tags)
                                      .register(registry);

            Gauge.builder("cassandra.repair.efficiency", this, m -> Double.longBitsToDouble(m.lastEfficiency.get()))
                .tags(tags)
                .register(registry);
        }

        public void recordRepair(RepairResult result)
        {
            bytesRepaired.increment(result.getBytesRepaired());
            bytesValidated.increment(result.getBytesValidated());
            discrepanciesFound.increment(result.getDiscrepanciesFound());
            repairDuration.record(result.getDuration());

            // Update efficiency gauge
            double efficiency = result.getBytesValidated() > 0 ?
                1.0 - (double) result.getBytesRepaired() / result.getBytesValidated() : 1.0;
            lastEfficiency.set(Double.doubleToLongBits(efficiency));
        }
    }

    /**
     * Clear all metrics (for testing)
     */
    @VisibleForTesting
    public void clearMetrics()
    {
        metricsByTable.clear();
        synchronized (metricHistory)
        {
            metricHistory.clear();
        }
    }
}
