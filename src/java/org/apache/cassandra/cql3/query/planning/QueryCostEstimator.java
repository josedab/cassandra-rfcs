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

import org.apache.cassandra.cql3.query.statistics.StatisticsCollector;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.index.IndexRegistry;
import org.apache.cassandra.schema.TableMetadata;

/**
 * Estimates the cost of executing a query based on table statistics, query restrictions,
 * and available indexes.
 *
 * The cost estimation considers:
 * - Number of partitions to scan
 * - Number of rows to scan
 * - Amount of data to read
 * - Network overhead
 * - Index usage
 */
public class QueryCostEstimator
{
    private final TableMetadata table;
    private final IndexRegistry indexRegistry;
    private final StatisticsCollector statistics;

    // Cost weights for different operations
    private static final double PARTITION_SCAN_WEIGHT = 1.0;
    private static final double ROW_SCAN_WEIGHT = 0.1;
    private static final double BYTE_READ_WEIGHT = 0.001;
    private static final double NETWORK_WEIGHT = 2.0;

    public QueryCostEstimator(TableMetadata table,
                             IndexRegistry indexRegistry,
                             StatisticsCollector statistics)
    {
        this.table = table;
        this.indexRegistry = indexRegistry;
        this.statistics = statistics;
    }

    /**
     * Estimate the cost of executing a SELECT statement.
     *
     * @param select the SELECT statement to estimate
     * @return QueryCost representing the estimated cost
     */
    public QueryCost estimate(SelectStatement select)
    {
        QueryCost cost = new QueryCost();

        // Estimate partitions to read
        PartitionEstimate partitions = estimatePartitions(select);
        cost.setPartitionsToScan(partitions.getCount());

        // Estimate rows to scan
        RowEstimate rows = estimateRows(select, partitions);
        cost.setRowsToScan(rows.getCount());

        // Estimate data size
        DataSizeEstimate dataSize = estimateDataSize(rows);
        cost.setBytesToRead(dataSize.getBytes());

        // Calculate network cost
        NetworkCost network = estimateNetworkCost(select, dataSize);
        cost.setNetworkCost(network.getCost());

        // Calculate total cost
        cost.setTotalCost(calculateTotalCost(cost));

        // Generate description
        cost.setDescription(generateDescription(select, cost));

        return cost;
    }

    /**
     * Estimate the number of partitions that will be scanned.
     */
    private PartitionEstimate estimatePartitions(SelectStatement select)
    {
        // This is a simplified implementation. In a real implementation,
        // we would analyze the WHERE clause restrictions on partition keys.

        // For demonstration, assume full table scan unless we have specific restrictions
        long estimatedPartitions = statistics.getEstimatedPartitionCount(table);

        // If we had partition key restrictions, we could reduce this estimate
        // For now, return the full table estimate
        return new PartitionEstimate(estimatedPartitions, 0.8);
    }

    /**
     * Estimate the number of rows that will be scanned.
     */
    private RowEstimate estimateRows(SelectStatement select, PartitionEstimate partitions)
    {
        // Get average rows per partition
        double avgRowsPerPartition = statistics.getAverageRowsPerPartition(table);

        // Apply clustering key selectivity (default to 1.0 for full partition scan)
        double selectivity = 1.0;

        // In a real implementation, we would:
        // 1. Analyze clustering key restrictions to estimate selectivity
        // 2. Apply filter selectivity for regular column restrictions
        // 3. Consider index selectivity

        long estimatedRows = (long)(partitions.getCount() * avgRowsPerPartition * selectivity);
        return new RowEstimate(estimatedRows, selectivity);
    }

    /**
     * Estimate the amount of data that will be read.
     */
    private DataSizeEstimate estimateDataSize(RowEstimate rows)
    {
        long avgRowSize = statistics.getAverageRowSize(table);
        long uncompressedBytes = rows.getCount() * avgRowSize;

        double compressionRatio = statistics.getCompressionRatio(table);
        long compressedBytes = (long)(uncompressedBytes * compressionRatio);

        return new DataSizeEstimate(uncompressedBytes, compressedBytes);
    }

    /**
     * Estimate the network cost of the query.
     */
    private NetworkCost estimateNetworkCost(SelectStatement select, DataSizeEstimate dataSize)
    {
        // In a real implementation, we would:
        // 1. Determine consistency level
        // 2. Calculate number of replicas to contact
        // 3. Estimate network bandwidth usage

        int replicasContacted = 1; // Simplified
        long bytesTransferred = dataSize.getCompressedBytes();
        double networkCost = bytesTransferred * 0.001; // Simplified cost calculation

        return new NetworkCost(networkCost, replicasContacted, bytesTransferred);
    }

    /**
     * Calculate the total cost by combining all cost components.
     */
    private double calculateTotalCost(QueryCost cost)
    {
        return (cost.getPartitionsToScan() * PARTITION_SCAN_WEIGHT) +
               (cost.getRowsToScan() * ROW_SCAN_WEIGHT) +
               (cost.getBytesToRead() * BYTE_READ_WEIGHT) +
               (cost.getNetworkCost() * NETWORK_WEIGHT);
    }

    /**
     * Generate a human-readable description of the query cost.
     */
    private String generateDescription(SelectStatement select, QueryCost cost)
    {
        StringBuilder desc = new StringBuilder();

        if (cost.getPartitionsToScan() > 10000)
        {
            desc.append("WARNING: Large partition scan. ");
        }

        if (cost.getRowsToScan() > 100000)
        {
            desc.append("WARNING: Large row scan. ");
        }

        double scanRatio = cost.getRowsToScan() > 0 ?
            (double)cost.getPartitionsToScan() / cost.getRowsToScan() : 0.0;

        if (scanRatio < 0.01)
        {
            desc.append("Query has low partition-to-row ratio, consider adding index. ");
        }

        return desc.toString();
    }
}
