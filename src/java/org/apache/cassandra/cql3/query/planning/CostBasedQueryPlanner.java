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

import org.apache.cassandra.cql3.query.optimization.IndexPlan;
import org.apache.cassandra.cql3.query.optimization.MultiIndexOptimizer;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.index.IndexRegistry;

/**
 * Creates optimized query execution plans based on cost estimates.
 * Determines the best index usage, scan strategy, and execution parameters.
 */
public class CostBasedQueryPlanner
{
    private final MultiIndexOptimizer indexOptimizer;
    private final QueryCostEstimator costEstimator;

    // Thresholds for determining scan strategy
    private static final long LARGE_PARTITION_THRESHOLD = 1000;
    private static final long LARGE_ROW_THRESHOLD = 100000;
    private static final long TIMEOUT_BASE = 5000L; // 5 seconds
    private static final int DEFAULT_PAGE_SIZE = 100;

    public CostBasedQueryPlanner(QueryCostEstimator costEstimator,
                                MultiIndexOptimizer indexOptimizer)
    {
        this.costEstimator = costEstimator;
        this.indexOptimizer = indexOptimizer;
    }

    /**
     * Create an optimized execution plan for a SELECT statement.
     *
     * @param select the SELECT statement
     * @param cost the estimated cost
     * @return optimized QueryPlan
     */
    public QueryPlan createPlan(SelectStatement select, QueryCost cost)
    {
        QueryPlan plan = new QueryPlan();
        plan.setEstimatedCost(cost);

        // Determine if indexes should be used
        IndexPlan indexPlan = selectBestIndexes(select, cost);
        plan.setIndexPlan(indexPlan);

        // Determine partition scan strategy
        PartitionScanStrategy scanStrategy = selectScanStrategy(select, cost, indexPlan);
        plan.setScanStrategy(scanStrategy);

        // Set execution parameters
        ExecutionParams params = new ExecutionParams();
        params.setPageSize(calculateOptimalPageSize(cost));
        params.setTimeout(calculateTimeout(cost));
        params.setParallelism(calculateParallelism(cost));
        plan.setExecutionParams(params);

        return plan;
    }

    /**
     * Select the best indexes to use for the query.
     */
    private IndexPlan selectBestIndexes(SelectStatement select, QueryCost cost)
    {
        if (indexOptimizer != null)
        {
            return indexOptimizer.optimizeIndexUsage(select);
        }
        return IndexPlan.noIndex();
    }

    /**
     * Determine the best partition scan strategy.
     */
    private PartitionScanStrategy selectScanStrategy(SelectStatement select,
                                                     QueryCost cost,
                                                     IndexPlan indexPlan)
    {
        // If using indexes, use index scan strategy
        if (indexPlan != null && indexPlan.usesIndex())
        {
            return PartitionScanStrategy.INDEX_SCAN;
        }

        // Otherwise determine based on query structure
        // In a real implementation, we would analyze the SELECT statement
        // For now, use a simple heuristic based on cost

        if (cost.getPartitionsToScan() == 1)
        {
            return PartitionScanStrategy.SINGLE_PARTITION;
        }
        else if (cost.getPartitionsToScan() < 100)
        {
            return PartitionScanStrategy.MULTI_PARTITION;
        }
        else if (cost.getPartitionsToScan() < LARGE_PARTITION_THRESHOLD)
        {
            return PartitionScanStrategy.TOKEN_RANGE;
        }
        else
        {
            return PartitionScanStrategy.FULL_TABLE_SCAN;
        }
    }

    /**
     * Calculate the optimal page size based on query cost.
     */
    private int calculateOptimalPageSize(QueryCost cost)
    {
        // Adjust page size based on expected result size
        long expectedRows = cost.getRowsToScan();

        if (expectedRows < 100)
        {
            return (int)expectedRows;
        }
        else if (expectedRows < 10000)
        {
            return 1000;
        }
        else
        {
            return 5000;
        }
    }

    /**
     * Calculate appropriate timeout based on query cost.
     */
    private long calculateTimeout(QueryCost cost)
    {
        // Base timeout plus additional time based on cost
        long baseTimeout = TIMEOUT_BASE;

        // Add time based on partitions to scan
        long partitionTimeout = cost.getPartitionsToScan() / 100 * 1000; // 1s per 100 partitions

        // Add time based on data to read
        long dataTimeout = cost.getBytesToRead() / (1024 * 1024) * 100; // 100ms per MB

        return baseTimeout + partitionTimeout + dataTimeout;
    }

    /**
     * Calculate optimal parallelism level based on query cost.
     */
    private int calculateParallelism(QueryCost cost)
    {
        // Use higher parallelism for larger scans
        if (cost.getPartitionsToScan() > LARGE_PARTITION_THRESHOLD)
        {
            return 8;
        }
        else if (cost.getPartitionsToScan() > 100)
        {
            return 4;
        }
        else
        {
            return 1;
        }
    }
}
