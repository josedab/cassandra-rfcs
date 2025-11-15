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

import java.util.ArrayList;
import java.util.List;

/**
 * Assesses risks associated with schema migrations.
 */
public class RiskAssessor
{
    public RiskAssessment assess(SchemaDiff diff, Schema current)
    {
        RiskAssessment assessment = new RiskAssessment();

        // Assess table removal risk
        if (!diff.getRemovedTables().isEmpty())
        {
            assessment.addRisk(new Risk(
                Severity.HIGH,
                "Data Loss",
                "Removing tables will permanently delete data: " + String.join(", ", diff.getRemovedTables()),
                "Ensure data is backed up or migrated before proceeding"
            ));
        }

        // Assess breaking changes risk
        if (diff.hasBreakingChanges())
        {
            assessment.addRisk(new Risk(
                Severity.HIGH,
                "Breaking Changes",
                "Schema contains breaking changes that may impact running applications",
                "Coordinate deployment with application updates"
            ));
        }

        // Assess column removal risk
        long removedColumns = diff.getColumnChanges().stream()
            .filter(c -> c.getChangeType() == SchemaDiff.ChangeType.REMOVE)
            .count();

        if (removedColumns > 0)
        {
            assessment.addRisk(new Risk(
                Severity.MEDIUM,
                "Column Removal",
                removedColumns + " columns will be removed",
                "Verify no queries reference removed columns"
            ));
        }

        // Assess new table risk
        if (!diff.getAddedTables().isEmpty())
        {
            assessment.addRisk(new Risk(
                Severity.LOW,
                "New Tables",
                diff.getAddedTables().size() + " new tables will be created",
                "Ensure adequate cluster capacity"
            ));
        }

        return assessment;
    }

    /**
     * Represents a migration risk.
     */
    public static class Risk
    {
        private final Severity severity;
        private final String category;
        private final String description;
        private final String mitigation;

        public Risk(Severity severity, String category, String description, String mitigation)
        {
            this.severity = severity;
            this.category = category;
            this.description = description;
            this.mitigation = mitigation;
        }

        public Severity getSeverity() { return severity; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public String getMitigation() { return mitigation; }

        @Override
        public String toString()
        {
            return String.format("[%s] %s: %s\n  Mitigation: %s",
                severity, category, description, mitigation);
        }
    }
}
