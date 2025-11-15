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
package org.apache.cassandra.io.storage.tier;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.apache.cassandra.io.storage.tier.AccessTracker.AccessType;

/**
 * Statistics about SSTable access patterns.
 */
public class AccessStats
{
    private final LongAdder totalReads = new LongAdder();
    private final LongAdder totalWrites = new LongAdder();
    private final LongAdder totalCompactions = new LongAdder();
    private final LongAdder recentReads = new LongAdder();
    private final LongAdder recentWrites = new LongAdder();
    private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong creationTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastAgeTime = new AtomicLong(System.currentTimeMillis());

    public void recordAccess(AccessType type)
    {
        lastAccessTime.set(System.currentTimeMillis());

        switch (type)
        {
            case READ:
                totalReads.increment();
                recentReads.increment();
                break;
            case WRITE:
                totalWrites.increment();
                recentWrites.increment();
                break;
            case COMPACTION:
                totalCompactions.increment();
                break;
        }
    }

    /**
     * Get the total number of read accesses.
     *
     * @return total read count
     */
    public long getTotalReads()
    {
        return totalReads.sum();
    }

    /**
     * Get the total number of write accesses.
     *
     * @return total write count
     */
    public long getTotalWrites()
    {
        return totalWrites.sum();
    }

    /**
     * Get the total number of compaction accesses.
     *
     * @return total compaction count
     */
    public long getTotalCompactions()
    {
        return totalCompactions.sum();
    }

    /**
     * Get the total number of accesses.
     *
     * @return total access count
     */
    public long getTotalAccesses()
    {
        return getTotalReads() + getTotalWrites() + getTotalCompactions();
    }

    /**
     * Get recent access rate (accesses per minute).
     *
     * @return access rate
     */
    public double getRecentAccessRate()
    {
        long elapsedMs = System.currentTimeMillis() - lastAgeTime.get();
        if (elapsedMs == 0)
        {
            return 0.0;
        }

        long recentAccesses = recentReads.sum() + recentWrites.sum();
        double elapsedMinutes = elapsedMs / 60000.0;

        return recentAccesses / elapsedMinutes;
    }

    /**
     * Get the last access time.
     *
     * @return timestamp of last access
     */
    public long getLastAccessTime()
    {
        return lastAccessTime.get();
    }

    /**
     * Get the time since last access.
     *
     * @return milliseconds since last access
     */
    public long getTimeSinceLastAccess()
    {
        return System.currentTimeMillis() - lastAccessTime.get();
    }

    /**
     * Get the creation time.
     *
     * @return timestamp when stats were created
     */
    public long getCreationTime()
    {
        return creationTime.get();
    }

    /**
     * Age the statistics (decay recent counters).
     */
    public void age()
    {
        // Reset recent counters for next period
        recentReads.reset();
        recentWrites.reset();
        lastAgeTime.set(System.currentTimeMillis());
    }

    @Override
    public String toString()
    {
        return String.format("AccessStats{reads=%d, writes=%d, compactions=%d, rate=%.2f/min}",
                           getTotalReads(), getTotalWrites(), getTotalCompactions(),
                           getRecentAccessRate());
    }
}
