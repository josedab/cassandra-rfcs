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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;

/**
 * Coordinates repair operations with compaction to avoid conflicts and optimize resource usage.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairCompactionCoordinator
{
    private static final Logger logger = LoggerFactory.getLogger(RepairCompactionCoordinator.class);

    private final CompactionManager compactionManager;
    private final boolean coordinationEnabled;

    // Time window allocations (percentages)
    private static final double REPAIR_ALLOCATION = 0.4;      // 40% for repairs
    private static final double COMPACTION_ALLOCATION = 0.4;  // 40% for compaction
    private static final double BUFFER_ALLOCATION = 0.2;      // 20% buffer for normal ops

    public RepairCompactionCoordinator(boolean coordinationEnabled)
    {
        this(CompactionManager.instance, coordinationEnabled);
    }

    @VisibleForTesting
    public RepairCompactionCoordinator(CompactionManager compactionManager,
                                      boolean coordinationEnabled)
    {
        this.compactionManager = compactionManager;
        this.coordinationEnabled = coordinationEnabled;
    }

    /**
     * Check if repair should be deferred due to compaction activity
     *
     * @param candidate The repair candidate to evaluate
     * @return true if repair should be deferred, false otherwise
     */
    public boolean shouldDeferRepair(RepairCandidate candidate)
    {
        if (!coordinationEnabled)
            return false;

        // Check if major compaction is running on target ranges
        if (hasMajorCompactionInProgress(candidate.getRanges()))
        {
            logger.debug("Deferring repair due to major compaction on ranges {} for {}.{}",
                        candidate.getRanges(), candidate.getKeyspace(), candidate.getTable());
            return true;
        }

        // Check compaction backlog
        int pendingCompactions = compactionManager.getPendingTasks();
        int threshold = DatabaseDescriptor.getConcurrentCompactors() * 2;

        if (pendingCompactions > threshold)
        {
            logger.debug("Deferring repair due to high compaction backlog: {} pending (threshold: {})",
                        pendingCompactions, threshold);
            return true;
        }

        return false;
    }

    /**
     * Check if major compaction is in progress for any of the given ranges
     */
    private boolean hasMajorCompactionInProgress(Collection<Range<Token>> ranges)
    {
        // This is a simplified check - actual implementation would query CompactionManager
        // for active compactions overlapping with the repair ranges
        int activeCompactions = compactionManager.getActiveCompactions().size();

        // If there are many active compactions, assume there might be overlap
        return activeCompactions > DatabaseDescriptor.getConcurrentCompactors();
    }

    /**
     * Create a coordination window for scheduling operations
     */
    public CoordinationWindow createCoordinationWindow(Duration windowDuration)
    {
        return new CoordinationWindow(windowDuration, Instant.now());
    }

    /**
     * Represents a time window for coordinating repair and compaction operations
     */
    public static class CoordinationWindow
    {
        private final Duration duration;
        private final Instant startTime;
        private final Map<OperationType, Double> allocations = new HashMap<>();
        private final Map<OperationType, TimeSlot> currentSlots = new HashMap<>();

        public CoordinationWindow(Duration duration, Instant startTime)
        {
            this.duration = duration;
            this.startTime = startTime;

            // Initialize default allocations
            allocations.put(OperationType.REPAIR, REPAIR_ALLOCATION);
            allocations.put(OperationType.COMPACTION, COMPACTION_ALLOCATION);
            allocations.put(OperationType.NORMAL_OPS, BUFFER_ALLOCATION);
        }

        /**
         * Allocate time for an operation type
         */
        public void allocate(OperationType type, double percentage)
        {
            if (percentage < 0.0 || percentage > 1.0)
                throw new IllegalArgumentException("Percentage must be between 0.0 and 1.0");

            allocations.put(type, percentage);
        }

        /**
         * Get the next available time slot for an operation type
         */
        public TimeSlot getNextSlot(OperationType type)
        {
            Double allocation = allocations.get(type);
            if (allocation == null)
                return null;

            // Calculate slot duration based on allocation
            Duration slotDuration = Duration.ofMillis((long) (duration.toMillis() * allocation));

            // Simple round-robin scheduling
            TimeSlot currentSlot = currentSlots.get(type);
            if (currentSlot == null || currentSlot.hasExpired())
            {
                Instant slotStart = Instant.now();
                currentSlot = new TimeSlot(type, slotStart, slotDuration);
                currentSlots.put(type, currentSlot);
            }

            return currentSlot;
        }

        public Duration getDuration()
        {
            return duration;
        }

        public Instant getStartTime()
        {
            return startTime;
        }
    }

    /**
     * Represents a time slot allocated to an operation type
     */
    public static class TimeSlot
    {
        private final OperationType type;
        private final Instant startTime;
        private final Duration duration;

        public TimeSlot(OperationType type, Instant startTime, Duration duration)
        {
            this.type = type;
            this.startTime = startTime;
            this.duration = duration;
        }

        public OperationType getType()
        {
            return type;
        }

        public Instant getStartTime()
        {
            return startTime;
        }

        public Instant getEndTime()
        {
            return startTime.plus(duration);
        }

        public Duration getDuration()
        {
            return duration;
        }

        public boolean isActive()
        {
            Instant now = Instant.now();
            return !now.isBefore(startTime) && now.isBefore(getEndTime());
        }

        public boolean hasExpired()
        {
            return Instant.now().isAfter(getEndTime());
        }

        @Override
        public String toString()
        {
            return String.format("TimeSlot{type=%s, start=%s, duration=%s}",
                               type, startTime, duration);
        }
    }

    /**
     * Types of operations that can be coordinated
     */
    public enum OperationType
    {
        REPAIR,
        COMPACTION,
        NORMAL_OPS
    }

    /**
     * Check if coordination is enabled
     */
    public boolean isCoordinationEnabled()
    {
        return coordinationEnabled;
    }
}
