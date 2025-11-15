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
package org.apache.cassandra.cql3.query.execution;

import org.apache.cassandra.cql3.query.planning.ExecutionParams;
import org.apache.cassandra.cql3.query.planning.QueryPlan;

/**
 * Adjusts query execution plans based on runtime metrics and observations.
 */
public class PlanAdjuster
{
    // Thresholds for plan adjustments
    private static final int HIGH_QUEUE_THRESHOLD = 100;
    private static final double IDLE_THRESHOLD = 0.5; // 50% idle time
    private static final double FALSE_POSITIVE_THRESHOLD = 0.3; // 30% false positive rate

    /**
     * Adjust the query plan based on execution snapshot.
     *
     * @param snapshot current execution metrics
     * @param currentPlan the current query plan
     * @return adjusted query plan
     */
    public QueryPlan adjust(ExecutionSnapshot snapshot, QueryPlan currentPlan)
    {
        QueryPlan adjusted = currentPlan.copy();
        ExecutionParams params = adjusted.getExecutionParams();

        // Adjust parallelism based on queue depth and idle time
        if (snapshot.getQueueDepth() > HIGH_QUEUE_THRESHOLD)
        {
            params.decreaseParallelism();
        }
        else if (snapshot.getIdleTime() > IDLE_THRESHOLD)
        {
            params.increaseParallelism();
        }

        // Adjust timeout based on estimated completion time
        double estimatedCompletion = estimateTimeToCompletion(snapshot);
        if (estimatedCompletion > params.getTimeout())
        {
            params.setTimeout((long)(estimatedCompletion * 1.2)); // Add 20% buffer
        }

        // Switch index strategy if false positive rate is too high
        if (snapshot.getFalsePositiveRate() > FALSE_POSITIVE_THRESHOLD)
        {
            // Would disable index usage or switch to different index
            // For now, just log this situation
        }

        return adjusted;
    }

    /**
     * Estimate time to completion based on current progress.
     */
    private double estimateTimeToCompletion(ExecutionSnapshot snapshot)
    {
        // Simplified estimation
        // In reality, would use more sophisticated prediction
        double avgResponseTime = snapshot.getAverageResponseTime();
        long processedRows = snapshot.getProcessedRows();

        // Assume similar rate for remaining work
        return avgResponseTime * 10; // Simplified
    }
}
