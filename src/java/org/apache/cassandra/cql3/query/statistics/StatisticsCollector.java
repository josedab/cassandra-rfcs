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
package org.apache.cassandra.cql3.query.statistics;

import org.apache.cassandra.schema.TableMetadata;

/**
 * Interface for collecting and providing table statistics used for query cost estimation.
 * Implementations should collect statistics asynchronously and use sampling to minimize overhead.
 */
public interface StatisticsCollector
{
    /**
     * Get the estimated total number of partitions in a table.
     *
     * @param table the table metadata
     * @return estimated partition count
     */
    long getEstimatedPartitionCount(TableMetadata table);

    /**
     * Get the average number of rows per partition for a table.
     *
     * @param table the table metadata
     * @return average rows per partition
     */
    double getAverageRowsPerPartition(TableMetadata table);

    /**
     * Get the average size of a row in bytes.
     *
     * @param table the table metadata
     * @return average row size in bytes
     */
    long getAverageRowSize(TableMetadata table);

    /**
     * Get the average partition size in bytes.
     *
     * @param table the table metadata
     * @return average partition size in bytes
     */
    long getAveragePartitionSize(TableMetadata table);

    /**
     * Get the compression ratio for the table's data.
     *
     * @param table the table metadata
     * @return compression ratio (0.0 to 1.0, where 1.0 means no compression)
     */
    double getCompressionRatio(TableMetadata table);

    /**
     * Update statistics for a table asynchronously.
     *
     * @param table the table to update statistics for
     */
    void updateStatistics(TableMetadata table);

    /**
     * Clear cached statistics for a table.
     *
     * @param table the table to clear statistics for
     */
    void clearStatistics(TableMetadata table);
}
