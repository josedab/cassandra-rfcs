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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.apache.cassandra.metrics.StorageMetrics;
import org.apache.cassandra.service.ActiveRepairService;

/**
 * Monitors system load metrics for adaptive repair scheduling.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class LoadMonitor
{
    private static final int DEFAULT_HISTORY_SIZE = 60; // Keep 60 snapshots (1 hour at 1min intervals)

    private final CircularFifoQueue<LoadSnapshot> loadHistory;
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;

    public LoadMonitor()
    {
        this(DEFAULT_HISTORY_SIZE);
    }

    public LoadMonitor(int historySize)
    {
        this.loadHistory = new CircularFifoQueue<>(historySize);
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Get current system load as a composite score (0.0 to 1.0)
     */
    public double getCurrentLoad()
    {
        LoadSnapshot snapshot = captureSnapshot();
        loadHistory.add(snapshot);
        return calculateLoadScore(snapshot);
    }

    /**
     * Capture a snapshot of current system metrics
     */
    @VisibleForTesting
    public LoadSnapshot captureSnapshot()
    {
        return LoadSnapshot.builder()
                          .cpuUsage(getCpuUsage())
                          .memoryUsage(getMemoryUsage())
                          .compactionPending(getCompactionPendingTasks())
                          .activeRepairs(getActiveRepairCount())
                          .readLatency(getReadLatency())
                          .writeLatency(getWriteLatency())
                          .timestamp(System.currentTimeMillis())
                          .build();
    }

    /**
     * Calculate a composite load score from a snapshot
     * Returns a value between 0.0 (no load) and 1.0 (maximum load)
     */
    @VisibleForTesting
    public double calculateLoadScore(LoadSnapshot snapshot)
    {
        // Weighted combination of metrics
        double score = 0.0;
        score += snapshot.getCpuUsage() * 0.3;  // 30% weight
        score += normalizeMemoryUsage(snapshot.getMemoryUsage()) * 0.2;  // 20% weight
        score += normalizeCompactionPending(snapshot.getCompactionPending()) * 0.2;  // 20% weight
        score += normalizeLatency(snapshot.getReadLatency(), snapshot.getWriteLatency()) * 0.3;  // 30% weight

        return Math.min(1.0, score);
    }

    /**
     * Predict future load based on recent trends
     */
    public double predictFutureLoad(Duration lookahead)
    {
        List<LoadSnapshot> recent = getRecentSnapshots(Duration.ofMinutes(30));
        if (recent.size() < 2)
            return getCurrentLoad();

        double trend = calculateTrend(recent);
        double currentLoad = getCurrentLoad();

        // Simple linear projection
        return Math.min(1.0, Math.max(0.0, currentLoad + (trend * lookahead.toMinutes())));
    }

    /**
     * Get recent snapshots within a time window
     */
    @VisibleForTesting
    public List<LoadSnapshot> getRecentSnapshots(Duration window)
    {
        long cutoff = System.currentTimeMillis() - window.toMillis();
        List<LoadSnapshot> recent = new ArrayList<>();

        for (LoadSnapshot snapshot : loadHistory)
        {
            if (snapshot.getTimestamp() >= cutoff)
                recent.add(snapshot);
        }

        return recent;
    }

    /**
     * Calculate load trend from snapshots (positive = increasing, negative = decreasing)
     */
    @VisibleForTesting
    public double calculateTrend(List<LoadSnapshot> snapshots)
    {
        if (snapshots.size() < 2)
            return 0.0;

        // Simple linear regression slope
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int n = snapshots.size();

        for (int i = 0; i < n; i++)
        {
            double x = i;
            double y = calculateLoadScore(snapshots.get(i));
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        return slope;
    }

    // Metric getters

    private double getCpuUsage()
    {
        double load = osBean.getSystemLoadAverage();
        int processors = osBean.getAvailableProcessors();

        if (load < 0)
            return 0.0; // Not available on some systems

        // Normalize by number of processors
        return Math.min(1.0, load / processors);
    }

    private double getMemoryUsage()
    {
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max = memoryBean.getHeapMemoryUsage().getMax();
        return (double) used / max;
    }

    private int getCompactionPendingTasks()
    {
        return CompactionManager.instance.getActiveCompactions().size() +
               CompactionManager.instance.getPendingTasks();
    }

    private int getActiveRepairCount()
    {
        return ActiveRepairService.instance().getActiveRepairCount();
    }

    private double getReadLatency()
    {
        try
        {
            return StorageMetrics.readLatency.getSnapshot().getMedian() / 1000.0; // Convert to ms
        }
        catch (Exception e)
        {
            return 0.0;
        }
    }

    private double getWriteLatency()
    {
        try
        {
            return StorageMetrics.writeLatency.getSnapshot().getMedian() / 1000.0; // Convert to ms
        }
        catch (Exception e)
        {
            return 0.0;
        }
    }

    // Normalization functions

    private double normalizeMemoryUsage(double memoryUsage)
    {
        // Memory usage above 0.8 is considered high load
        if (memoryUsage < 0.7)
            return 0.0;
        else if (memoryUsage > 0.9)
            return 1.0;
        else
            return (memoryUsage - 0.7) / 0.2;
    }

    private double normalizeCompactionPending(int pending)
    {
        // More than 10 pending compactions is considered high load
        if (pending == 0)
            return 0.0;
        else if (pending >= 10)
            return 1.0;
        else
            return pending / 10.0;
    }

    private double normalizeLatency(double readLatency, double writeLatency)
    {
        // Average latency above 50ms is considered high load
        double avgLatency = (readLatency + writeLatency) / 2.0;

        if (avgLatency < 10.0)
            return 0.0;
        else if (avgLatency > 50.0)
            return 1.0;
        else
            return (avgLatency - 10.0) / 40.0;
    }

    /**
     * Clear history (for testing)
     */
    @VisibleForTesting
    public void clearHistory()
    {
        loadHistory.clear();
    }
}
