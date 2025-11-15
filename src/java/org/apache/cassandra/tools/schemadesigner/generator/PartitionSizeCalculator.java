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
package org.apache.cassandra.tools.schemadesigner.generator;

import org.apache.cassandra.tools.schemadesigner.model.Column;
import org.apache.cassandra.tools.schemadesigner.model.Table;
import org.apache.cassandra.tools.schemadesigner.model.WorkloadProfile;

/**
 * Estimates partition sizes for capacity planning.
 */
public class PartitionSizeCalculator
{
    private static final int DEFAULT_ROWS_PER_PARTITION = 1000;
    private static final int OVERHEAD_PER_ROW = 100; // bytes

    /**
     * Estimates the maximum partition size for a table.
     *
     * @param table Table to analyze
     * @param workload Workload characteristics
     * @return Estimated partition size in bytes
     */
    public long estimate(Table table, WorkloadProfile workload)
    {
        int estimatedRowsPerPartition = estimateRowsPerPartition(table, workload);
        int avgRowSize = estimateAverageRowSize(table);

        return (long) estimatedRowsPerPartition * (avgRowSize + OVERHEAD_PER_ROW);
    }

    private int estimateRowsPerPartition(Table table, WorkloadProfile workload)
    {
        // If table has clustering columns, estimate based on data pattern
        if (!table.getClusteringColumns().isEmpty())
        {
            switch (workload.getDataPattern())
            {
                case TIME_SERIES:
                    // Time series data: estimate based on retention
                    int recordsPerDay = workload.getExpectedQPS() / 10; // Rough estimate
                    return Math.min(recordsPerDay * workload.getRetentionDays(), 100000);

                case SEQUENTIAL:
                    return 5000;

                case RANDOM:
                default:
                    return DEFAULT_ROWS_PER_PARTITION;
            }
        }

        // Single row per partition
        return 1;
    }

    private int estimateAverageRowSize(Table table)
    {
        int totalSize = 0;

        // Partition key
        for (Column col : table.getPartitionKey())
        {
            totalSize += estimateColumnSize(col);
        }

        // Clustering columns
        for (Column col : table.getClusteringColumns())
        {
            totalSize += estimateColumnSize(col);
        }

        // Regular columns
        for (Column col : table.getColumns())
        {
            totalSize += estimateColumnSize(col);
        }

        return totalSize;
    }

    private int estimateColumnSize(Column column)
    {
        String type = column.getType().toUpperCase();

        if (type.equals("UUID"))
            return 16;
        if (type.equals("TIMESTAMP") || type.equals("BIGINT"))
            return 8;
        if (type.equals("INT"))
            return 4;
        if (type.equals("BOOLEAN"))
            return 1;
        if (type.equals("TEXT") || type.equals("VARCHAR"))
            return 100; // Average text size
        if (type.equals("DECIMAL"))
            return 8;
        if (type.startsWith("LIST<") || type.startsWith("SET<") || type.startsWith("MAP<"))
            return 500; // Average collection size

        return 50; // Default
    }
}
