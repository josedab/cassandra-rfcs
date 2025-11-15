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
package org.apache.cassandra.tools.schemadesigner.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete migration plan.
 */
public class MigrationPlan
{
    private SchemaDiff diff;
    private MigrationStrategy strategy;
    private List<MigrationPhase> phases;
    private RiskAssessment risks;
    private RollbackPlan rollback;
    private Duration estimatedDuration;
    private boolean requiresDowntime;

    public MigrationPlan()
    {
        this.phases = new ArrayList<>();
    }

    // Getters and setters
    public SchemaDiff getDiff() { return diff; }
    public void setDiff(SchemaDiff diff) { this.diff = diff; }

    public MigrationStrategy getStrategy() { return strategy; }
    public void setStrategy(MigrationStrategy strategy) { this.strategy = strategy; }

    public List<MigrationPhase> getPhases() { return new ArrayList<>(phases); }
    public void setPhases(List<MigrationPhase> phases) { this.phases = new ArrayList<>(phases); }

    public RiskAssessment getRisks() { return risks; }
    public void setRisks(RiskAssessment risks) { this.risks = risks; }

    public RollbackPlan getRollback() { return rollback; }
    public void setRollback(RollbackPlan rollback) { this.rollback = rollback; }

    public Duration getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(Duration duration) { this.estimatedDuration = duration; }

    public boolean requiresDowntime() { return requiresDowntime; }
    public void setRequiresDowntime(boolean requires) { this.requiresDowntime = requires; }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Migration Plan ===\n\n");
        sb.append("Strategy: ").append(strategy).append("\n");
        sb.append("Estimated Duration: ").append(estimatedDuration).append("\n");
        sb.append("Requires Downtime: ").append(requiresDowntime ? "YES" : "NO").append("\n\n");

        sb.append("Phases:\n");
        for (int i = 0; i < phases.size(); i++)
        {
            MigrationPhase phase = phases.get(i);
            sb.append(String.format("%d. %s\n", i + 1, phase.getName()));
            sb.append("   Duration: ").append(phase.getEstimatedDuration()).append("\n");
            sb.append("   Downtime: ").append(phase.requiresDowntime() ? "YES" : "NO").append("\n");
            sb.append("   Steps: ").append(phase.getSteps().size()).append("\n");
        }

        if (risks != null && risks.hasRisks())
        {
            sb.append("\n").append(risks);
        }

        return sb.toString();
    }
}
