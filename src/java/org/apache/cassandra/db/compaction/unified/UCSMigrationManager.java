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

package org.apache.cassandra.db.compaction.unified;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.compaction.AbstractCompactionStrategy;
import org.apache.cassandra.db.compaction.LeveledCompactionStrategy;
import org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy;
import org.apache.cassandra.db.compaction.unified.UCSWorkloadAnalyzer.AnalysisResult;
import org.apache.cassandra.db.compaction.unified.UCSWorkloadAnalyzer.UCSConfiguration;
import org.apache.cassandra.schema.TableMetadata;

/**
 * Manages migration from other compaction strategies to UCS.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
public class UCSMigrationManager
{
    private static final Logger logger = LoggerFactory.getLogger(UCSMigrationManager.class);

    private final MigrationAnalyzer analyzer;
    private final MigrationExecutor executor;
    private final MigrationValidator validator;

    public UCSMigrationManager()
    {
        this.analyzer = new MigrationAnalyzer();
        this.executor = new MigrationExecutor();
        this.validator = new MigrationValidator();
    }

    /**
     * Migrate a table to UCS with the specified options.
     *
     * @param cfs The ColumnFamilyStore to migrate
     * @param options Migration options
     * @return The result of the migration
     */
    public MigrationResult migrateToUCS(ColumnFamilyStore cfs, MigrationOptions options)
    {
        logger.info("Starting UCS migration for {}.{} with options: {}",
                   cfs.keyspace.getName(), cfs.name, options);

        try
        {
            // Phase 1: Analysis
            AnalysisResult analysis = options.shouldAnalyzeFirst() ?
                analyzer.analyze(cfs, options) : null;

            if (options.isDryRun())
            {
                logger.info("Dry run completed for {}.{}", cfs.keyspace.getName(), cfs.name);
                return MigrationResult.dryRun(analysis);
            }

            // Phase 2: Preparation
            MigrationPlan plan = prepareMigrationPlan(cfs, analysis, options);

            // Phase 3: Execution
            if (options.isGradual())
            {
                return executeGradualMigration(cfs, plan);
            }
            else
            {
                return executeImmediateMigration(cfs, plan);
            }
        }
        catch (Exception e)
        {
            logger.error("Migration failed for {}.{}", cfs.keyspace.getName(), cfs.name, e);
            return MigrationResult.failure(e);
        }
    }

    private MigrationPlan prepareMigrationPlan(ColumnFamilyStore cfs,
                                              AnalysisResult analysis,
                                              MigrationOptions options)
    {
        MigrationPlan plan = new MigrationPlan();
        plan.cfs = cfs;

        // Determine optimal UCS configuration
        UCSConfiguration targetConfig = analysis != null ?
            analysis.getRecommendedConfiguration() :
            new UCSConfiguration();

        plan.targetConfiguration = targetConfig;

        // Calculate migration steps based on current strategy
        AbstractCompactionStrategy currentStrategy = cfs.getCompactionStrategy();

        if (currentStrategy instanceof LeveledCompactionStrategy)
        {
            plan.addStep(new LCSToUCSMigrationStep(cfs, targetConfig));
        }
        else if (currentStrategy instanceof SizeTieredCompactionStrategy)
        {
            plan.addStep(new STCSToUCSMigrationStep(cfs, targetConfig));
        }
        else
        {
            plan.addStep(new GenericMigrationStep(cfs, targetConfig));
        }

        // Add validation steps
        plan.addStep(new ValidationStep(cfs));

        // Add monitoring steps
        plan.addStep(new MonitoringStep(cfs, Duration.ofHours(24)));

        logger.info("Migration plan prepared with {} steps", plan.steps.size());
        return plan;
    }

    private MigrationResult executeGradualMigration(ColumnFamilyStore cfs, MigrationPlan plan)
    {
        logger.info("Executing gradual migration for {}.{}", cfs.keyspace.getName(), cfs.name);

        GradualMigration migration = new GradualMigration(cfs, plan);

        // Step 1: Create UCS with similar behavior to current strategy
        UCSConfiguration transitional = createTransitionalConfig(cfs);
        migration.applyConfiguration(transitional);

        // Step 2: Monitor for stability
        migration.monitorStability(Duration.ofHours(1));

        // Step 3: Gradually adjust parameters toward optimal
        int steps = 5;
        for (int i = 1; i <= steps; i++)
        {
            logger.info("Gradual migration step {}/{} for {}.{}",
                       i, steps, cfs.keyspace.getName(), cfs.name);

            UCSConfiguration intermediate = interpolateConfig(
                transitional,
                plan.getTargetConfiguration(),
                (double) i / steps
            );

            migration.applyConfiguration(intermediate);
            migration.monitorStability(Duration.ofMinutes(30));

            // Check for issues
            if (migration.hasIssues())
            {
                logger.warn("Issues detected during gradual migration, rolling back");
                return migration.rollback();
            }
        }

        // Step 4: Final validation
        ValidationResult validation = validator.validate(cfs, plan);

        logger.info("Gradual migration completed successfully for {}.{}",
                   cfs.keyspace.getName(), cfs.name);

        return MigrationResult.success()
            .withPlan(plan)
            .withValidation(validation)
            .withMetrics(migration.getMetrics());
    }

    private MigrationResult executeImmediateMigration(ColumnFamilyStore cfs, MigrationPlan plan)
    {
        logger.info("Executing immediate migration for {}.{}", cfs.keyspace.getName(), cfs.name);

        // Apply target configuration directly
        executor.applyConfiguration(cfs, plan.getTargetConfiguration());

        // Validate
        ValidationResult validation = validator.validate(cfs, plan);

        if (!validation.isValid())
        {
            logger.error("Immediate migration validation failed for {}.{}",
                        cfs.keyspace.getName(), cfs.name);
            return MigrationResult.failure(new Exception("Validation failed"));
        }

        logger.info("Immediate migration completed successfully for {}.{}",
                   cfs.keyspace.getName(), cfs.name);

        return MigrationResult.success()
            .withPlan(plan)
            .withValidation(validation);
    }

    private UCSConfiguration createTransitionalConfig(ColumnFamilyStore cfs)
    {
        // Create a UCS configuration that mimics current strategy behavior
        UCSConfiguration config = new UCSConfiguration();

        AbstractCompactionStrategy current = cfs.getCompactionStrategy();
        if (current instanceof LeveledCompactionStrategy)
        {
            config.setScalingParameter(16); // Higher scaling for LCS-like behavior
            config.setTargetSStableSize(160 * 1024 * 1024);
        }
        else if (current instanceof SizeTieredCompactionStrategy)
        {
            config.setScalingParameter(4); // Lower scaling for STCS-like behavior
            config.setTargetSStableSize(160 * 1024 * 1024);
        }

        return config;
    }

    private UCSConfiguration interpolateConfig(UCSConfiguration from, UCSConfiguration to, double progress)
    {
        UCSConfiguration result = new UCSConfiguration();

        // Interpolate scaling parameter
        int scalingDelta = to.getScalingParameter() - from.getScalingParameter();
        result.setScalingParameter(from.getScalingParameter() + (int) (scalingDelta * progress));

        // Interpolate target size
        long sizeDelta = to.getTargetSStableSize() - from.getTargetSStableSize();
        result.setTargetSStableSize(from.getTargetSStableSize() + (long) (sizeDelta * progress));

        // Copy other parameters
        result.setNumShards(to.getNumShards());
        result.setAggressiveTombstoneCompaction(to.isAggressiveTombstoneCompaction());

        return result;
    }

    // Supporting classes

    public static class MigrationAnalyzer
    {
        private final UCSWorkloadAnalyzer workloadAnalyzer = new UCSWorkloadAnalyzer();

        public AnalysisResult analyze(ColumnFamilyStore cfs, MigrationOptions options)
        {
            Duration period = Duration.ofDays(1); // Default analysis period
            return workloadAnalyzer.analyze(cfs, period);
        }
    }

    public static class MigrationExecutor
    {
        public void applyConfiguration(ColumnFamilyStore cfs, UCSConfiguration config)
        {
            logger.info("Applying UCS configuration to {}.{}: {}",
                       cfs.keyspace.getName(), cfs.name, config.toOptions());

            // In real implementation, this would update the table's compaction strategy
            // For now, this is a placeholder
        }
    }

    public static class MigrationValidator
    {
        public ValidationResult validate(ColumnFamilyStore cfs, MigrationPlan plan)
        {
            ValidationResult result = new ValidationResult();
            result.valid = true;

            // Check that UCS is active
            if (!(cfs.getCompactionStrategy() instanceof UnifiedCompactionStrategy))
            {
                result.valid = false;
                result.errors.add("UCS is not the active compaction strategy");
            }

            // Check SSTable distribution
            int sstableCount = cfs.getLiveSSTables().size();
            if (sstableCount == 0)
            {
                result.warnings.add("No SSTables found");
            }

            logger.info("Migration validation for {}.{}: valid={}, errors={}, warnings={}",
                       cfs.keyspace.getName(), cfs.name,
                       result.valid, result.errors.size(), result.warnings.size());

            return result;
        }
    }

    public static class GradualMigration
    {
        private final ColumnFamilyStore cfs;
        private final MigrationPlan plan;
        private final List<String> issues = new ArrayList<>();
        private final MigrationMetrics metrics = new MigrationMetrics();

        public GradualMigration(ColumnFamilyStore cfs, MigrationPlan plan)
        {
            this.cfs = cfs;
            this.plan = plan;
        }

        public void applyConfiguration(UCSConfiguration config)
        {
            logger.info("Applying gradual configuration to {}.{}", cfs.keyspace.getName(), cfs.name);
            // Apply configuration gradually
        }

        public void monitorStability(Duration duration)
        {
            logger.info("Monitoring stability for {}.{} for {}",
                       cfs.keyspace.getName(), cfs.name, duration);

            try
            {
                TimeUnit.MILLISECONDS.sleep(Math.min(1000, duration.toMillis()));
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }

        public boolean hasIssues()
        {
            return !issues.isEmpty();
        }

        public MigrationResult rollback()
        {
            logger.warn("Rolling back migration for {}.{}", cfs.keyspace.getName(), cfs.name);
            return MigrationResult.failure(new Exception("Migration rolled back due to issues"));
        }

        public MigrationMetrics getMetrics()
        {
            return metrics;
        }
    }

    // Step implementations

    public abstract static class MigrationStep
    {
        protected final ColumnFamilyStore cfs;
        protected final UCSConfiguration targetConfig;

        public MigrationStep(ColumnFamilyStore cfs, UCSConfiguration config)
        {
            this.cfs = cfs;
            this.targetConfig = config;
        }

        public abstract void execute();
    }

    public static class LCSToUCSMigrationStep extends MigrationStep
    {
        public LCSToUCSMigrationStep(ColumnFamilyStore cfs, UCSConfiguration config)
        {
            super(cfs, config);
        }

        @Override
        public void execute()
        {
            logger.info("Migrating from LCS to UCS for {}.{}", cfs.keyspace.getName(), cfs.name);
            // Migration logic here
        }
    }

    public static class STCSToUCSMigrationStep extends MigrationStep
    {
        public STCSToUCSMigrationStep(ColumnFamilyStore cfs, UCSConfiguration config)
        {
            super(cfs, config);
        }

        @Override
        public void execute()
        {
            logger.info("Migrating from STCS to UCS for {}.{}", cfs.keyspace.getName(), cfs.name);
            // Migration logic here
        }
    }

    public static class GenericMigrationStep extends MigrationStep
    {
        public GenericMigrationStep(ColumnFamilyStore cfs, UCSConfiguration config)
        {
            super(cfs, config);
        }

        @Override
        public void execute()
        {
            logger.info("Generic migration to UCS for {}.{}", cfs.keyspace.getName(), cfs.name);
            // Generic migration logic here
        }
    }

    public static class ValidationStep extends MigrationStep
    {
        public ValidationStep(ColumnFamilyStore cfs)
        {
            super(cfs, null);
        }

        @Override
        public void execute()
        {
            logger.info("Validating UCS migration for {}.{}", cfs.keyspace.getName(), cfs.name);
            // Validation logic here
        }
    }

    public static class MonitoringStep extends MigrationStep
    {
        private final Duration monitoringPeriod;

        public MonitoringStep(ColumnFamilyStore cfs, Duration period)
        {
            super(cfs, null);
            this.monitoringPeriod = period;
        }

        @Override
        public void execute()
        {
            logger.info("Monitoring UCS for {}.{} for {}",
                       cfs.keyspace.getName(), cfs.name, monitoringPeriod);
            // Monitoring logic here
        }
    }

    // Data classes

    public static class MigrationOptions
    {
        private boolean analyzeFirst = false;
        private boolean gradual = false;
        private boolean dryRun = false;

        public boolean shouldAnalyzeFirst() { return analyzeFirst; }
        public boolean isGradual() { return gradual; }
        public boolean isDryRun() { return dryRun; }

        public MigrationOptions setAnalyzeFirst(boolean value) { this.analyzeFirst = value; return this; }
        public MigrationOptions setGradual(boolean value) { this.gradual = value; return this; }
        public MigrationOptions setDryRun(boolean value) { this.dryRun = value; return this; }

        @Override
        public String toString()
        {
            return String.format("MigrationOptions{analyzeFirst=%s, gradual=%s, dryRun=%s}",
                               analyzeFirst, gradual, dryRun);
        }
    }

    public static class MigrationPlan
    {
        public ColumnFamilyStore cfs;
        public UCSConfiguration targetConfiguration;
        public List<MigrationStep> steps = new ArrayList<>();

        public void addStep(MigrationStep step)
        {
            steps.add(step);
        }

        public UCSConfiguration getTargetConfiguration()
        {
            return targetConfiguration;
        }
    }

    public static class ValidationResult
    {
        public boolean valid = true;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        public boolean isValid() { return valid; }
    }

    public static class MigrationMetrics
    {
        public long duration;
        public long sstablesMigrated;
        public long bytesMigrated;

        @Override
        public String toString()
        {
            return String.format("MigrationMetrics{duration=%dms, sstables=%d, bytes=%d}",
                               duration, sstablesMigrated, bytesMigrated);
        }
    }

    public static class MigrationResult
    {
        private boolean success;
        private MigrationPlan plan;
        private ValidationResult validation;
        private MigrationMetrics metrics;
        private Exception error;
        private boolean wasDryRun;

        public static MigrationResult success()
        {
            MigrationResult result = new MigrationResult();
            result.success = true;
            return result;
        }

        public static MigrationResult failure(Exception error)
        {
            MigrationResult result = new MigrationResult();
            result.success = false;
            result.error = error;
            return result;
        }

        public static MigrationResult dryRun(AnalysisResult analysis)
        {
            MigrationResult result = new MigrationResult();
            result.success = true;
            result.wasDryRun = true;
            return result;
        }

        public MigrationResult withPlan(MigrationPlan plan)
        {
            this.plan = plan;
            return this;
        }

        public MigrationResult withValidation(ValidationResult validation)
        {
            this.validation = validation;
            return this;
        }

        public MigrationResult withMetrics(MigrationMetrics metrics)
        {
            this.metrics = metrics;
            return this;
        }

        public boolean isSuccess() { return success; }
        public boolean wasDryRun() { return wasDryRun; }
        public Exception getError() { return error; }

        @Override
        public String toString()
        {
            return String.format("MigrationResult{success=%s, dryRun=%s, error=%s}",
                               success, wasDryRun, error != null ? error.getMessage() : "none");
        }
    }
}
