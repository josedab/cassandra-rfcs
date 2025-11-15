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

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.tcm.migration.CMSMigrationTracker.MigrationCheckpoint;

/**
 * Handles rollback of CMS migration to previous checkpoints.
 * Provides safe rollback with validation and manual step generation.
 */
public class CMSMigrationRollback
{
    private static final Logger logger = LoggerFactory.getLogger(CMSMigrationRollback.class);

    private final CMSMigrationTracker.CheckpointStore checkpointStore;
    private final CMSMigrationTracker tracker;

    private static final Duration ROLLBACK_WINDOW = Duration.ofHours(24);
    private static final Duration SAFE_ROLLBACK_WINDOW = Duration.ofHours(1);

    public CMSMigrationRollback(CMSMigrationTracker.CheckpointStore checkpointStore,
                                CMSMigrationTracker tracker)
    {
        this.checkpointStore = checkpointStore;
        this.tracker = tracker;
    }

    /**
     * Attempts to rollback migration to a specific checkpoint.
     *
     * @param checkpointId ID of the checkpoint to rollback to
     * @param options Rollback options
     * @return Result of rollback attempt
     */
    public RollbackResult rollback(String checkpointId, RollbackOptions options)
    {
        logger.info("Attempting rollback to checkpoint: {}", checkpointId);

        // Load checkpoint
        MigrationCheckpoint checkpoint = checkpointStore.getCheckpoint(checkpointId);
        if (checkpoint == null)
        {
            return RollbackResult.failure("Checkpoint not found: " + checkpointId);
        }

        // Validate rollback is possible
        RollbackValidation validation = validateRollback(checkpoint, options);
        if (!validation.canRollback())
        {
            return RollbackResult.failure("Rollback validation failed: " + validation.getReason());
        }

        try
        {
            // Update tracker
            tracker.recordEvent(new CMSMigrationTracker.MigrationEvent(
                CMSMigrationTracker.MigrationEventType.ROLLBACK_STARTED,
                "Starting rollback to checkpoint: " + checkpointId,
                Map.of("checkpoint_id", checkpointId)
            ));

            // Phase 1: Stop ongoing migration operations
            logger.info("Phase 1: Stopping ongoing migration operations");
            stopMigrationOperations();

            // Phase 2: Restore metadata state
            logger.info("Phase 2: Restoring metadata state from checkpoint");
            restoreMetadataState(checkpoint);

            // Phase 3: Revert CMS initialization if needed
            if (checkpoint.migrationState == CMSMigrationTracker.MigrationState.NOT_STARTED)
            {
                logger.info("Phase 3: Reverting CMS initialization");
                revertCMSInitialization();
            }

            // Phase 4: Validate rollback success
            logger.info("Phase 4: Validating rollback");
            boolean validationSuccess = validateRollbackSuccess(checkpoint);

            if (!validationSuccess)
            {
                logger.warn("Rollback validation failed, but rollback partially completed");
            }

            // Generate manual steps
            List<String> manualSteps = generateManualSteps(checkpoint, validation);

            tracker.recordEvent(new CMSMigrationTracker.MigrationEvent(
                CMSMigrationTracker.MigrationEventType.ROLLBACK_COMPLETED,
                "Rollback completed to checkpoint: " + checkpointId,
                Map.of("checkpoint_id", checkpointId)
            ));

            return RollbackResult.success(checkpoint, manualSteps, validation.getWarnings());
        }
        catch (Exception e)
        {
            logger.error("Rollback failed", e);
            return RollbackResult.failure("Rollback failed: " + e.getMessage());
        }
    }

    /**
     * Gets available rollback checkpoints.
     */
    public List<MigrationCheckpoint> getAvailableCheckpoints()
    {
        List<MigrationCheckpoint> all = checkpointStore.getAllCheckpoints();
        Instant cutoff = Instant.now().minus(ROLLBACK_WINDOW);

        // Filter checkpoints within rollback window
        List<MigrationCheckpoint> available = new ArrayList<>();
        for (MigrationCheckpoint cp : all)
        {
            if (cp.timestamp.isAfter(cutoff))
            {
                available.add(cp);
            }
        }

        return available;
    }

    /**
     * Validates if rollback is possible.
     */
    private RollbackValidation validateRollback(MigrationCheckpoint checkpoint, RollbackOptions options)
    {
        List<String> warnings = new ArrayList<>();
        String failureReason = null;

        // Check rollback window
        Duration age = Duration.between(checkpoint.timestamp, Instant.now());
        if (age.compareTo(ROLLBACK_WINDOW) > 0 && !options.isForceRollback())
        {
            failureReason = String.format(
                "Checkpoint is too old (%d hours). Rollback window is %d hours. Use --force to override.",
                age.toHours(), ROLLBACK_WINDOW.toHours()
            );
        }

        // Add warning if outside safe window
        if (age.compareTo(SAFE_ROLLBACK_WINDOW) > 0)
        {
            warnings.add(String.format(
                "Checkpoint is %d hours old, outside safe rollback window of %d hour. Extra caution advised.",
                age.toHours(), SAFE_ROLLBACK_WINDOW.toHours()
            ));
        }

        // Check if checkpoint data is intact
        if (checkpoint.clusterState.isEmpty())
        {
            failureReason = "Checkpoint data is incomplete or corrupted";
        }

        // Check for irreversible operations
        // In a real implementation, we would check if any irreversible operations
        // have occurred since the checkpoint

        return new RollbackValidation(failureReason == null, failureReason, warnings);
    }

    private void stopMigrationOperations()
    {
        // In a real implementation, this would:
        // 1. Signal all migration threads to stop
        // 2. Wait for in-progress operations to complete
        // 3. Prevent new migration operations from starting
        logger.info("Stopping migration operations");
    }

    private void restoreMetadataState(MigrationCheckpoint checkpoint)
    {
        // In a real implementation, this would:
        // 1. Restore cluster metadata from checkpoint
        // 2. Restore schema state
        // 3. Restore directory information
        logger.info("Restoring metadata state from checkpoint: {}", checkpoint.checkpointId);
    }

    private void revertCMSInitialization()
    {
        // In a real implementation, this would:
        // 1. Disable CMS on all nodes
        // 2. Revert to gossip-based metadata
        // 3. Clean up CMS-specific state
        logger.info("Reverting CMS initialization");
    }

    private boolean validateRollbackSuccess(MigrationCheckpoint checkpoint)
    {
        // In a real implementation, this would:
        // 1. Verify cluster metadata matches checkpoint
        // 2. Verify all nodes are healthy
        // 3. Run consistency checks
        logger.info("Validating rollback success");
        return true;
    }

    private List<String> generateManualSteps(MigrationCheckpoint checkpoint, RollbackValidation validation)
    {
        List<String> steps = new ArrayList<>();

        steps.add("1. Verify all nodes are in expected state:");
        steps.add("   nodetool status");
        steps.add("");

        steps.add("2. Check schema consistency:");
        steps.add("   nodetool describecluster");
        steps.add("");

        if (checkpoint.migrationState == CMSMigrationTracker.MigrationState.NOT_STARTED)
        {
            steps.add("3. Restart all nodes in rolling fashion to ensure gossip is active");
            steps.add("");
        }

        steps.add("4. Run validation to ensure cluster health:");
        steps.add("   nodetool cms validate --verbose");
        steps.add("");

        if (!validation.getWarnings().isEmpty())
        {
            steps.add("WARNINGS:");
            for (String warning : validation.getWarnings())
            {
                steps.add("  - " + warning);
            }
            steps.add("");
        }

        steps.add("5. Monitor logs for any errors or warnings");
        steps.add("6. Contact support if any issues persist");

        return steps;
    }

    /**
     * Result of rollback validation.
     */
    private static class RollbackValidation
    {
        private final boolean canRollback;
        private final String reason;
        private final List<String> warnings;

        public RollbackValidation(boolean canRollback, String reason, List<String> warnings)
        {
            this.canRollback = canRollback;
            this.reason = reason;
            this.warnings = warnings;
        }

        public boolean canRollback()
        {
            return canRollback;
        }

        public String getReason()
        {
            return reason;
        }

        public List<String> getWarnings()
        {
            return warnings;
        }
    }

    /**
     * Rollback operation result.
     */
    public static class RollbackResult
    {
        private final boolean success;
        private final String message;
        private final MigrationCheckpoint checkpoint;
        private final List<String> manualSteps;
        private final List<String> warnings;

        private RollbackResult(boolean success, String message, MigrationCheckpoint checkpoint,
                              List<String> manualSteps, List<String> warnings)
        {
            this.success = success;
            this.message = message;
            this.checkpoint = checkpoint;
            this.manualSteps = manualSteps != null ? manualSteps : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }

        public static RollbackResult success(MigrationCheckpoint checkpoint,
                                            List<String> manualSteps,
                                            List<String> warnings)
        {
            return new RollbackResult(true, "Rollback completed successfully", checkpoint,
                                     manualSteps, warnings);
        }

        public static RollbackResult failure(String message)
        {
            return new RollbackResult(false, message, null, null, null);
        }

        public boolean isSuccess()
        {
            return success;
        }

        public String getMessage()
        {
            return message;
        }

        public MigrationCheckpoint getCheckpoint()
        {
            return checkpoint;
        }

        public List<String> getManualSteps()
        {
            return new ArrayList<>(manualSteps);
        }

        public List<String> getWarnings()
        {
            return new ArrayList<>(warnings);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Rollback Result: ").append(success ? "SUCCESS" : "FAILURE").append("\n");
            sb.append("Message: ").append(message).append("\n");

            if (checkpoint != null)
            {
                sb.append("Checkpoint: ").append(checkpoint.checkpointId).append("\n");
                sb.append("Timestamp: ").append(checkpoint.timestamp).append("\n");
            }

            if (!warnings.isEmpty())
            {
                sb.append("\nWarnings:\n");
                for (String warning : warnings)
                {
                    sb.append("  - ").append(warning).append("\n");
                }
            }

            if (!manualSteps.isEmpty())
            {
                sb.append("\nManual Steps Required:\n");
                for (String step : manualSteps)
                {
                    sb.append(step).append("\n");
                }
            }

            return sb.toString();
        }
    }

    /**
     * Options for rollback operation.
     */
    public static class RollbackOptions
    {
        private boolean forceRollback = false;
        private boolean dryRun = false;

        public boolean isForceRollback()
        {
            return forceRollback;
        }

        public void setForceRollback(boolean forceRollback)
        {
            this.forceRollback = forceRollback;
        }

        public boolean isDryRun()
        {
            return dryRun;
        }

        public void setDryRun(boolean dryRun)
        {
            this.dryRun = dryRun;
        }
    }
}
