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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Logs repair scheduling decisions for observability and debugging.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairDecisionLogger
{
    private static final Logger logger = LoggerFactory.getLogger(RepairDecisionLogger.class);

    // Keep recent decisions in memory for virtual table access
    private static final int MAX_DECISIONS_IN_MEMORY = 1000;
    private final ConcurrentLinkedQueue<RepairDecision> recentDecisions = new ConcurrentLinkedQueue<>();

    // Statistics
    private final AtomicLong totalScheduled = new AtomicLong(0);
    private final AtomicLong totalSkipped = new AtomicLong(0);
    private final AtomicLong totalDeferred = new AtomicLong(0);
    private final AtomicLong totalPrioritized = new AtomicLong(0);

    private final boolean enableLogging;

    public RepairDecisionLogger(boolean enableLogging)
    {
        this.enableLogging = enableLogging;
    }

    /**
     * Log a repair decision
     */
    public void logDecision(RepairDecision decision)
    {
        // Update statistics
        switch (decision.getType())
        {
            case SCHEDULED:
                totalScheduled.incrementAndGet();
                break;
            case SKIPPED:
                totalSkipped.incrementAndGet();
                break;
            case DEFERRED:
                totalDeferred.incrementAndGet();
                break;
            case PRIORITIZED:
                totalPrioritized.incrementAndGet();
                break;
        }

        // Store in memory for virtual table access
        recentDecisions.offer(decision);
        while (recentDecisions.size() > MAX_DECISIONS_IN_MEMORY)
        {
            recentDecisions.poll();
        }

        // Log if enabled
        if (enableLogging)
        {
            logger.info("Repair decision: type={}, keyspace={}, table={}, range={}, reason={}",
                       decision.getType(),
                       decision.getKeyspace(),
                       decision.getTable(),
                       decision.getTokenRange(),
                       decision.getReason());
        }
        else if (decision.getType() == RepairDecision.DecisionType.DEFERRED)
        {
            // Always log deferred decisions at debug level
            logger.debug("Repair deferred: keyspace={}, table={}, reason={}",
                        decision.getKeyspace(),
                        decision.getTable(),
                        decision.getReason());
        }
    }

    /**
     * Get recent decisions for virtual table access
     */
    public ConcurrentLinkedQueue<RepairDecision> getRecentDecisions()
    {
        return recentDecisions;
    }

    /**
     * Get decision statistics
     */
    public DecisionStatistics getStatistics()
    {
        return new DecisionStatistics(
            totalScheduled.get(),
            totalSkipped.get(),
            totalDeferred.get(),
            totalPrioritized.get()
        );
    }

    /**
     * Reset statistics (for testing)
     */
    @VisibleForTesting
    public void reset()
    {
        recentDecisions.clear();
        totalScheduled.set(0);
        totalSkipped.set(0);
        totalDeferred.set(0);
        totalPrioritized.set(0);
    }

    public static class DecisionStatistics
    {
        public final long scheduled;
        public final long skipped;
        public final long deferred;
        public final long prioritized;

        public DecisionStatistics(long scheduled, long skipped, long deferred, long prioritized)
        {
            this.scheduled = scheduled;
            this.skipped = skipped;
            this.deferred = deferred;
            this.prioritized = prioritized;
        }

        public long getTotal()
        {
            return scheduled + skipped + deferred + prioritized;
        }

        @Override
        public String toString()
        {
            return String.format("DecisionStats{scheduled=%d, skipped=%d, deferred=%d, prioritized=%d}",
                               scheduled, skipped, deferred, prioritized);
        }
    }
}
