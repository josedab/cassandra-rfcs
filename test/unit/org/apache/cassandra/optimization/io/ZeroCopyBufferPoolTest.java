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
package org.apache.cassandra.optimization.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ZeroCopyBufferPoolTest
{
    private ZeroCopyBufferPool pool;
    private static final long POOL_CAPACITY = 1024 * 1024; // 1MB
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    @Before
    public void setUp()
    {
        pool = new ZeroCopyBufferPool(POOL_CAPACITY, CHUNK_SIZE);
    }

    @After
    public void tearDown()
    {
        if (pool != null)
        {
            pool.shutdown();
        }
    }

    @Test
    public void testAcquireBuffer()
    {
        DirectBuffer buffer = pool.acquire();

        assertNotNull("Buffer should not be null", buffer);
        assertEquals("Buffer size should match chunk size", CHUNK_SIZE, buffer.getSize());
    }

    @Test
    public void testReleaseBuffer()
    {
        DirectBuffer buffer = pool.acquire();
        assertNotNull("Buffer should not be null", buffer);

        pool.release(buffer);

        // Acquire again - should get the same buffer from pool
        DirectBuffer buffer2 = pool.acquire();
        assertNotNull("Second buffer should not be null", buffer2);
    }

    @Test
    public void testMultipleAcquireRelease()
    {
        DirectBuffer buffer1 = pool.acquire();
        DirectBuffer buffer2 = pool.acquire();

        assertNotNull("First buffer should not be null", buffer1);
        assertNotNull("Second buffer should not be null", buffer2);
        assertNotSame("Buffers should be different", buffer1, buffer2);

        pool.release(buffer1);
        pool.release(buffer2);

        ZeroCopyBufferPool.PoolStats stats = pool.getStats();
        assertEquals("Should have 2 available buffers", 2, stats.availableBuffers);
    }

    @Test
    public void testPoolStats()
    {
        pool.acquire();
        pool.acquire();

        ZeroCopyBufferPool.PoolStats stats = pool.getStats();

        assertEquals("Total capacity should match", POOL_CAPACITY, stats.totalCapacity);
        assertEquals("Chunk size should match", CHUNK_SIZE, stats.chunkSize);
        assertTrue("Allocated bytes should be positive", stats.allocatedBytes > 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testPoolExhaustion()
    {
        // Acquire buffers until pool is exhausted
        int maxBuffers = (int) (POOL_CAPACITY / CHUNK_SIZE) + 1;

        for (int i = 0; i < maxBuffers + 1; i++)
        {
            pool.acquire();
        }
    }

    @Test
    public void testBufferClear()
    {
        DirectBuffer buffer = pool.acquire();

        // Write some data
        buffer.getBuffer().putInt(42);

        // Release and reacquire
        pool.release(buffer);
        DirectBuffer buffer2 = pool.acquire();

        // Buffer should be cleared
        assertEquals("Buffer position should be 0", 0, buffer2.getBuffer().position());
    }
}
