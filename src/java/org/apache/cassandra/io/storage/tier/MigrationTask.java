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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.storage.tier.MigrationScheduler.MigrationReason;
import org.apache.cassandra.io.storage.tier.MigrationScheduler.MigrationStatus;

/**
 * Represents a migration task for moving an SSTable between tiers.
 */
public class MigrationTask implements Comparable<MigrationTask>
{
    private final UUID migrationId;
    private final SSTableReader sstable;
    private final StorageTier sourceTier;
    private final StorageTier targetTier;
    private final MigrationReason reason;
    private final long createdAt;

    private final AtomicInteger priority = new AtomicInteger(100);
    private final AtomicInteger progressPercent = new AtomicInteger(0);
    private final AtomicReference<MigrationStatus> status = new AtomicReference<>(MigrationStatus.PENDING);
    private volatile long startedAt;
    private volatile long completedAt;
    private volatile String errorMessage;
    private volatile boolean cancelled;

    public MigrationTask(UUID migrationId,
                        SSTableReader sstable,
                        StorageTier sourceTier,
                        StorageTier targetTier,
                        MigrationReason reason)
    {
        this.migrationId = migrationId;
        this.sstable = sstable;
        this.sourceTier = sourceTier;
        this.targetTier = targetTier;
        this.reason = reason;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getMigrationId()
    {
        return migrationId;
    }

    public SSTableReader getSSTable()
    {
        return sstable;
    }

    public StorageTier getSourceTier()
    {
        return sourceTier;
    }

    public StorageTier getTargetTier()
    {
        return targetTier;
    }

    public MigrationReason getReason()
    {
        return reason;
    }

    public long getCreatedAt()
    {
        return createdAt;
    }

    public int getPriority()
    {
        return priority.get();
    }

    public void setPriority(int priority)
    {
        this.priority.set(priority);
    }

    public int getProgressPercent()
    {
        return progressPercent.get();
    }

    public void setProgressPercent(int percent)
    {
        this.progressPercent.set(percent);
    }

    public MigrationStatus getStatus()
    {
        return status.get();
    }

    public void setStatus(MigrationStatus newStatus)
    {
        MigrationStatus oldStatus = status.getAndSet(newStatus);

        if (newStatus == MigrationStatus.IN_PROGRESS && oldStatus == MigrationStatus.PENDING)
        {
            startedAt = System.currentTimeMillis();
        }
        else if (newStatus == MigrationStatus.COMPLETED || newStatus == MigrationStatus.FAILED)
        {
            completedAt = System.currentTimeMillis();
        }
    }

    public long getStartedAt()
    {
        return startedAt;
    }

    public long getCompletedAt()
    {
        return completedAt;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setError(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public boolean isCancelled()
    {
        return cancelled;
    }

    public void cancel()
    {
        this.cancelled = true;
        setStatus(MigrationStatus.CANCELLED);
    }

    public long getElapsedTimeMs()
    {
        if (startedAt == 0)
        {
            return 0;
        }
        long endTime = completedAt != 0 ? completedAt : System.currentTimeMillis();
        return endTime - startedAt;
    }

    @Override
    public int compareTo(MigrationTask other)
    {
        // Lower priority number = higher priority
        return Integer.compare(this.priority.get(), other.priority.get());
    }

    @Override
    public String toString()
    {
        return String.format("MigrationTask{id=%s, sstable=%s, %s->%s, reason=%s, status=%s}",
                           migrationId, sstable.descriptor,
                           sourceTier.getName(), targetTier.getName(),
                           reason, status.get());
    }
}
