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
package org.apache.cassandra.tools.schemadesigner.generator;

import org.apache.cassandra.tools.schemadesigner.model.AccessPattern;
import org.apache.cassandra.tools.schemadesigner.model.Schema;
import org.apache.cassandra.tools.schemadesigner.model.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes access patterns and suggests denormalization opportunities.
 */
public class DenormalizationEngine
{
    private static final double DENORMALIZATION_THRESHOLD = 2.0;
    private static final double STORAGE_COST_WEIGHT = 0.1;

    /**
     * Optimizes schema by identifying denormalization opportunities.
     *
     * @param schema Current schema
     * @param patterns Access patterns
     */
    public void optimize(Schema schema, List<AccessPattern> patterns)
    {
        // Identify join patterns
        List<JoinPattern> joins = identifyJoins(patterns);

        for (JoinPattern join : joins)
        {
            // Calculate denormalization benefit
            double benefit = calculateBenefit(join);
            double cost = calculateCost(join);

            if (cost > 0 && benefit / cost > DENORMALIZATION_THRESHOLD)
            {
                // Create denormalized table
                Table denormalized = createDenormalizedTable(join);
                schema.addTable(denormalized);
            }
        }
    }

    private List<JoinPattern> identifyJoins(List<AccessPattern> patterns)
    {
        List<JoinPattern> joins = new ArrayList<>();

        // Simple join detection: look for patterns that access multiple entities
        // This is a simplified implementation
        for (int i = 0; i < patterns.size(); i++)
        {
            for (int j = i + 1; j < patterns.size(); j++)
            {
                AccessPattern p1 = patterns.get(i);
                AccessPattern p2 = patterns.get(j);

                // Check if patterns might represent a join
                if (hasCommonColumns(p1, p2))
                {
                    joins.add(new JoinPattern(p1, p2));
                }
            }
        }

        return joins;
    }

    private boolean hasCommonColumns(AccessPattern p1, AccessPattern p2)
    {
        return p1.getPartitionKeys().stream()
            .anyMatch(k -> p2.getRegularColumns().contains(k));
    }

    private double calculateBenefit(JoinPattern join)
    {
        // Estimate read performance improvement
        double readImprovement = join.getReadFrequency() * join.getJoinCost();

        // Factor in consistency requirements
        double consistencyFactor = join.requiresStrongConsistency() ? 0.5 : 1.0;

        return readImprovement * consistencyFactor;
    }

    private double calculateCost(JoinPattern join)
    {
        // Estimate write amplification
        double writeAmplification = join.getWriteFrequency() * join.getTableCount();

        // Estimate storage overhead
        double storageOverhead = join.getDataSize() * join.getRedundancyFactor();

        return writeAmplification + (storageOverhead * STORAGE_COST_WEIGHT);
    }

    private Table createDenormalizedTable(JoinPattern join)
    {
        String tableName = join.getPattern1().getEntity() + "_" + join.getPattern2().getEntity();
        return new Table(tableName);
    }

    /**
     * Represents a potential join pattern between access patterns.
     */
    public static class JoinPattern
    {
        private final AccessPattern pattern1;
        private final AccessPattern pattern2;

        public JoinPattern(AccessPattern pattern1, AccessPattern pattern2)
        {
            this.pattern1 = pattern1;
            this.pattern2 = pattern2;
        }

        public AccessPattern getPattern1() { return pattern1; }
        public AccessPattern getPattern2() { return pattern2; }

        public double getReadFrequency()
        {
            return pattern1.getEstimatedFrequency() + pattern2.getEstimatedFrequency();
        }

        public double getJoinCost()
        {
            return 1.5; // Simplified cost estimate
        }

        public boolean requiresStrongConsistency()
        {
            return false; // Default assumption
        }

        public double getWriteFrequency()
        {
            return getReadFrequency() * 0.2; // Assume 20% write rate
        }

        public int getTableCount()
        {
            return 2;
        }

        public long getDataSize()
        {
            return 1000; // Simplified estimate
        }

        public double getRedundancyFactor()
        {
            return 1.5; // Data duplication factor
        }
    }
}
