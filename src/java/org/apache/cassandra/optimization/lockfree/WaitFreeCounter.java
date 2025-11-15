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
package org.apache.cassandra.optimization.lockfree;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Wait-free counter using striped approach to avoid contention.
 * Each thread updates its own stripe, reducing cache line bouncing.
 */
public class WaitFreeCounter
{
    // Striped counters to avoid contention
    private final AtomicLong[] counters;
    private final int mask;

    public WaitFreeCounter()
    {
        this(Runtime.getRuntime().availableProcessors() * 4);
    }

    public WaitFreeCounter(int stripes)
    {
        // Round up to next power of 2
        int size = Integer.highestOneBit(stripes - 1) << 1;
        if (size < stripes)
            size = Integer.highestOneBit(size) << 1;

        this.counters = new AtomicLong[size];
        this.mask = size - 1;

        for (int i = 0; i < size; i++)
        {
            counters[i] = new AtomicLong();
        }
    }

    /**
     * Increment the counter (wait-free)
     */
    public void increment()
    {
        int index = getIndex();
        counters[index].incrementAndGet();
    }

    /**
     * Add a value to the counter
     */
    public void add(long delta)
    {
        int index = getIndex();
        counters[index].addAndGet(delta);
    }

    /**
     * Decrement the counter
     */
    public void decrement()
    {
        int index = getIndex();
        counters[index].decrementAndGet();
    }

    /**
     * Get the total count across all stripes
     */
    public long sum()
    {
        long total = 0;
        for (AtomicLong counter : counters)
        {
            total += counter.get();
        }
        return total;
    }

    /**
     * Reset all counters to zero
     */
    public void reset()
    {
        for (AtomicLong counter : counters)
        {
            counter.set(0);
        }
    }

    /**
     * Get the stripe index for current thread
     */
    private int getIndex()
    {
        // Use thread ID to determine stripe
        // This ensures same thread always hits same stripe
        return (int) (Thread.currentThread().threadId() & mask);
    }

    /**
     * Get number of stripes
     */
    public int getStripeCount()
    {
        return counters.length;
    }
}
