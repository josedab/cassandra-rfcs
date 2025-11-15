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

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.io.sstable.format.SSTableReader;

/**
 * Scheduler for managing SSTable migrations between storage tiers.
 */
public class MigrationScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(MigrationScheduler.class);

    private final ThreadPoolExecutor migrationExecutor;
    private final PriorityBlockingQueue<MigrationTask> migrationQueue;
    private final Map<UUID, MigrationTask> activeMigrations;
    private final MigrationConfig config;

    public MigrationScheduler(MigrationConfig config)
    {
        this.config = config;
        this.migrationQueue = new PriorityBlockingQueue<>();
        this.activeMigrations = new ConcurrentHashMap<>();
        this.migrationExecutor = new ThreadPoolExecutor(
            config.getThreadCount(),
            config.getThreadCount(),
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory()
            {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r)
                {
                    Thread t = new Thread(r, "TierMigration-" + counter++);
                    t.setDaemon(true);
                    return t;
                }
            }
        );

        startScheduler();
    }

    /**
     * Schedule a migration task.
     *
     * @param sstable the SSTable to migrate
     * @param sourceTier the source tier
     * @param targetTier the target tier
     * @param reason the reason for migration
     * @return the migration task ID
     */
    public UUID scheduleMigration(SSTableReader sstable,
                                  StorageTier sourceTier,
                                  StorageTier targetTier,
                                  MigrationReason reason)
    {
        MigrationTask task = new MigrationTask(
            UUID.randomUUID(),
            sstable,
            sourceTier,
            targetTier,
            reason
        );

        // Calculate priority
        int priority = calculatePriority(task);
        task.setPriority(priority);

        migrationQueue.offer(task);
        logger.info("Scheduled migration: {} from {} to {} (reason: {}, priority: {})",
                   sstable.descriptor, sourceTier.getName(), targetTier.getName(),
                   reason, priority);

        triggerMigrationIfNeeded();
        return task.getMigrationId();
    }

    /**
     * Cancel a migration task.
     *
     * @param migrationId the migration ID
     * @return true if cancelled successfully
     */
    public boolean cancelMigration(UUID migrationId)
    {
        MigrationTask task = activeMigrations.get(migrationId);
        if (task != null)
        {
            task.cancel();
            activeMigrations.remove(migrationId);
            logger.info("Cancelled migration: {}", migrationId);
            return true;
        }
        return false;
    }

    /**
     * Get the status of a migration.
     *
     * @param migrationId the migration ID
     * @return the migration status, or null if not found
     */
    public MigrationStatus getStatus(UUID migrationId)
    {
        MigrationTask task = activeMigrations.get(migrationId);
        return task != null ? task.getStatus() : null;
    }

    /**
     * Get all active migrations.
     *
     * @return collection of active migrations
     */
    public Collection<MigrationTask> getActiveMigrations()
    {
        return Collections.unmodifiableCollection(activeMigrations.values());
    }

    /**
     * Shutdown the scheduler.
     */
    public void shutdown()
    {
        migrationExecutor.shutdown();
        try
        {
            if (!migrationExecutor.awaitTermination(60, TimeUnit.SECONDS))
            {
                migrationExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            migrationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void startScheduler()
    {
        Thread scheduler = new Thread(() ->
        {
            while (!Thread.currentThread().isInterrupted())
            {
                try
                {
                    MigrationTask task = migrationQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null)
                    {
                        submitMigration(task);
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "TierMigrationScheduler");
        scheduler.setDaemon(true);
        scheduler.start();
    }

    private void submitMigration(MigrationTask task)
    {
        activeMigrations.put(task.getMigrationId(), task);
        migrationExecutor.submit(() -> executeMigration(task));
    }

    private void executeMigration(MigrationTask task)
    {
        try
        {
            task.setStatus(MigrationStatus.IN_PROGRESS);
            logger.info("Starting migration: {}", task.getMigrationId());

            SSTableReader source = task.getSSTable();
            StorageTier targetTier = task.getTargetTier();

            // TODO: Implement actual migration logic
            // This would involve:
            // 1. Creating a writer for the target tier
            // 2. Copying data from source to target
            // 3. Updating references
            // 4. Deleting from source tier

            // Simulate migration time
            Thread.sleep(1000);

            task.setStatus(MigrationStatus.COMPLETED);
            logger.info("Completed migration: {}", task.getMigrationId());
        }
        catch (Exception e)
        {
            logger.error("Migration failed: {}", task.getMigrationId(), e);
            task.setStatus(MigrationStatus.FAILED);
            task.setError(e.getMessage());
        }
        finally
        {
            activeMigrations.remove(task.getMigrationId());
        }
    }

    private int calculatePriority(MigrationTask task)
    {
        // Higher priority (lower number) for:
        // - High access frequency promotions
        // - Cost-saving demotions
        // - Smaller SSTables (faster to migrate)

        int basePriority = 100;

        switch (task.getReason())
        {
            case HIGH_ACCESS_FREQUENCY:
                basePriority -= 50; // High priority
                break;
            case LOW_ACCESS_FREQUENCY:
                basePriority += 20; // Lower priority
                break;
            case COST_OPTIMIZATION:
                basePriority -= 30; // Medium-high priority
                break;
            case AGE_BASED:
                basePriority += 10; // Lower priority
                break;
            case MANUAL:
                basePriority -= 100; // Highest priority
                break;
        }

        // Adjust for SSTable size (prefer smaller ones)
        long sizeGB = task.getSSTable().onDiskLength() / (1024L * 1024L * 1024L);
        basePriority += Math.min(sizeGB, 50); // Cap size adjustment

        return basePriority;
    }

    private void triggerMigrationIfNeeded()
    {
        // Check if we should start more migrations
        int active = activeMigrations.size();
        int maxConcurrent = config.getThreadCount();

        if (active < maxConcurrent && !migrationQueue.isEmpty())
        {
            // Scheduler thread will pick it up
        }
    }

    /**
     * Configuration for migration scheduler.
     */
    public static class MigrationConfig
    {
        private final int threadCount;
        private final long throughputMbPerSec;

        public MigrationConfig(int threadCount, long throughputMbPerSec)
        {
            this.threadCount = threadCount;
            this.throughputMbPerSec = throughputMbPerSec;
        }

        public int getThreadCount()
        {
            return threadCount;
        }

        public long getThroughputMbPerSec()
        {
            return throughputMbPerSec;
        }

        public static MigrationConfig defaults()
        {
            return new MigrationConfig(2, 100);
        }
    }

    /**
     * Migration reasons.
     */
    public enum MigrationReason
    {
        AGE_BASED,
        HIGH_ACCESS_FREQUENCY,
        LOW_ACCESS_FREQUENCY,
        COST_OPTIMIZATION,
        MANUAL,
        TIER_CAPACITY
    }

    /**
     * Migration status.
     */
    public enum MigrationStatus
    {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
