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

/**
 * Snapshot of system load metrics at a point in time.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class LoadSnapshot
{
    private final double cpuUsage;
    private final double memoryUsage;
    private final int compactionPending;
    private final int activeRepairs;
    private final double readLatency;
    private final double writeLatency;
    private final long timestamp;

    private LoadSnapshot(Builder builder)
    {
        this.cpuUsage = builder.cpuUsage;
        this.memoryUsage = builder.memoryUsage;
        this.compactionPending = builder.compactionPending;
        this.activeRepairs = builder.activeRepairs;
        this.readLatency = builder.readLatency;
        this.writeLatency = builder.writeLatency;
        this.timestamp = builder.timestamp;
    }

    public double getCpuUsage()
    {
        return cpuUsage;
    }

    public double getMemoryUsage()
    {
        return memoryUsage;
    }

    public int getCompactionPending()
    {
        return compactionPending;
    }

    public int getActiveRepairs()
    {
        return activeRepairs;
    }

    public double getReadLatency()
    {
        return readLatency;
    }

    public double getWriteLatency()
    {
        return writeLatency;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    @Override
    public String toString()
    {
        return String.format("LoadSnapshot{cpu=%.2f, memory=%.2f, compaction=%d, repairs=%d, read=%.2fms, write=%.2fms}",
                           cpuUsage, memoryUsage, compactionPending, activeRepairs, readLatency, writeLatency);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private double cpuUsage;
        private double memoryUsage;
        private int compactionPending;
        private int activeRepairs;
        private double readLatency;
        private double writeLatency;
        private long timestamp;

        public Builder cpuUsage(double cpuUsage)
        {
            this.cpuUsage = cpuUsage;
            return this;
        }

        public Builder memoryUsage(double memoryUsage)
        {
            this.memoryUsage = memoryUsage;
            return this;
        }

        public Builder compactionPending(int compactionPending)
        {
            this.compactionPending = compactionPending;
            return this;
        }

        public Builder activeRepairs(int activeRepairs)
        {
            this.activeRepairs = activeRepairs;
            return this;
        }

        public Builder readLatency(double readLatency)
        {
            this.readLatency = readLatency;
            return this;
        }

        public Builder writeLatency(double writeLatency)
        {
            this.writeLatency = writeLatency;
            return this;
        }

        public Builder timestamp(long timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        public LoadSnapshot build()
        {
            return new LoadSnapshot(this);
        }
    }
}
