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
package org.apache.cassandra.tools.schemadesigner.rules;

import org.apache.cassandra.tools.schemadesigner.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Defines anti-pattern detection rules.
 */
public class AntiPatternRule
{
    private final String name;
    private final BiPredicate<Schema, List<AccessPattern>> detector;
    private final String description;
    private final Severity severity;
    private final List<String> recommendations;

    public AntiPatternRule(String name,
                          BiPredicate<Schema, List<AccessPattern>> detector,
                          String description,
                          Severity severity,
                          List<String> recommendations)
    {
        this.name = name;
        this.detector = detector;
        this.description = description;
        this.severity = severity;
        this.recommendations = recommendations;
    }

    public boolean matches(Schema schema, List<AccessPattern> patterns)
    {
        return detector.test(schema, patterns);
    }

    public AntiPattern createAntiPattern()
    {
        return new AntiPattern(name, description, severity, recommendations);
    }

    // Predefined anti-pattern rules

    public static final AntiPatternRule LARGE_PARTITION = new AntiPatternRule(
        "Large Partition",
        (schema, patterns) -> {
            // Estimate partition sizes
            return schema.getTables().stream()
                .anyMatch(table -> estimateMaxPartitionSize(table) > 100_000_000);
        },
        "Partition size exceeds recommended 100MB limit",
        Severity.HIGH,
        Arrays.asList(
            "Add time bucketing to partition key",
            "Implement partition splitting strategy",
            "Archive old data to separate table"
        )
    );

    public static final AntiPatternRule HOT_PARTITION = new AntiPatternRule(
        "Hot Partition",
        (schema, patterns) -> {
            // Detect uneven partition access
            if (patterns.isEmpty())
                return false;

            long totalFrequency = patterns.stream()
                .mapToLong(AccessPattern::getEstimatedFrequency)
                .sum();

            long maxFrequency = patterns.stream()
                .mapToLong(AccessPattern::getEstimatedFrequency)
                .max()
                .orElse(0);

            // If one pattern accounts for >40% of traffic, flag as hot partition
            return totalFrequency > 0 && (double) maxFrequency / totalFrequency > 0.4;
        },
        "Uneven partition access causing hotspots",
        Severity.HIGH,
        Arrays.asList(
            "Add partition key components for better distribution",
            "Implement partition bucketing",
            "Consider random partition key suffix"
        )
    );

    public static final AntiPatternRule UNBOUNDED_COLLECTION = new AntiPatternRule(
        "Unbounded Collection",
        (schema, patterns) -> {
            return schema.getTables().stream()
                .flatMap(t -> t.getColumns().stream())
                .anyMatch(Column::isCollection);
        },
        "Collection columns can grow unbounded",
        Severity.MEDIUM,
        Arrays.asList(
            "Set collection size limits in application logic",
            "Use separate table for one-to-many relationships",
            "Implement data retention policy"
        )
    );

    public static final AntiPatternRule ALLOW_FILTERING_OVERUSE = new AntiPatternRule(
        "ALLOW FILTERING Overuse",
        (schema, patterns) -> {
            long filteringQueries = patterns.stream()
                .filter(AccessPattern::isAllowFiltering)
                .count();

            return filteringQueries > patterns.size() * 0.2; // >20% use ALLOW FILTERING
        },
        "Excessive use of ALLOW FILTERING indicates poor schema design",
        Severity.HIGH,
        Arrays.asList(
            "Create materialized views for common query patterns",
            "Add secondary indexes for frequently queried columns",
            "Redesign partition keys to support common queries"
        )
    );

    private static long estimateMaxPartitionSize(Table table)
    {
        // Simple estimation
        int estimatedRowsPerPartition = table.getClusteringColumns().isEmpty() ? 1 : 1000;
        int avgRowSize = table.getColumns().size() * 100; // Rough estimate
        return (long) estimatedRowsPerPartition * avgRowSize;
    }
}
