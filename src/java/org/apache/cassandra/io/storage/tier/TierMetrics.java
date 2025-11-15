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
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Metrics for a storage tier.
 */
public class TierMetrics
{
    private final String tierName;
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong writeCount = new AtomicLong();
    private final AtomicLong readCount = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final DoubleAdder readLatencySum = new DoubleAdder();
    private final DoubleAdder writeLatencySum = new DoubleAdder();

    public TierMetrics(String tierName)
    {
        this.tierName = tierName;
    }

    public void recordWrite(long bytes)
    {
        bytesWritten.addAndGet(bytes);
        writeCount.incrementAndGet();
    }

    public void recordWrite(long bytes, double latencyMs)
    {
        recordWrite(bytes);
        writeLatencySum.add(latencyMs);
    }

    public void recordRead(long bytes)
    {
        bytesRead.addAndGet(bytes);
        readCount.incrementAndGet();
    }

    public void recordRead(long bytes, double latencyMs)
    {
        recordRead(bytes);
        readLatencySum.add(latencyMs);
    }

    public void recordCacheHit()
    {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss()
    {
        cacheMisses.incrementAndGet();
    }

    public String getTierName()
    {
        return tierName;
    }

    public long getBytesWritten()
    {
        return bytesWritten.get();
    }

    public long getBytesRead()
    {
        return bytesRead.get();
    }

    public long getWriteCount()
    {
        return writeCount.get();
    }

    public long getReadCount()
    {
        return readCount.get();
    }

    public long getCacheHits()
    {
        return cacheHits.get();
    }

    public long getCacheMisses()
    {
        return cacheMisses.get();
    }

    public double getCacheHitRatio()
    {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public double getAverageReadLatency()
    {
        long count = readCount.get();
        return count == 0 ? 0.0 : readLatencySum.sum() / count;
    }

    public double getAverageWriteLatency()
    {
        long count = writeCount.get();
        return count == 0 ? 0.0 : writeLatencySum.sum() / count;
    }
}
