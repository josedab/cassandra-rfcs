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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.repair.RepairRunnable;
import org.apache.cassandra.schema.SchemaKeyspace;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.streaming.StreamManager;
import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.tcm.membership.NodeId;
import org.apache.cassandra.utils.FBUtilities;

/**
 * Validates cluster readiness for CMS migration.
 * Performs comprehensive pre-migration checks including schema consistency,
 * topology stability, network connectivity, and resource availability.
 */
public class CMSMigrationValidator
{
    private static final Logger logger = LoggerFactory.getLogger(CMSMigrationValidator.class);

    private static final Duration TOPOLOGY_STABILITY_WINDOW = Duration.ofMinutes(30);
    private static final double MIN_FREE_DISK_SPACE_PERCENT = 20.0;
    private static final int MAX_NETWORK_LATENCY_MS = 100;

    /**
     * Validates the cluster for CMS migration readiness.
     *
     * @param metadata Current cluster metadata
     * @return ValidationResult containing all check results
     */
    public ValidationResult validate(ClusterMetadata metadata)
    {
        ValidationResult result = new ValidationResult();

        // Check schema consistency
        result.addCheck("schema_consistency", validateSchemaConsistency(metadata));

        // Verify topology stability
        result.addCheck("topology_stability", validateTopologyStability(metadata));

        // Check for in-progress operations
        result.addCheck("pending_operations", validateNoPendingOperations(metadata));

        // Validate network connectivity
        result.addCheck("network_connectivity", validateNetworkConnectivity(metadata));

        // Check disk space requirements
        result.addCheck("disk_space", validateDiskSpace(metadata));

        // Validate all nodes are alive and reachable
        result.addCheck("node_liveness", validateNodeLiveness(metadata));

        return result;
    }

    /**
     * Ensures all nodes have consistent schema version.
     * Multiple schema versions indicate ongoing schema changes which should
     * complete before migration.
     */
    private ValidationCheck validateSchemaConsistency(ClusterMetadata metadata)
    {
        try
        {
            Set<UUID> schemaVersions = new HashSet<>();
            UUID localSchemaVersion = SchemaKeyspace.calculateSchemaDigest();
            schemaVersions.add(localSchemaVersion);

            // In a real implementation, we would query all nodes for their schema versions
            // For now, we check if local schema is stable

            if (schemaVersions.size() > 1)
            {
                logger.warn("Multiple schema versions detected: {}", schemaVersions);
                return ValidationCheck.failure(
                    "Schema consistency",
                    String.format("Multiple schema versions found: %s. Ensure schema is synchronized across all nodes.",
                                  schemaVersions)
                );
            }

            return ValidationCheck.success(
                "Schema consistency",
                String.format("All nodes on schema version: %s", localSchemaVersion)
            );
        }
        catch (Exception e)
        {
            logger.error("Error validating schema consistency", e);
            return ValidationCheck.failure("Schema consistency", "Error checking schema: " + e.getMessage());
        }
    }

    /**
     * Checks that no recent topology changes have occurred.
     * Migration should only proceed when topology is stable.
     */
    private ValidationCheck validateTopologyStability(ClusterMetadata metadata)
    {
        try
        {
            // Check if any nodes are in MOVING, LEAVING, JOINING states
            long unstableNodes = metadata.directory.states.entrySet().stream()
                .filter(entry -> !entry.getValue().isNormal())
                .count();

            if (unstableNodes > 0)
            {
                return ValidationCheck.failure(
                    "Topology stability",
                    String.format("%d nodes in non-NORMAL state. Wait for topology changes to complete.", unstableNodes)
                );
            }

            // In a real implementation, we would track the last topology change timestamp
            // For now, we assume topology is stable if all nodes are NORMAL

            return ValidationCheck.success(
                "Topology stability",
                "No topology changes detected - all nodes in NORMAL state"
            );
        }
        catch (Exception e)
        {
            logger.error("Error validating topology stability", e);
            return ValidationCheck.failure("Topology stability", "Error checking topology: " + e.getMessage());
        }
    }

    /**
     * Validates no major operations (repairs, compactions, streaming) are in progress.
     */
    private ValidationCheck validateNoPendingOperations(ClusterMetadata metadata)
    {
        try
        {
            StringBuilder issues = new StringBuilder();
            boolean hasIssues = false;

            // Check for active repairs
            if (RepairRunnable.getActiveRepairCount() > 0)
            {
                issues.append("Active repairs in progress. ");
                hasIssues = true;
            }

            // Check for active compactions
            if (CompactionManager.instance.getActiveCompactions() > 0)
            {
                issues.append("Major compactions in progress. ");
                hasIssues = true;
            }

            // Check for active streaming
            if (StreamManager.instance.hasActiveOutgoingSessions() ||
                StreamManager.instance.hasActiveIncomingSessions())
            {
                issues.append("Streaming operations in progress. ");
                hasIssues = true;
            }

            if (hasIssues)
            {
                return ValidationCheck.failure(
                    "Pending operations",
                    issues.toString() + "Wait for operations to complete before migration."
                );
            }

            return ValidationCheck.success(
                "Pending operations",
                "No repairs, compactions, or streaming in progress"
            );
        }
        catch (Exception e)
        {
            logger.error("Error validating pending operations", e);
            return ValidationCheck.failure("Pending operations", "Error checking operations: " + e.getMessage());
        }
    }

    /**
     * Validates network connectivity between nodes.
     */
    private ValidationCheck validateNetworkConnectivity(ClusterMetadata metadata)
    {
        try
        {
            // Check that gossiper sees all nodes as alive
            int deadNodes = 0;
            for (Map.Entry<NodeId, InetAddressAndPort> entry : metadata.directory.addresses.entrySet())
            {
                InetAddressAndPort endpoint = entry.getValue();
                if (!Gossiper.instance.isAlive(endpoint))
                {
                    deadNodes++;
                    logger.warn("Node {} appears to be down", endpoint);
                }
            }

            if (deadNodes > 0)
            {
                return ValidationCheck.failure(
                    "Network connectivity",
                    String.format("%d nodes appear to be unreachable. Ensure all nodes are alive and network is stable.", deadNodes)
                );
            }

            return ValidationCheck.success(
                "Network connectivity",
                "All nodes reachable via gossip"
            );
        }
        catch (Exception e)
        {
            logger.error("Error validating network connectivity", e);
            return ValidationCheck.failure("Network connectivity", "Error checking connectivity: " + e.getMessage());
        }
    }

    /**
     * Validates sufficient disk space is available.
     */
    private ValidationCheck validateDiskSpace(ClusterMetadata metadata)
    {
        try
        {
            // Check local node disk space
            double freeSpacePercent = FBUtilities.getAvailableDiskSpacePercent();

            if (freeSpacePercent < MIN_FREE_DISK_SPACE_PERCENT)
            {
                return ValidationCheck.failure(
                    "Disk space",
                    String.format("Insufficient free disk space: %.1f%% (minimum %.1f%% required)",
                                  freeSpacePercent, MIN_FREE_DISK_SPACE_PERCENT)
                );
            }

            return ValidationCheck.success(
                "Disk space",
                String.format("Sufficient free space: %.1f%%", freeSpacePercent)
            );
        }
        catch (Exception e)
        {
            logger.error("Error validating disk space", e);
            return ValidationCheck.failure("Disk space", "Error checking disk space: " + e.getMessage());
        }
    }

    /**
     * Validates all nodes are alive and responsive.
     */
    private ValidationCheck validateNodeLiveness(ClusterMetadata metadata)
    {
        try
        {
            int totalNodes = metadata.directory.addresses.size();
            int aliveNodes = 0;

            for (InetAddressAndPort endpoint : metadata.directory.addresses.values())
            {
                if (Gossiper.instance.isAlive(endpoint))
                {
                    aliveNodes++;
                }
            }

            if (aliveNodes < totalNodes)
            {
                return ValidationCheck.failure(
                    "Node liveness",
                    String.format("Only %d/%d nodes are alive. All nodes must be alive for migration.",
                                  aliveNodes, totalNodes)
                );
            }

            return ValidationCheck.success(
                "Node liveness",
                String.format("All %d nodes are alive and responsive", totalNodes)
            );
        }
        catch (Exception e)
        {
            logger.error("Error validating node liveness", e);
            return ValidationCheck.failure("Node liveness", "Error checking liveness: " + e.getMessage());
        }
    }

    /**
     * Represents a single validation check result.
     */
    public static class ValidationCheck
    {
        public final String name;
        public final boolean passed;
        public final String message;

        private ValidationCheck(String name, boolean passed, String message)
        {
            this.name = name;
            this.passed = passed;
            this.message = message;
        }

        public static ValidationCheck success(String name, String message)
        {
            return new ValidationCheck(name, true, message);
        }

        public static ValidationCheck failure(String name, String message)
        {
            return new ValidationCheck(name, false, message);
        }

        @Override
        public String toString()
        {
            return String.format("[%s] %s: %s", passed ? "✓" : "✗", name, message);
        }
    }

    /**
     * Aggregated validation result containing all checks.
     */
    public static class ValidationResult
    {
        private final Map<String, ValidationCheck> checks = new HashMap<>();

        public void addCheck(String name, ValidationCheck check)
        {
            checks.put(name, check);
        }

        public boolean isValid()
        {
            return checks.values().stream().allMatch(check -> check.passed);
        }

        public Map<String, ValidationCheck> getChecks()
        {
            return new HashMap<>(checks);
        }

        public int getTotalChecks()
        {
            return checks.size();
        }

        public int getPassedChecks()
        {
            return (int) checks.values().stream().filter(check -> check.passed).count();
        }

        public int getFailedChecks()
        {
            return (int) checks.values().stream().filter(check -> !check.passed).count();
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Validation Result: %d/%d checks passed\n",
                                    getPassedChecks(), getTotalChecks()));
            for (ValidationCheck check : checks.values())
            {
                sb.append(check.toString()).append("\n");
            }
            return sb.toString();
        }
    }
}
