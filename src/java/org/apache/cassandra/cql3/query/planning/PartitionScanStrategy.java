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

/**
 * Defines the strategy for scanning partitions during query execution.
 */
public enum PartitionScanStrategy
{
    /**
     * Single partition lookup by partition key.
     */
    SINGLE_PARTITION,

    /**
     * Multiple specific partitions (e.g., from IN clause).
     */
    MULTI_PARTITION,

    /**
     * Range scan within a single partition.
     */
    PARTITION_RANGE,

    /**
     * Token range scan across multiple partitions.
     */
    TOKEN_RANGE,

    /**
     * Full table scan.
     */
    FULL_TABLE_SCAN,

    /**
     * Index-based scan.
     */
    INDEX_SCAN
}
