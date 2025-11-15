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

package org.apache.cassandra.tcm.migration;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.apache.cassandra.tcm.ClusterMetadata;

/**
 * Tracks CMS migration progress with checkpoints and event logging.
 * Provides real-time visibility into migration status and enables rollback capabilities.
 */
public class CMSMigrationTracker
{
    private static final Logger logger = LoggerFactory.getLogger(CMSMigrationTracker.class);

    private final AtomicReference<MigrationState> currentState;
    private final MigrationMetrics metrics;
    private final CheckpointStore checkpointStore;
    private final List<MigrationEvent> events;

    private static final int MAX_EVENTS = 1000;
    private static final int CHECKPOINT_PERCENTAGE_INTERVAL = 25;

    public CMSMigrationTracker()
    {
        this.currentState = new AtomicReference<>(MigrationState.NOT_STARTED);
        this.metrics = new MigrationMetrics();
        this.checkpointStore = new CheckpointStore();
        this.events = new CopyOnWriteArrayList<>();
    }

    /**
     * Starts migration tracking.
     */
    public void startMigration(CMSMigrationPlanner.MigrationPlan plan)
    {
        currentState.set(MigrationState.INITIALIZING);
        metrics.recordMigrationStart();

        MigrationEvent event = new MigrationEvent(
            MigrationEventType.MIGRATION_STARTED,
            "Migration started",
            Map.of("estimated_duration_minutes", String.valueOf(plan.getEstimatedDuration().toMinutes()))
        );
        recordEvent(event);

        // Create initial checkpoint
        createCheckpoint("migration_start", "Initial state before migration");

        logger.info("Migration tracking started with plan: {}", plan);
    }

    /**
     * Updates migration progress.
     */
    public void updateProgress(String phase, double percentage, String message)
    {
        MigrationProgress progress = new MigrationProgress(phase, percentage, message, Instant.now());
        currentState.updateAndGet(state -> state.withProgress(progress));

        metrics.recordProgress(phase, percentage);

        MigrationEvent event = new MigrationEvent(
            MigrationEventType.PROGRESS_UPDATE,
            message,
            Map.of(
                "phase", phase,
                "percentage", String.valueOf(percentage)
            )
        );
        recordEvent(event);

        // Create checkpoint at major milestones
        if (percentage > 0 && percentage % CHECKPOINT_PERCENTAGE_INTERVAL == 0)
        {
            String checkpointName = String.format("phase_%s_%d", phase, (int) percentage);
            String description = String.format("Checkpoint at %s %.0f%%", phase, percentage);
            createCheckpoint(checkpointName, description);
        }

        logger.debug("Migration progress: {} - {}% - {}", phase, percentage, message);
    }

    /**
     * Marks a phase as complete.
     */
    public void completePhase(String phase)
    {
        MigrationEvent event = new MigrationEvent(
            MigrationEventType.PHASE_COMPLETED,
            "Phase completed: " + phase,
            Map.of("phase", phase)
        );
        recordEvent(event);

        createCheckpoint("phase_" + phase + "_complete", "Phase " + phase + " completed successfully");

        logger.info("Migration phase completed: {}", phase);
    }

    /**
     * Records a migration error.
     */
    public void recordError(String phase, String error, Exception exception)
    {
        MigrationState errorState = MigrationState.FAILED.withError(error);
        currentState.set(errorState);

        metrics.recordError(phase);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("phase", phase);
        metadata.put("error", error);
        if (exception != null)
        {
            metadata.put("exception", exception.getClass().getName());
            metadata.put("message", exception.getMessage());
        }

        MigrationEvent event = new MigrationEvent(
            MigrationEventType.ERROR_OCCURRED,
            error,
            metadata
        );
        recordEvent(event);

        createCheckpoint("error_" + phase, "Error occurred during " + phase + ": " + error);

        logger.error("Migration error in phase {}: {}", phase, error, exception);
    }

    /**
     * Marks migration as complete.
     */
    public void completeMigration()
    {
        currentState.set(MigrationState.COMPLETED);
        metrics.recordMigrationComplete();

        MigrationEvent event = new MigrationEvent(
            MigrationEventType.MIGRATION_COMPLETED,
            "Migration completed successfully",
            Map.of()
        );
        recordEvent(event);

        createCheckpoint("migration_complete", "Migration completed successfully");

        logger.info("Migration completed successfully");
    }

    /**
     * Gets current migration state.
     */
    public MigrationState getCurrentState()
    {
        return currentState.get();
    }

    /**
     * Gets recent migration events.
     */
    public List<MigrationEvent> getRecentEvents(int limit)
    {
        int size = events.size();
        int start = Math.max(0, size - limit);
        return new ArrayList<>(events.subList(start, size));
    }

    /**
     * Gets all migration events.
     */
    public List<MigrationEvent> getAllEvents()
    {
        return new ArrayList<>(events);
    }

    /**
     * Gets available checkpoints.
     */
    public List<MigrationCheckpoint> getCheckpoints()
    {
        return checkpointStore.getAllCheckpoints();
    }

    /**
     * Gets a specific checkpoint.
     */
    public MigrationCheckpoint getCheckpoint(String checkpointId)
    {
        return checkpointStore.getCheckpoint(checkpointId);
    }

    private void recordEvent(MigrationEvent event)
    {
        events.add(event);

        // Trim events if too many
        while (events.size() > MAX_EVENTS)
        {
            events.remove(0);
        }
    }

    private void createCheckpoint(String checkpointName, String description)
    {
        try
        {
            MigrationCheckpoint checkpoint = new MigrationCheckpoint(
                checkpointName,
                description,
                Instant.now(),
                currentState.get(),
                captureClusterState()
            );

            checkpointStore.save(checkpoint);
            logger.info("Created checkpoint: {} - {}", checkpointName, description);
        }
        catch (Exception e)
        {
            logger.error("Failed to create checkpoint: {}", checkpointName, e);
        }
    }

    private Map<String, String> captureClusterState()
    {
        // In a real implementation, this would capture actual cluster state
        Map<String, String> state = new HashMap<>();
        state.put("timestamp", Instant.now().toString());
        state.put("migration_state", currentState.get().toString());
        // Add more state information as needed
        return state;
    }

    /**
     * Migration state enumeration.
     */
    public enum MigrationState
    {
        NOT_STARTED,
        INITIALIZING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLING_BACK;

        private MigrationProgress progress;
        private String errorMessage;

        public MigrationState withProgress(MigrationProgress progress)
        {
            MigrationState state = this == IN_PROGRESS ? this : IN_PROGRESS;
            state.progress = progress;
            return state;
        }

        public MigrationState withError(String error)
        {
            MigrationState state = FAILED;
            state.errorMessage = error;
            return state;
        }

        public MigrationProgress getProgress()
        {
            return progress;
        }

        public String getErrorMessage()
        {
            return errorMessage;
        }
    }

    /**
     * Migration progress information.
     */
    public static class MigrationProgress
    {
        public final String phase;
        public final double percentage;
        public final String message;
        public final Instant timestamp;
        public final Instant estimatedCompletion;

        public MigrationProgress(String phase, double percentage, String message, Instant timestamp)
        {
            this.phase = phase;
            this.percentage = percentage;
            this.message = message;
            this.timestamp = timestamp;
            this.estimatedCompletion = calculateEstimatedCompletion(percentage, timestamp);
        }

        private Instant calculateEstimatedCompletion(double percentage, Instant current)
        {
            if (percentage <= 0)
                return null;

            // Simple estimation based on current progress
            // In reality, this would use more sophisticated estimation
            long estimatedRemainingMinutes = (long) ((100.0 - percentage) / percentage * 10);
            return current.plusSeconds(estimatedRemainingMinutes * 60);
        }

        @Override
        public String toString()
        {
            return String.format("%s: %.2f%% - %s", phase, percentage, message);
        }
    }

    /**
     * Migration event types.
     */
    public enum MigrationEventType
    {
        MIGRATION_STARTED,
        PROGRESS_UPDATE,
        PHASE_COMPLETED,
        ERROR_OCCURRED,
        CHECKPOINT_CREATED,
        MIGRATION_COMPLETED,
        ROLLBACK_STARTED,
        ROLLBACK_COMPLETED
    }

    /**
     * Individual migration event.
     */
    public static class MigrationEvent
    {
        public final UUID eventId;
        public final MigrationEventType type;
        public final String description;
        public final Instant timestamp;
        public final Map<String, String> metadata;

        public MigrationEvent(MigrationEventType type, String description, Map<String, String> metadata)
        {
            this.eventId = UUID.randomUUID();
            this.type = type;
            this.description = description;
            this.timestamp = Instant.now();
            this.metadata = new HashMap<>(metadata);
        }

        @Override
        public String toString()
        {
            return String.format("[%s] %s: %s", timestamp, type, description);
        }
    }

    /**
     * Migration checkpoint for rollback.
     */
    public static class MigrationCheckpoint
    {
        public final String checkpointId;
        public final String description;
        public final Instant timestamp;
        public final MigrationState migrationState;
        public final Map<String, String> clusterState;

        public MigrationCheckpoint(String checkpointId, String description, Instant timestamp,
                                   MigrationState migrationState, Map<String, String> clusterState)
        {
            this.checkpointId = checkpointId;
            this.description = description;
            this.timestamp = timestamp;
            this.migrationState = migrationState;
            this.clusterState = new HashMap<>(clusterState);
        }

        @Override
        public String toString()
        {
            return String.format("Checkpoint[%s]: %s at %s", checkpointId, description, timestamp);
        }
    }

    /**
     * Stores migration checkpoints.
     */
    public static class CheckpointStore
    {
        private final Map<String, MigrationCheckpoint> checkpoints = new ConcurrentHashMap<>();
        private final List<String> checkpointOrder = new CopyOnWriteArrayList<>();

        public void save(MigrationCheckpoint checkpoint)
        {
            checkpoints.put(checkpoint.checkpointId, checkpoint);
            checkpointOrder.add(checkpoint.checkpointId);
        }

        public MigrationCheckpoint getCheckpoint(String checkpointId)
        {
            return checkpoints.get(checkpointId);
        }

        public List<MigrationCheckpoint> getAllCheckpoints()
        {
            List<MigrationCheckpoint> result = new ArrayList<>();
            for (String id : checkpointOrder)
            {
                MigrationCheckpoint cp = checkpoints.get(id);
                if (cp != null)
                {
                    result.add(cp);
                }
            }
            return result;
        }

        public void clear()
        {
            checkpoints.clear();
            checkpointOrder.clear();
        }
    }

    /**
     * Metrics for migration tracking.
     */
    public static class MigrationMetrics
    {
        private Instant startTime;
        private Instant endTime;
        private final Map<String, Double> phaseProgress = new ConcurrentHashMap<>();
        private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();

        public void recordMigrationStart()
        {
            startTime = Instant.now();
        }

        public void recordProgress(String phase, double percentage)
        {
            phaseProgress.put(phase, percentage);
        }

        public void recordError(String phase)
        {
            errorCounts.merge(phase, 1, Integer::sum);
        }

        public void recordMigrationComplete()
        {
            endTime = Instant.now();
        }

        public long getDurationSeconds()
        {
            if (startTime == null)
                return 0;

            Instant end = endTime != null ? endTime : Instant.now();
            return end.getEpochSecond() - startTime.getEpochSecond();
        }

        public Map<String, Double> getPhaseProgress()
        {
            return new HashMap<>(phaseProgress);
        }

        public Map<String, Integer> getErrorCounts()
        {
            return new HashMap<>(errorCounts);
        }
    }
}
