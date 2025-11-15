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

import org.apache.cassandra.cql3.query.optimization.PartitionRange;

/**
 * Provides statistics about partitions for query optimization.
 */
public interface PartitionStatistics
{
    /**
     * Get statistics for a specific partition range.
     *
     * @param range the partition range
     * @return range statistics
     */
    RangeStatistics getStatistics(PartitionRange range);

    /**
     * Statistics for a partition range including min/max values.
     */
    interface RangeStatistics
    {
        /**
         * Get the minimum clustering values in this range.
         */
        Object getMinClustering();

        /**
         * Get the maximum clustering values in this range.
         */
        Object getMaxClustering();

        /**
         * Get the minimum timestamp in this range.
         */
        long getMinTimestamp();

        /**
         * Get the maximum timestamp in this range.
         */
        long getMaxTimestamp();

        /**
         * Get the estimated number of partitions in this range.
         */
        long getEstimatedPartitions();

        /**
         * Get the estimated number of rows in this range.
         */
        long getEstimatedRows();
    }
}
