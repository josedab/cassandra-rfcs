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

package org.apache.cassandra.repair.autorepair;

import java.time.Duration;

/**
 * Represents the result of a repair operation with metrics.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairResult
{
    private final String keyspace;
    private final String table;
    private final long bytesRepaired;
    private final long bytesValidated;
    private final int discrepanciesFound;
    private final Duration duration;
    private final int merkleTreeCount;
    private final int streamingSessionCount;
    private final boolean success;

    private RepairResult(Builder builder)
    {
        this.keyspace = builder.keyspace;
        this.table = builder.table;
        this.bytesRepaired = builder.bytesRepaired;
        this.bytesValidated = builder.bytesValidated;
        this.discrepanciesFound = builder.discrepanciesFound;
        this.duration = builder.duration;
        this.merkleTreeCount = builder.merkleTreeCount;
        this.streamingSessionCount = builder.streamingSessionCount;
        this.success = builder.success;
    }

    public String getKeyspace()
    {
        return keyspace;
    }

    public String getTable()
    {
        return table;
    }

    public long getBytesRepaired()
    {
        return bytesRepaired;
    }

    public long getBytesValidated()
    {
        return bytesValidated;
    }

    public int getDiscrepanciesFound()
    {
        return discrepanciesFound;
    }

    public Duration getDuration()
    {
        return duration;
    }

    public int getMerkleTreeCount()
    {
        return merkleTreeCount;
    }

    public int getStreamingSessionCount()
    {
        return streamingSessionCount;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private String keyspace;
        private String table;
        private long bytesRepaired;
        private long bytesValidated;
        private int discrepanciesFound;
        private Duration duration;
        private int merkleTreeCount;
        private int streamingSessionCount;
        private boolean success = true;

        public Builder keyspace(String keyspace)
        {
            this.keyspace = keyspace;
            return this;
        }

        public Builder table(String table)
        {
            this.table = table;
            return this;
        }

        public Builder bytesRepaired(long bytesRepaired)
        {
            this.bytesRepaired = bytesRepaired;
            return this;
        }

        public Builder bytesValidated(long bytesValidated)
        {
            this.bytesValidated = bytesValidated;
            return this;
        }

        public Builder discrepanciesFound(int discrepanciesFound)
        {
            this.discrepanciesFound = discrepanciesFound;
            return this;
        }

        public Builder duration(Duration duration)
        {
            this.duration = duration;
            return this;
        }

        public Builder merkleTreeCount(int merkleTreeCount)
        {
            this.merkleTreeCount = merkleTreeCount;
            return this;
        }

        public Builder streamingSessionCount(int streamingSessionCount)
        {
            this.streamingSessionCount = streamingSessionCount;
            return this;
        }

        public Builder success(boolean success)
        {
            this.success = success;
            return this;
        }

        public RepairResult build()
        {
            return new RepairResult(this);
        }
    }
}
