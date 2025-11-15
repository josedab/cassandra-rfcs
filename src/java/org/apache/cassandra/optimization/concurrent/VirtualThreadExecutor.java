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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Virtual thread executor for Cassandra read/write operations.
 * Uses Project Loom virtual threads to handle thousands of concurrent requests
 * with minimal OS thread overhead.
 */
public class VirtualThreadExecutor
{
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadExecutor.class);

    private final ExecutorService virtualExecutor;
    private final Semaphore concurrencyLimiter;
    private final ThreadFactory virtualThreadFactory;
    private final int maxConcurrency;

    public VirtualThreadExecutor(int maxConcurrency)
    {
        this.maxConcurrency = maxConcurrency;
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.virtualThreadFactory = Thread.ofVirtual()
            .name("cassandra-vthread-", 0)
            .factory();
        this.virtualExecutor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);

        logger.info("VirtualThreadExecutor initialized with max concurrency: {}", maxConcurrency);
    }

    /**
     * Submit a task to be executed in a virtual thread
     */
    public void submit(Runnable task)
    {
        virtualExecutor.submit(() -> {
            try {
                concurrencyLimiter.acquire();
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Virtual thread interrupted", e);
            } catch (Exception e) {
                logger.error("Error executing task in virtual thread", e);
            } finally {
                concurrencyLimiter.release();
            }
        });
    }

    /**
     * Shutdown the executor gracefully
     */
    public void shutdown()
    {
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get current available permits
     */
    public int availablePermits()
    {
        return concurrencyLimiter.availablePermits();
    }

    /**
     * Get max concurrency setting
     */
    public int getMaxConcurrency()
    {
        return maxConcurrency;
    }
}
