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

import org.apache.cassandra.tools.schemadesigner.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates optimized Cassandra schemas from access patterns.
 */
public class SchemaGenerator
{
    private final DenormalizationEngine denormalizer;
    private final IndexOptimizer indexOptimizer;
    private final PartitionSizeCalculator sizeCalculator;

    private static final long MAX_PARTITION_SIZE = 100_000_000; // 100MB

    public SchemaGenerator()
    {
        this.denormalizer = new DenormalizationEngine();
        this.indexOptimizer = new IndexOptimizer();
        this.sizeCalculator = new PartitionSizeCalculator();
    }

    /**
     * Generates a complete schema from access patterns and workload profile.
     *
     * @param patterns Access patterns extracted from queries
     * @param workload Workload characteristics
     * @return Generated schema
     */
    public Schema generateSchema(List<AccessPattern> patterns, WorkloadProfile workload)
    {
        Schema schema = new Schema();

        // Group patterns by entity
        Map<String, List<AccessPattern>> entityPatterns = groupByEntity(patterns);

        for (Map.Entry<String, List<AccessPattern>> entry : entityPatterns.entrySet())
        {
            String entity = entry.getKey();
            List<AccessPattern> entityAccessPatterns = entry.getValue();

            // Generate base table
            Table baseTable = generateBaseTable(entity, entityAccessPatterns, workload);
            schema.addTable(baseTable);

            // Consider materialized views
            List<MaterializedView> views = generateMaterializedViews(baseTable, entityAccessPatterns);
            schema.addViews(views);

            // Consider secondary indexes
            List<Index> indexes = generateIndexes(baseTable, entityAccessPatterns);
            schema.addIndexes(indexes);
        }

        // Optimize for denormalization
        denormalizer.optimize(schema, patterns);

        return schema;
    }

    private Map<String, List<AccessPattern>> groupByEntity(List<AccessPattern> patterns)
    {
        return patterns.stream()
            .collect(Collectors.groupingBy(AccessPattern::getEntity));
    }

    private Table generateBaseTable(String entity, List<AccessPattern> patterns, WorkloadProfile workload)
    {
        Table table = new Table(entity);

        // Find primary access pattern (most frequent)
        AccessPattern primary = findPrimaryPattern(patterns);

        // Set partition key
        List<Column> partitionKey = determinePartitionKey(primary, workload);
        table.setPartitionKey(partitionKey);

        // Set clustering columns
        List<Column> clusteringColumns = determineClusteringColumns(primary);
        table.setClusteringColumns(clusteringColumns);

        // Add regular columns from all patterns
        Set<Column> columns = extractAllColumns(patterns);
        table.addColumns(columns);

        // Set table options
        TableOptions options = new TableOptions();
        SchemaRecommendation recommendation = workload.recommendSchema();
        options.setCompactionStrategy(recommendation.getCompactionStrategy());

        // Add compaction parameters
        for (Map.Entry<String, String> param : recommendation.getCompactionParameters().entrySet())
        {
            options.addCompactionParameter(param.getKey(), param.getValue());
        }

        options.setCompression("LZ4");
        options.setBloomFilterFpChance(0.01);

        // Calculate and validate partition size
        long estimatedSize = sizeCalculator.estimate(table, workload);
        if (estimatedSize > MAX_PARTITION_SIZE)
        {
            // Add time bucketing or other partition strategy
            table.addBucketing(determineBucketStrategy(estimatedSize));
        }

        table.setOptions(options);

        return table;
    }

    private AccessPattern findPrimaryPattern(List<AccessPattern> patterns)
    {
        return patterns.stream()
            .max(Comparator.comparingLong(AccessPattern::getEstimatedFrequency))
            .orElseThrow(() -> new IllegalArgumentException("No patterns provided"));
    }

    private List<Column> determinePartitionKey(AccessPattern pattern, WorkloadProfile workload)
    {
        List<Column> partitionKey = new ArrayList<>();

        // Use equality predicates as partition key
        for (String key : pattern.getPartitionKeys())
        {
            partitionKey.add(new Column(key, inferType(key)));
        }

        // If no partition keys found, use a default
        if (partitionKey.isEmpty())
        {
            partitionKey.add(new Column("id", "UUID"));
        }

        return partitionKey;
    }

    private List<Column> determineClusteringColumns(AccessPattern pattern)
    {
        List<Column> clusteringColumns = new ArrayList<>();

        for (String key : pattern.getClusteringKeys())
        {
            clusteringColumns.add(new Column(key, inferType(key)));
        }

        return clusteringColumns;
    }

    private Set<Column> extractAllColumns(List<AccessPattern> patterns)
    {
        Set<Column> columns = new HashSet<>();

        for (AccessPattern pattern : patterns)
        {
            for (String col : pattern.getRegularColumns())
            {
                if (!col.equals("*"))
                {
                    columns.add(new Column(col, inferType(col)));
                }
            }
        }

        return columns;
    }

    private String inferType(String columnName)
    {
        // Simple type inference based on column name
        String lower = columnName.toLowerCase();

        if (lower.contains("id"))
            return "UUID";
        if (lower.contains("time") || lower.contains("date") || lower.equals("created_at") || lower.equals("updated_at"))
            return "TIMESTAMP";
        if (lower.contains("count") || lower.contains("size"))
            return "INT";
        if (lower.contains("amount") || lower.contains("price"))
            return "DECIMAL";
        if (lower.contains("email") || lower.contains("name") || lower.contains("text"))
            return "TEXT";
        if (lower.contains("active") || lower.contains("enabled") || lower.contains("flag"))
            return "BOOLEAN";

        return "TEXT"; // Default
    }

    private String determineBucketStrategy(long estimatedSize)
    {
        // Determine bucketing strategy based on size
        if (estimatedSize > 1_000_000_000) // > 1GB
        {
            return "HOURLY";
        }
        else if (estimatedSize > 500_000_000) // > 500MB
        {
            return "DAILY";
        }
        else
        {
            return "WEEKLY";
        }
    }

    private List<MaterializedView> generateMaterializedViews(Table baseTable, List<AccessPattern> patterns)
    {
        List<MaterializedView> views = new ArrayList<>();

        // Generate views for secondary access patterns
        for (int i = 1; i < Math.min(patterns.size(), 3); i++)
        {
            AccessPattern pattern = patterns.get(i);

            // Only create view if access pattern is significantly different
            if (!pattern.getPartitionKeys().equals(baseTable.getPartitionKey().stream()
                .map(Column::getName).collect(Collectors.toList())))
            {
                String viewName = baseTable.getName() + "_by_" +
                    String.join("_", pattern.getPartitionKeys());

                String selectClause = "*";
                String whereClause = pattern.getPartitionKeys().stream()
                    .map(k -> k + " IS NOT NULL")
                    .collect(Collectors.joining(" AND "));

                String primaryKey = "(" + String.join(", ", pattern.getPartitionKeys()) + ")";

                MaterializedView view = new MaterializedView(
                    viewName,
                    baseTable.getName(),
                    selectClause,
                    whereClause,
                    primaryKey
                );

                views.add(view);
            }
        }

        return views;
    }

    private List<Index> generateIndexes(Table baseTable, List<AccessPattern> patterns)
    {
        return indexOptimizer.generateIndexes(baseTable, patterns);
    }
}
