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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.*;

public class WaitFreeCounterTest
{
    @Test
    public void testIncrement()
    {
        WaitFreeCounter counter = new WaitFreeCounter();

        counter.increment();
        assertEquals("Counter should be 1", 1, counter.sum());

        counter.increment();
        assertEquals("Counter should be 2", 2, counter.sum());
    }

    @Test
    public void testAdd()
    {
        WaitFreeCounter counter = new WaitFreeCounter();

        counter.add(10);
        assertEquals("Counter should be 10", 10, counter.sum());

        counter.add(5);
        assertEquals("Counter should be 15", 15, counter.sum());
    }

    @Test
    public void testDecrement()
    {
        WaitFreeCounter counter = new WaitFreeCounter();

        counter.add(10);
        counter.decrement();
        assertEquals("Counter should be 9", 9, counter.sum());
    }

    @Test
    public void testReset()
    {
        WaitFreeCounter counter = new WaitFreeCounter();

        counter.add(100);
        assertEquals("Counter should be 100", 100, counter.sum());

        counter.reset();
        assertEquals("Counter should be 0 after reset", 0, counter.sum());
    }

    @Test
    public void testConcurrentIncrements() throws Exception
    {
        WaitFreeCounter counter = new WaitFreeCounter();
        int threadCount = 10;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++)
        {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++)
                {
                    counter.increment();
                }
                latch.countDown();
            });
        }

        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));

        long expected = (long) threadCount * incrementsPerThread;
        assertEquals("Counter should equal total increments", expected, counter.sum());

        executor.shutdown();
    }

    @Test
    public void testStripeCount()
    {
        WaitFreeCounter counter = new WaitFreeCounter();

        int stripeCount = counter.getStripeCount();
        assertTrue("Stripe count should be power of 2",
                   Integer.bitCount(stripeCount) == 1);
        assertTrue("Stripe count should be at least 4",
                   stripeCount >= 4);
    }
}
