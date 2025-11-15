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
import org.apache.cassandra.tools.schemadesigner.model.Severity;
import org.apache.cassandra.tools.schemadesigner.model.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Validates data consistency during and after migrations.
 */
public class DataValidator
{
    private static final int DEFAULT_SAMPLE_SIZE = 1000;
    private final Random random;

    public DataValidator()
    {
        this.random = new Random();
    }

    /**
     * Validates a migration by comparing data between old and new schemas.
     *
     * @param oldSchema Old schema
     * @param newSchema New schema
     * @param plan Migration plan
     * @return Validation report
     */
    public ValidationReport validateMigration(Schema oldSchema, Schema newSchema, MigrationPlan plan)
    {
        ValidationReport report = new ValidationReport();

        System.out.println("Starting migration validation...");

        // Sample data validation
        for (Table oldTable : oldSchema.getTables())
        {
            Table newTable = newSchema.getTable(oldTable.getName());
            if (newTable != null)
            {
                validateTableData(oldTable, newTable, report);
            }
        }

        // Check for missing tables
        for (Table newTable : newSchema.getTables())
        {
            if (oldSchema.getTable(newTable.getName()) == null)
            {
                report.addIssue(new ValidationIssue(
                    Severity.INFO,
                    "New table detected: " + newTable.getName(),
                    "This table exists only in the new schema"
                ));
            }
        }

        System.out.println("Validation complete.");
        return report;
    }

    private void validateTableData(Table oldTable, Table newTable, ValidationReport report)
    {
        System.out.println("Validating table: " + oldTable.getName());

        // In a real implementation, this would:
        // 1. Sample random partitions from the old table
        // 2. Read the same partitions from the new table
        // 3. Compare the data
        // 4. Report any discrepancies

        // For now, we'll create a placeholder validation
        report.addIssue(new ValidationIssue(
            Severity.INFO,
            "Table validated: " + oldTable.getName(),
            "Placeholder validation - actual implementation would compare sampled data"
        ));
    }

    /**
     * Performs a quick consistency check.
     *
     * @param oldSchema Old schema
     * @param newSchema New schema
     * @return true if basic checks pass
     */
    public boolean quickConsistencyCheck(Schema oldSchema, Schema newSchema)
    {
        // Check table count
        int oldTableCount = oldSchema.getTables().size();
        int newTableCount = newSchema.getTables().size();

        if (Math.abs(oldTableCount - newTableCount) > oldTableCount * 0.2)
        {
            System.err.println("WARNING: Significant table count difference detected");
            return false;
        }

        // Check for common tables
        int commonTables = 0;
        for (Table oldTable : oldSchema.getTables())
        {
            if (newSchema.getTable(oldTable.getName()) != null)
            {
                commonTables++;
            }
        }

        if (commonTables < Math.min(oldTableCount, newTableCount) * 0.5)
        {
            System.err.println("WARNING: Less than 50% table overlap");
            return false;
        }

        return true;
    }

    /**
     * Validation report containing issues found during validation.
     */
    public static class ValidationReport
    {
        private final List<ValidationIssue> issues;

        public ValidationReport()
        {
            this.issues = new ArrayList<>();
        }

        public void addIssue(ValidationIssue issue)
        {
            issues.add(issue);
        }

        public List<ValidationIssue> getIssues()
        {
            return new ArrayList<>(issues);
        }

        public boolean hasErrors()
        {
            return issues.stream()
                .anyMatch(i -> i.getSeverity() == Severity.ERROR || i.getSeverity() == Severity.CRITICAL);
        }

        public boolean hasWarnings()
        {
            return issues.stream()
                .anyMatch(i -> i.getSeverity() == Severity.WARNING);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Validation Report ===\n\n");

            if (issues.isEmpty())
            {
                sb.append("No issues found.\n");
            }
            else
            {
                for (ValidationIssue issue : issues)
                {
                    sb.append(issue).append("\n");
                }
            }

            sb.append("\nSummary:\n");
            sb.append("  Total Issues: ").append(issues.size()).append("\n");
            sb.append("  Errors: ").append(countBySeverity(Severity.ERROR)).append("\n");
            sb.append("  Warnings: ").append(countBySeverity(Severity.WARNING)).append("\n");

            return sb.toString();
        }

        private long countBySeverity(Severity severity)
        {
            return issues.stream()
                .filter(i -> i.getSeverity() == severity)
                .count();
        }
    }

    /**
     * Represents a validation issue.
     */
    public static class ValidationIssue
    {
        private final Severity severity;
        private final String message;
        private final String details;

        public ValidationIssue(Severity severity, String message, String details)
        {
            this.severity = severity;
            this.message = message;
            this.details = details;
        }

        public Severity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }

        @Override
        public String toString()
        {
            return String.format("[%s] %s\n  %s", severity, message, details);
        }
    }
}
