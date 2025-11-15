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
package org.apache.cassandra.cql3.query.planning;

import java.util.UUID;

import org.apache.cassandra.cql3.query.optimization.IndexPlan;

/**
 * Represents a complete query execution plan including index usage,
 * scan strategy, and execution parameters.
 */
public class QueryPlan
{
    private final UUID planId;
    private IndexPlan indexPlan;
    private PartitionScanStrategy scanStrategy;
    private ExecutionParams executionParams;
    private QueryCost estimatedCost;
    private long creationTime;

    public QueryPlan()
    {
        this.planId = UUID.randomUUID();
        this.creationTime = System.currentTimeMillis();
    }

    public UUID getPlanId()
    {
        return planId;
    }

    public IndexPlan getIndexPlan()
    {
        return indexPlan;
    }

    public void setIndexPlan(IndexPlan indexPlan)
    {
        this.indexPlan = indexPlan;
    }

    public PartitionScanStrategy getScanStrategy()
    {
        return scanStrategy;
    }

    public void setScanStrategy(PartitionScanStrategy scanStrategy)
    {
        this.scanStrategy = scanStrategy;
    }

    public ExecutionParams getExecutionParams()
    {
        return executionParams;
    }

    public void setExecutionParams(ExecutionParams params)
    {
        this.executionParams = params;
    }

    public QueryCost getEstimatedCost()
    {
        return estimatedCost;
    }

    public void setEstimatedCost(QueryCost cost)
    {
        this.estimatedCost = cost;
    }

    public long getCreationTime()
    {
        return creationTime;
    }

    /**
     * Create a copy of this query plan for adjustments.
     */
    public QueryPlan copy()
    {
        QueryPlan copy = new QueryPlan();
        copy.indexPlan = this.indexPlan;
        copy.scanStrategy = this.scanStrategy;
        copy.executionParams = this.executionParams != null ? this.executionParams.copy() : null;
        copy.estimatedCost = this.estimatedCost;
        return copy;
    }

    /**
     * Get the initial batch size for query execution.
     */
    public int getInitialBatchSize()
    {
        return executionParams != null ? executionParams.getPageSize() : 100;
    }

    /**
     * Get the timeout for query execution.
     */
    public long getTimeout()
    {
        return executionParams != null ? executionParams.getTimeout() : 10000L;
    }

    @Override
    public String toString()
    {
        return String.format("QueryPlan{id=%s, indexPlan=%s, scanStrategy=%s, params=%s}",
                             planId, indexPlan, scanStrategy, executionParams);
    }
}
