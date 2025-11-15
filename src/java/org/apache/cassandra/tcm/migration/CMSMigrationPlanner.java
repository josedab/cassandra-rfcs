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
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.tcm.membership.Location;
import org.apache.cassandra.tcm.membership.NodeId;

/**
 * Plans CMS migration execution with intelligent member selection and phased approach.
 * Generates comprehensive migration plans including timing estimates and rollback points.
 */
public class CMSMigrationPlanner
{
    private static final Logger logger = LoggerFactory.getLogger(CMSMigrationPlanner.class);

    private static final int DEFAULT_CMS_SIZE = 3;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_THROTTLE_MS = 10;
    private static final int MIN_CMS_MEMBERS = 3;
    private static final int RECOMMENDED_CMS_SIZE_SINGLE_DC = 3;
    private static final int RECOMMENDED_CMS_SIZE_MULTI_DC = 5;

    /**
     * Generates a comprehensive migration plan.
     *
     * @param metadata Current cluster metadata
     * @param options Migration options
     * @return Complete migration plan
     */
    public MigrationPlan generatePlan(ClusterMetadata metadata, MigrationOptions options)
    {
        MigrationPlan plan = new MigrationPlan();

        // Phase 1: Pre-migration checks
        plan.addPhase(createPreMigrationPhase(options));

        // Phase 2: CMS initialization
        plan.addPhase(createCMSInitializationPhase(metadata, options));

        // Phase 3: Metadata migration
        plan.addPhase(createMetadataMigrationPhase(metadata, options));

        // Phase 4: Validation and cleanup
        plan.addPhase(createPostMigrationPhase(options));

        return plan;
    }

    private MigrationPhase createPreMigrationPhase(MigrationOptions options)
    {
        MigrationPhase phase = new MigrationPhase("pre_migration", Duration.ofMinutes(10));
        phase.addStep("Run validation checks");
        if (options.isBackupEnabled())
        {
            phase.addStep("Create metadata backup");
        }
        phase.addStep("Notify operators");
        phase.addStep("Create initial checkpoint");
        return phase;
    }

    private MigrationPhase createCMSInitializationPhase(ClusterMetadata metadata, MigrationOptions options)
    {
        Set<InetAddressAndPort> members = selectCMSMembers(metadata, options);
        int quorumSize = calculateQuorumSize(members.size());

        MigrationPhase phase = new MigrationPhase("cms_initialization", Duration.ofMinutes(5));
        phase.addStep("Initialize CMS nodes: " + members);
        phase.addStep("Establish quorum (size: " + quorumSize + ")");
        phase.addStep("Verify CMS health");
        phase.addStep("Create checkpoint after initialization");
        phase.setMetadata("cms_members", members.toString());
        phase.setMetadata("quorum_size", String.valueOf(quorumSize));
        return phase;
    }

    private MigrationPhase createMetadataMigrationPhase(ClusterMetadata metadata, MigrationOptions options)
    {
        MigrationPhase phase = new MigrationPhase("metadata_migration", Duration.ofMinutes(25));
        phase.addStep("Migrate keyspace metadata");
        phase.addStep("Migrate table metadata");
        phase.addStep("Migrate index metadata");
        phase.addStep("Migrate materialized view metadata");
        phase.addStep("Migrate user-defined types");
        phase.addStep("Create checkpoint after migration");
        phase.setMetadata("batch_size", String.valueOf(options.getBatchSize()));
        phase.setMetadata("throttle_ms", String.valueOf(options.getThrottleMs()));
        return phase;
    }

    private MigrationPhase createPostMigrationPhase(MigrationOptions options)
    {
        MigrationPhase phase = new MigrationPhase("post_migration", Duration.ofMinutes(5));
        phase.addStep("Validate migrated metadata");
        phase.addStep("Verify CMS consistency");
        if (options.isCleanupEnabled())
        {
            phase.addStep("Cleanup old metadata structures");
        }
        phase.addStep("Update system tables");
        phase.addStep("Create final checkpoint");
        return phase;
    }

    /**
     * Selects optimal CMS members based on topology and options.
     */
    private Set<InetAddressAndPort> selectCMSMembers(ClusterMetadata metadata, MigrationOptions options)
    {
        if (options.hasExplicitMembers())
        {
            logger.info("Using explicitly specified CMS members: {}", options.getExplicitMembers());
            return options.getExplicitMembers();
        }

        CMSMemberSelector selector = new CMSMemberSelector();
        int targetSize = options.getCmsSize() > 0 ? options.getCmsSize() : getRecommendedCMSSize(metadata);
        return selector.selectOptimalMembers(metadata, targetSize);
    }

    private int getRecommendedCMSSize(ClusterMetadata metadata)
    {
        Set<String> datacenters = metadata.directory.locations.values().stream()
            .map(Location::datacenter)
            .collect(Collectors.toSet());

        return datacenters.size() > 1 ? RECOMMENDED_CMS_SIZE_MULTI_DC : RECOMMENDED_CMS_SIZE_SINGLE_DC;
    }

    private int calculateQuorumSize(int totalMembers)
    {
        return (totalMembers / 2) + 1;
    }

    /**
     * Selects optimal CMS members based on datacenter distribution, rack awareness,
     * and node stability.
     */
    public static class CMSMemberSelector
    {
        private static final Logger logger = LoggerFactory.getLogger(CMSMemberSelector.class);

        public Set<InetAddressAndPort> selectOptimalMembers(ClusterMetadata metadata, int targetSize)
        {
            if (targetSize < MIN_CMS_MEMBERS)
            {
                logger.warn("Requested CMS size {} is less than minimum {}, using minimum",
                            targetSize, MIN_CMS_MEMBERS);
                targetSize = MIN_CMS_MEMBERS;
            }

            List<NodeScore> scoredNodes = scoreNodes(metadata);
            scoredNodes.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());

            Set<InetAddressAndPort> selected = new HashSet<>();
            Set<String> selectedDatacenters = new HashSet<>();
            Set<String> selectedRacks = new HashSet<>();

            // First pass: Select one node from each datacenter
            for (NodeScore nodeScore : scoredNodes)
            {
                if (selected.size() >= targetSize)
                    break;

                String dc = nodeScore.datacenter;
                if (!selectedDatacenters.contains(dc))
                {
                    selected.add(nodeScore.address);
                    selectedDatacenters.add(dc);
                    selectedRacks.add(nodeScore.rack);
                }
            }

            // Second pass: Fill remaining slots with best-scored nodes from different racks
            for (NodeScore nodeScore : scoredNodes)
            {
                if (selected.size() >= targetSize)
                    break;

                if (!selected.contains(nodeScore.address))
                {
                    String rackKey = nodeScore.datacenter + ":" + nodeScore.rack;
                    if (!selectedRacks.contains(rackKey) || selectedRacks.size() < targetSize)
                    {
                        selected.add(nodeScore.address);
                        selectedRacks.add(rackKey);
                    }
                }
            }

            // Third pass: If we still need more nodes, add highest-scored remaining nodes
            for (NodeScore nodeScore : scoredNodes)
            {
                if (selected.size() >= targetSize)
                    break;

                if (!selected.contains(nodeScore.address))
                {
                    selected.add(nodeScore.address);
                }
            }

            logger.info("Selected {} CMS members from {} nodes", selected.size(), scoredNodes.size());
            return selected;
        }

        private List<NodeScore> scoreNodes(ClusterMetadata metadata)
        {
            List<NodeScore> scores = new ArrayList<>();

            for (Map.Entry<NodeId, InetAddressAndPort> entry : metadata.directory.addresses.entrySet())
            {
                NodeId nodeId = entry.getKey();
                InetAddressAndPort address = entry.getValue();
                Location location = metadata.directory.locations.get(nodeId);

                if (location == null)
                {
                    logger.warn("No location found for node {}", nodeId);
                    continue;
                }

                double score = calculateNodeScore(metadata, nodeId);
                scores.add(new NodeScore(address, location.datacenter, location.rack, score));
            }

            return scores;
        }

        private double calculateNodeScore(ClusterMetadata metadata, NodeId nodeId)
        {
            double score = 100.0;

            // Prefer nodes in NORMAL state
            if (!metadata.directory.states.get(nodeId).isNormal())
            {
                score -= 50.0;
            }

            // In a real implementation, we would consider:
            // - Node uptime/stability history
            // - Hardware capabilities (CPU, memory, disk)
            // - Current load
            // - Network latency to other nodes

            return score;
        }

        private static class NodeScore
        {
            final InetAddressAndPort address;
            final String datacenter;
            final String rack;
            final double score;

            NodeScore(InetAddressAndPort address, String datacenter, String rack, double score)
            {
                this.address = address;
                this.datacenter = datacenter;
                this.rack = rack;
                this.score = score;
            }

            double getScore()
            {
                return score;
            }

            @Override
            public String toString()
            {
                return String.format("%s (dc=%s, rack=%s, score=%.2f)",
                                     address, datacenter, rack, score);
            }
        }
    }

    /**
     * Migration plan containing all phases and steps.
     */
    public static class MigrationPlan
    {
        private final List<MigrationPhase> phases = new ArrayList<>();
        private final Map<String, String> metadata = new HashMap<>();

        public void addPhase(MigrationPhase phase)
        {
            phases.add(phase);
        }

        public List<MigrationPhase> getPhases()
        {
            return new ArrayList<>(phases);
        }

        public Duration getEstimatedDuration()
        {
            return phases.stream()
                .map(MigrationPhase::getEstimatedDuration)
                .reduce(Duration.ZERO, Duration::plus);
        }

        public void setMetadata(String key, String value)
        {
            metadata.put(key, value);
        }

        public Map<String, String> getMetadata()
        {
            return new HashMap<>(metadata);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Migration Plan\n");
            sb.append("==============\n");
            sb.append(String.format("Estimated Duration: %d minutes\n\n", getEstimatedDuration().toMinutes()));

            for (int i = 0; i < phases.size(); i++)
            {
                sb.append(String.format("Phase %d: %s\n", i + 1, phases.get(i)));
            }

            return sb.toString();
        }
    }

    /**
     * Individual migration phase with steps and timing.
     */
    public static class MigrationPhase
    {
        private final String name;
        private final Duration estimatedDuration;
        private final List<String> steps = new ArrayList<>();
        private final Map<String, String> metadata = new HashMap<>();

        public MigrationPhase(String name, Duration estimatedDuration)
        {
            this.name = name;
            this.estimatedDuration = estimatedDuration;
        }

        public void addStep(String step)
        {
            steps.add(step);
        }

        public void setMetadata(String key, String value)
        {
            metadata.put(key, value);
        }

        public String getName()
        {
            return name;
        }

        public Duration getEstimatedDuration()
        {
            return estimatedDuration;
        }

        public List<String> getSteps()
        {
            return new ArrayList<>(steps);
        }

        public Map<String, String> getMetadata()
        {
            return new HashMap<>(metadata);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s (estimated: %d min)\n", name, estimatedDuration.toMinutes()));
            for (String step : steps)
            {
                sb.append(String.format("  - %s\n", step));
            }
            return sb.toString();
        }
    }

    /**
     * Migration options for customizing the migration process.
     */
    public static class MigrationOptions
    {
        private Set<InetAddressAndPort> explicitMembers;
        private int cmsSize = DEFAULT_CMS_SIZE;
        private int batchSize = DEFAULT_BATCH_SIZE;
        private int throttleMs = DEFAULT_THROTTLE_MS;
        private boolean backupEnabled = true;
        private boolean cleanupEnabled = true;

        public boolean hasExplicitMembers()
        {
            return explicitMembers != null && !explicitMembers.isEmpty();
        }

        public Set<InetAddressAndPort> getExplicitMembers()
        {
            return explicitMembers;
        }

        public void setExplicitMembers(Set<InetAddressAndPort> explicitMembers)
        {
            this.explicitMembers = explicitMembers;
        }

        public int getCmsSize()
        {
            return cmsSize;
        }

        public void setCmsSize(int cmsSize)
        {
            this.cmsSize = cmsSize;
        }

        public int getBatchSize()
        {
            return batchSize;
        }

        public void setBatchSize(int batchSize)
        {
            this.batchSize = batchSize;
        }

        public int getThrottleMs()
        {
            return throttleMs;
        }

        public void setThrottleMs(int throttleMs)
        {
            this.throttleMs = throttleMs;
        }

        public boolean isBackupEnabled()
        {
            return backupEnabled;
        }

        public void setBackupEnabled(boolean backupEnabled)
        {
            this.backupEnabled = backupEnabled;
        }

        public boolean isCleanupEnabled()
        {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(boolean cleanupEnabled)
        {
            this.cleanupEnabled = cleanupEnabled;
        }
    }
}
