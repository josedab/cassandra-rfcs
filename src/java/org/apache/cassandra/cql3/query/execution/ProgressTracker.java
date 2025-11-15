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

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks progress of query execution including rows processed,
 * timing, and performance metrics.
 */
public class ProgressTracker
{
    private long processedRows;
    private long startTime;
    private long lastBatchTime;
    private final List<Long> batchTimes;

    public ProgressTracker()
    {
        this.processedRows = 0;
        this.batchTimes = new ArrayList<>();
    }

    /**
     * Start tracking.
     */
    public void start()
    {
        this.startTime = System.currentTimeMillis();
        this.lastBatchTime = startTime;
    }

    /**
     * Update with rows processed in current batch.
     */
    public void update(int rowsProcessed)
    {
        this.processedRows += rowsProcessed;
        long currentTime = System.currentTimeMillis();
        long batchTime = currentTime - lastBatchTime;
        batchTimes.add(batchTime);
        lastBatchTime = currentTime;
    }

    /**
     * Get total rows processed.
     */
    public long getProcessedRows()
    {
        return processedRows;
    }

    /**
     * Get average response time across batches.
     */
    public double getAverageResponseTime()
    {
        if (batchTimes.isEmpty())
        {
            return 0.0;
        }

        long sum = 0;
        for (Long time : batchTimes)
        {
            sum += time;
        }
        return (double) sum / batchTimes.size();
    }

    /**
     * Get total elapsed time.
     */
    public long getElapsedTime()
    {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get throughput in rows per second.
     */
    public double getThroughput()
    {
        long elapsed = getElapsedTime();
        if (elapsed == 0)
        {
            return 0.0;
        }
        return (double) processedRows / (elapsed / 1000.0);
    }
}
