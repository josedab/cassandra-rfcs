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

import java.util.concurrent.TimeoutException;

import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.cql3.query.planning.QueryPlan;
import org.apache.cassandra.cql3.statements.SelectStatement;

/**
 * Manages the adaptive execution of a single query, tracking progress
 * and adjusting parameters dynamically.
 */
public class AdaptiveExecution
{
    private final SelectStatement select;
    private volatile QueryPlan currentPlan;
    private final ProgressTracker progressTracker;
    private final ResultCollector resultCollector;
    private final AdaptiveQueryExecutor.ResourceManager resourceManager;

    private static final double TARGET_RESPONSE_TIME = 100.0; // ms
    private static final int MAX_BATCH_SIZE_UNDER_PRESSURE = 100;

    private boolean started;
    private boolean complete;

    public AdaptiveExecution(SelectStatement select,
                            QueryPlan initialPlan,
                            AdaptiveQueryExecutor.ExecutionMonitor monitor,
                            AdaptiveQueryExecutor.ResourceManager resourceManager)
    {
        this.select = select;
        this.currentPlan = initialPlan;
        this.progressTracker = new ProgressTracker();
        this.resultCollector = new ResultCollector();
        this.resourceManager = resourceManager;
        this.started = false;
        this.complete = false;
    }

    /**
     * Start query execution.
     */
    public void start()
    {
        started = true;
        progressTracker.start();
    }

    /**
     * Process the next batch of results with adaptive batch sizing.
     */
    public void processNextBatch()
    {
        BatchSize batchSize = calculateAdaptiveBatchSize();

        try
        {
            // In real implementation, would fetch rows from storage
            // For now, simulate batch processing
            int rowsProcessed = Math.min(batchSize.getSize(), 100);

            // Track progress
            progressTracker.update(rowsProcessed);

            // Check if we're done
            if (progressTracker.getProcessedRows() >= 1000) // Simplified completion check
            {
                complete = true;
            }
        }
        catch (Exception e)
        {
            if (e instanceof TimeoutException)
            {
                handleTimeout();
            }
            else
            {
                throw new RuntimeException("Error processing batch", e);
            }
        }
    }

    /**
     * Calculate adaptive batch size based on current performance.
     */
    private BatchSize calculateAdaptiveBatchSize()
    {
        // Start with default
        int size = currentPlan.getInitialBatchSize();

        // Adjust based on response time
        double avgResponseTime = progressTracker.getAverageResponseTime();
        if (avgResponseTime < TARGET_RESPONSE_TIME * 0.5)
        {
            size = (int)(size * 1.5); // Increase batch size
        }
        else if (avgResponseTime > TARGET_RESPONSE_TIME)
        {
            size = (int)(size * 0.75); // Decrease batch size
        }

        // Adjust based on memory pressure
        double memoryUsage = resourceManager.getMemoryUsage();
        if (memoryUsage > 0.8)
        {
            size = Math.min(size, MAX_BATCH_SIZE_UNDER_PRESSURE);
        }

        return new BatchSize(size);
    }

    /**
     * Handle timeout during execution.
     */
    private void handleTimeout()
    {
        if (progressTracker.getProcessedRows() > 0)
        {
            // Return partial results with warning
            resultCollector.setPartialResult(true);
            resultCollector.addWarning("Query timed out, returning partial results");
            complete = true;
        }
        else
        {
            throw new RuntimeException("Query exceeded timeout with no results");
        }
    }

    /**
     * Update the query plan during execution.
     */
    public void updatePlan(QueryPlan newPlan)
    {
        this.currentPlan = newPlan;
    }

    /**
     * Get the current query plan.
     */
    public QueryPlan getCurrentPlan()
    {
        return currentPlan;
    }

    /**
     * Check if execution is complete.
     */
    public boolean isComplete()
    {
        return complete;
    }

    /**
     * Get the final results.
     */
    public ResultSet getResults()
    {
        return resultCollector.getResultSet();
    }

    /**
     * Get the progress tracker for monitoring.
     */
    public ProgressTracker getProgressTracker()
    {
        return progressTracker;
    }

    /**
     * Cleanup resources.
     */
    public void cleanup()
    {
        resourceManager.releaseResources();
    }
}
