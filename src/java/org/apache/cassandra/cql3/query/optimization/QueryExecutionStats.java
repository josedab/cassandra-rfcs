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
package org.apache.cassandra.cql3.query.optimization;

/**
 * Statistics collected from query execution.
 */
public class QueryExecutionStats
{
    private final long executionTimeMs;
    private final long rowsScanned;
    private final long rowsReturned;
    private final long partitionsScanned;
    private final long bytesRead;
    private final boolean usedAllowFiltering;

    public QueryExecutionStats(long executionTimeMs,
                              long rowsScanned,
                              long rowsReturned,
                              long partitionsScanned,
                              long bytesRead,
                              boolean usedAllowFiltering)
    {
        this.executionTimeMs = executionTimeMs;
        this.rowsScanned = rowsScanned;
        this.rowsReturned = rowsReturned;
        this.partitionsScanned = partitionsScanned;
        this.bytesRead = bytesRead;
        this.usedAllowFiltering = usedAllowFiltering;
    }

    public long getExecutionTimeMs()
    {
        return executionTimeMs;
    }

    public long getRowsScanned()
    {
        return rowsScanned;
    }

    public long getRowsReturned()
    {
        return rowsReturned;
    }

    public long getPartitionsScanned()
    {
        return partitionsScanned;
    }

    public long getBytesRead()
    {
        return bytesRead;
    }

    public boolean usedAllowFiltering()
    {
        return usedAllowFiltering;
    }

    /**
     * Calculate the scan efficiency (ratio of rows returned to rows scanned).
     */
    public double getScanEfficiency()
    {
        return rowsScanned > 0 ? (double) rowsReturned / rowsScanned : 0.0;
    }
}
