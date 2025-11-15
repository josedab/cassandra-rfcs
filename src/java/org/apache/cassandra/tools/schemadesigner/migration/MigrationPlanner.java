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

import org.apache.cassandra.tools.schemadesigner.model.Schema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Plans and orchestrates schema migrations.
 */
public class MigrationPlanner
{
    private final SchemaComparator comparator;
    private final RiskAssessor riskAssessor;
    private final CodeGenerator codeGenerator;

    public MigrationPlanner()
    {
        this.comparator = new SchemaComparator();
        this.riskAssessor = new RiskAssessor();
        this.codeGenerator = new CodeGenerator();
    }

    /**
     * Plans a migration from current schema to target schema.
     *
     * @param current Current schema
     * @param target Target schema
     * @return Migration plan
     */
    public MigrationPlan planMigration(Schema current, Schema target)
    {
        MigrationPlan plan = new MigrationPlan();

        // Identify changes
        SchemaDiff diff = comparator.compare(current, target);
        plan.setDiff(diff);

        // Determine migration strategy
        MigrationStrategy strategy = determineStrategy(diff);
        plan.setStrategy(strategy);

        // Generate migration phases
        List<MigrationPhase> phases = generatePhases(diff, strategy);
        plan.setPhases(phases);

        // Assess risks
        RiskAssessment risks = riskAssessor.assess(diff, current);
        plan.setRisks(risks);

        // Generate rollback plan
        RollbackPlan rollback = generateRollback(phases, current);
        plan.setRollback(rollback);

        // Estimate duration and impact
        estimateImpact(plan, current);

        return plan;
    }

    private MigrationStrategy determineStrategy(SchemaDiff diff)
    {
        // Determine best strategy based on changes
        if (diff.hasBreakingChanges())
        {
            if (diff.canMigrateOnline())
            {
                return MigrationStrategy.ONLINE_MIGRATION;
            }
            else
            {
                return MigrationStrategy.BLUE_GREEN;
            }
        }
        else if (diff.requiresDataMigration())
        {
            return MigrationStrategy.ONLINE_MIGRATION;
        }
        else
        {
            return MigrationStrategy.ROLLING_UPGRADE;
        }
    }

    private List<MigrationPhase> generatePhases(SchemaDiff diff, MigrationStrategy strategy)
    {
        List<MigrationPhase> phases = new ArrayList<>();

        switch (strategy)
        {
            case ONLINE_MIGRATION:
                phases.add(createPhase("Setup", "Create new tables and enable dual writes", false));
                phases.add(createPhase("Backfill", "Copy existing data to new schema", false));
                phases.add(createPhase("Validate", "Verify data consistency", false));
                phases.add(createPhase("Cutover", "Switch reads to new schema", false));
                phases.add(createPhase("Cleanup", "Remove old schema", false));
                break;

            case BLUE_GREEN:
                phases.add(createPhase("Setup Blue", "Prepare new environment", true));
                phases.add(createPhase("Migrate Data", "Copy data to new environment", true));
                phases.add(createPhase("Validate", "Verify new environment", false));
                phases.add(createPhase("Switch", "Route traffic to new environment", true));
                phases.add(createPhase("Decommission", "Remove old environment", false));
                break;

            case ROLLING_UPGRADE:
                phases.add(createPhase("Prepare", "Add new columns/tables", false));
                phases.add(createPhase("Deploy", "Deploy application updates", false));
                phases.add(createPhase("Cleanup", "Remove deprecated schema", false));
                break;

            case BIG_BANG:
                phases.add(createPhase("Backup", "Backup current data", false));
                phases.add(createPhase("Downtime Start", "Stop application", true));
                phases.add(createPhase("Migrate", "Apply schema changes", true));
                phases.add(createPhase("Verify", "Verify migration", true));
                phases.add(createPhase("Downtime End", "Start application", true));
                break;
        }

        // Add specific migration steps to each phase
        addMigrationSteps(phases, diff);

        return phases;
    }

    private MigrationPhase createPhase(String name, String description, boolean requiresDowntime)
    {
        return new MigrationPhase(name, description, new ArrayList<>(), Duration.ofMinutes(10), requiresDowntime);
    }

    private void addMigrationSteps(List<MigrationPhase> phases, SchemaDiff diff)
    {
        // Add table creation steps
        for (String tableName : diff.getAddedTables())
        {
            phases.get(0).addStep(new MigrationStep(
                "CREATE_TABLE",
                "CREATE TABLE " + tableName,
                "Create new table: " + tableName
            ));
        }

        // Add table drop steps
        for (String tableName : diff.getRemovedTables())
        {
            MigrationPhase lastPhase = phases.get(phases.size() - 1);
            lastPhase.addStep(new MigrationStep(
                "DROP_TABLE",
                "DROP TABLE " + tableName,
                "Drop old table: " + tableName
            ));
        }

        // Add column modification steps
        for (SchemaDiff.ColumnChange change : diff.getColumnChanges())
        {
            phases.get(0).addStep(new MigrationStep(
                "ALTER_TABLE",
                change.toCQL(),
                "Modify column: " + change.getTableName() + "." + change.getColumnName()
            ));
        }
    }

    private RollbackPlan generateRollback(List<MigrationPhase> phases, Schema originalSchema)
    {
        RollbackPlan rollback = new RollbackPlan();

        // Generate inverse steps for each phase
        for (MigrationPhase phase : phases)
        {
            List<MigrationStep> inverseSteps = new ArrayList<>();

            for (MigrationStep step : phase.getSteps())
            {
                MigrationStep inverse = step.createInverse();
                if (inverse != null)
                {
                    inverseSteps.add(inverse);
                }
            }

            // Add in reverse order
            for (int i = inverseSteps.size() - 1; i >= 0; i--)
            {
                rollback.addStep(inverseSteps.get(i));
            }
        }

        rollback.setRestoreSchema(originalSchema);

        return rollback;
    }

    private void estimateImpact(MigrationPlan plan, Schema current)
    {
        Duration totalDuration = Duration.ZERO;
        boolean requiresDowntime = false;

        for (MigrationPhase phase : plan.getPhases())
        {
            totalDuration = totalDuration.plus(phase.getEstimatedDuration());
            if (phase.requiresDowntime())
            {
                requiresDowntime = true;
            }
        }

        plan.setEstimatedDuration(totalDuration);
        plan.setRequiresDowntime(requiresDowntime);
    }

    /**
     * Generates migration code in the specified language.
     *
     * @param plan Migration plan
     * @param language Target language
     * @return Generated code
     */
    public String generateCode(MigrationPlan plan, CodeGenerator.Language language)
    {
        return codeGenerator.generate(plan, language);
    }
}
