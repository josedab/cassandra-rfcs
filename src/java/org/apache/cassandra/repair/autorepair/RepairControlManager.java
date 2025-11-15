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
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.audit.AuditLogEntryType;

/**
 * Manages control operations for auto repair (pause, resume, status).
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairControlManager
{
    private static final Logger logger = LoggerFactory.getLogger(RepairControlManager.class);

    public enum RepairState
    {
        RUNNING,
        PAUSED,
        SUSPENDED,  // Paused by system (e.g., during bootstrap)
        STOPPING
    }

    private final AtomicReference<RepairState> state;
    private final ScheduledExecutorService executor;
    private volatile Instant pauseUntil;
    private volatile ScheduledFuture<?> resumeTask;
    private volatile String pauseReason;

    public RepairControlManager(ScheduledExecutorService executor)
    {
        this.state = new AtomicReference<>(RepairState.RUNNING);
        this.executor = executor;
        this.pauseUntil = null;
    }

    /**
     * Pause auto repair for a specified duration
     *
     * @param duration How long to pause
     * @param reason Reason for the pause (for audit logging)
     * @return Result of the pause operation
     */
    public PauseResult pauseAutoRepair(Duration duration, String reason)
    {
        if (!state.compareAndSet(RepairState.RUNNING, RepairState.PAUSED))
        {
            return PauseResult.failure("Auto repair is not currently running (state: " + state.get() + ")");
        }

        pauseUntil = Instant.now().plus(duration);
        pauseReason = reason;

        logger.info("Auto repair paused for {} until {} - Reason: {}", duration, pauseUntil, reason);

        // Audit log the pause event
        logAuditEvent("AUTO_REPAIR_PAUSED", String.format("duration=%s, reason=%s, resume_at=%s",
                                                         duration, reason, pauseUntil));

        // Schedule automatic resume
        if (resumeTask != null)
        {
            resumeTask.cancel(false);
        }
        resumeTask = executor.schedule(this::resumeAutoRepair, duration.toMillis(), TimeUnit.MILLISECONDS);

        return PauseResult.success()
                         .withResumeTime(pauseUntil)
                         .withMessage("Auto repair paused until " + pauseUntil);
    }

    /**
     * Resume auto repair
     *
     * @return Result of the resume operation
     */
    public ResumeResult resumeAutoRepair()
    {
        return resumeAutoRepair(false);
    }

    /**
     * Resume auto repair
     *
     * @param force Force resume even if pause duration hasn't expired
     * @return Result of the resume operation
     */
    public ResumeResult resumeAutoRepair(boolean force)
    {
        RepairState currentState = state.get();

        if (currentState == RepairState.RUNNING)
        {
            return ResumeResult.failure("Auto repair is already running");
        }

        if (currentState != RepairState.PAUSED && !force)
        {
            return ResumeResult.failure("Auto repair is not currently paused (state: " + currentState + ")");
        }

        if (!state.compareAndSet(currentState, RepairState.RUNNING))
        {
            return ResumeResult.failure("Failed to resume auto repair (state changed)");
        }

        logger.info("Auto repair resumed{}", force ? " (forced)" : "");

        // Audit log the resume event
        logAuditEvent("AUTO_REPAIR_RESUMED", force ? "forced=true" : "forced=false");

        // Cancel scheduled resume task if any
        if (resumeTask != null)
        {
            resumeTask.cancel(false);
            resumeTask = null;
        }

        pauseUntil = null;
        pauseReason = null;

        return ResumeResult.success()
                          .withMessage("Auto repair resumed successfully");
    }

    /**
     * Get current auto repair status
     *
     * @return Status information
     */
    public StatusResult getStatus()
    {
        return StatusResult.builder()
                          .state(state.get())
                          .pausedUntil(pauseUntil)
                          .pauseReason(pauseReason)
                          .build();
    }

    /**
     * Check if auto repair is currently active
     *
     * @return true if repair is running, false otherwise
     */
    public boolean isActive()
    {
        return state.get() == RepairState.RUNNING;
    }

    /**
     * Check if auto repair is paused
     *
     * @return true if repair is paused, false otherwise
     */
    public boolean isPaused()
    {
        return state.get() == RepairState.PAUSED;
    }

    /**
     * Suspend auto repair (system-initiated pause)
     */
    public void suspend(String reason)
    {
        RepairState current = state.get();
        if (current == RepairState.RUNNING || current == RepairState.PAUSED)
        {
            state.set(RepairState.SUSPENDED);
            pauseReason = reason;
            logger.info("Auto repair suspended: {}", reason);
            logAuditEvent("AUTO_REPAIR_SUSPENDED", "reason=" + reason);
        }
    }

    /**
     * Get current state
     */
    public RepairState getState()
    {
        return state.get();
    }

    /**
     * Set state directly (for testing)
     */
    @VisibleForTesting
    public void setState(RepairState newState)
    {
        state.set(newState);
    }

    private void logAuditEvent(String eventType, String message)
    {
        try
        {
            // Simplified audit logging - actual implementation would use proper audit framework
            logger.info("AUDIT: {} - {}", eventType, message);
        }
        catch (Exception e)
        {
            logger.warn("Failed to log audit event", e);
        }
    }

    // Result classes

    public static class PauseResult extends OperationResult
    {
        private Instant resumeTime;

        public static PauseResult success()
        {
            PauseResult result = new PauseResult();
            result.success = true;
            return result;
        }

        public static PauseResult failure(String message)
        {
            PauseResult result = new PauseResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public PauseResult withResumeTime(Instant resumeTime)
        {
            this.resumeTime = resumeTime;
            return this;
        }

        public PauseResult withMessage(String message)
        {
            this.message = message;
            return this;
        }

        public Instant getResumeTime()
        {
            return resumeTime;
        }
    }

    public static class ResumeResult extends OperationResult
    {
        public static ResumeResult success()
        {
            ResumeResult result = new ResumeResult();
            result.success = true;
            return result;
        }

        public static ResumeResult failure(String message)
        {
            ResumeResult result = new ResumeResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public ResumeResult withMessage(String message)
        {
            this.message = message;
            return this;
        }
    }

    public static class StatusResult
    {
        private final RepairState state;
        private final Instant pausedUntil;
        private final String pauseReason;

        private StatusResult(Builder builder)
        {
            this.state = builder.state;
            this.pausedUntil = builder.pausedUntil;
            this.pauseReason = builder.pauseReason;
        }

        public RepairState getState()
        {
            return state;
        }

        public Instant getPausedUntil()
        {
            return pausedUntil;
        }

        public String getPauseReason()
        {
            return pauseReason;
        }

        public static Builder builder()
        {
            return new Builder();
        }

        public static class Builder
        {
            private RepairState state;
            private Instant pausedUntil;
            private String pauseReason;

            public Builder state(RepairState state)
            {
                this.state = state;
                return this;
            }

            public Builder pausedUntil(Instant pausedUntil)
            {
                this.pausedUntil = pausedUntil;
                return this;
            }

            public Builder pauseReason(String pauseReason)
            {
                this.pauseReason = pauseReason;
                return this;
            }

            public StatusResult build()
            {
                return new StatusResult(this);
            }
        }
    }

    public static abstract class OperationResult
    {
        protected boolean success;
        protected String message;

        public boolean isSuccess()
        {
            return success;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
