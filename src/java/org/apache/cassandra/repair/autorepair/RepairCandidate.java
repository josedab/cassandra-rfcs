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
import java.util.Collection;
import java.util.Objects;

import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;

/**
 * Represents a candidate for repair with associated metadata.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairCandidate
{
    private final String keyspace;
    private final String table;
    private final Collection<Range<Token>> ranges;
    private final long lastRepairTime;
    private final long estimatedDataSize;
    private final double priority;

    public RepairCandidate(String keyspace,
                          String table,
                          Collection<Range<Token>> ranges,
                          long lastRepairTime,
                          long estimatedDataSize,
                          double priority)
    {
        this.keyspace = Objects.requireNonNull(keyspace);
        this.table = Objects.requireNonNull(table);
        this.ranges = Objects.requireNonNull(ranges);
        this.lastRepairTime = lastRepairTime;
        this.estimatedDataSize = estimatedDataSize;
        this.priority = priority;
    }

    public String getKeyspace()
    {
        return keyspace;
    }

    public String getTable()
    {
        return table;
    }

    public Collection<Range<Token>> getRanges()
    {
        return ranges;
    }

    public long getLastRepairTime()
    {
        return lastRepairTime;
    }

    public Duration getTimeSinceLastRepair()
    {
        return Duration.ofMillis(System.currentTimeMillis() - lastRepairTime);
    }

    public long getEstimatedDataSize()
    {
        return estimatedDataSize;
    }

    public double getPriority()
    {
        return priority;
    }

    public String getTokenRangeString()
    {
        if (ranges.isEmpty())
            return "empty";

        Range<Token> first = ranges.iterator().next();
        if (ranges.size() == 1)
            return String.format("(%s, %s]", first.left, first.right);
        else
            return String.format("(%s, %s] and %d more", first.left, first.right, ranges.size() - 1);
    }

    @Override
    public String toString()
    {
        return String.format("RepairCandidate{keyspace=%s, table=%s, ranges=%d, priority=%.2f}",
                           keyspace, table, ranges.size(), priority);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepairCandidate that = (RepairCandidate) o;
        return keyspace.equals(that.keyspace) &&
               table.equals(that.table) &&
               ranges.equals(that.ranges);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(keyspace, table, ranges);
    }
}
