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
package org.apache.cassandra.optimization.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class VirtualThreadExecutorTest
{
    private VirtualThreadExecutor executor;

    @Before
    public void setUp()
    {
        executor = new VirtualThreadExecutor(100);
    }

    @After
    public void tearDown()
    {
        if (executor != null)
        {
            executor.shutdown();
        }
    }

    @Test
    public void testSimpleSubmit() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        executor.submit(() -> {
            result.set(42);
            latch.countDown();
        });

        assertTrue("Task should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        assertEquals("Result should be set", 42, result.get());
    }

    @Test
    public void testMultipleSubmits() throws Exception
    {
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++)
        {
            executor.submit(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        assertTrue("All tasks should complete", latch.await(5, TimeUnit.SECONDS));
        assertEquals("All tasks should have run", taskCount, counter.get());
    }

    @Test
    public void testConcurrencyLimit()
    {
        VirtualThreadExecutor limitedExecutor = new VirtualThreadExecutor(5);

        try
        {
            assertEquals("Should have 5 available permits", 5, limitedExecutor.availablePermits());

            CountDownLatch startLatch = new CountDownLatch(5);
            CountDownLatch finishLatch = new CountDownLatch(1);

            // Submit 5 tasks that will block
            for (int i = 0; i < 5; i++)
            {
                limitedExecutor.submit(() -> {
                    try
                    {
                        startLatch.countDown();
                        finishLatch.await();
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // Release the tasks
            finishLatch.countDown();
        }
        finally
        {
            limitedExecutor.shutdown();
        }
    }

    @Test
    public void testMaxConcurrency()
    {
        assertEquals("Max concurrency should be 100", 100, executor.getMaxConcurrency());
    }
}
