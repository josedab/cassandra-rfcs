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
package org.apache.cassandra.tools.schemadesigner.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a Cassandra table.
 */
public class Table
{
    private final String name;
    private List<Column> partitionKey;
    private List<Column> clusteringColumns;
    private final Map<String, Column> regularColumns;
    private TableOptions options;
    private String keyspace;

    public Table(String name)
    {
        this.name = name;
        this.partitionKey = new ArrayList<>();
        this.clusteringColumns = new ArrayList<>();
        this.regularColumns = new LinkedHashMap<>();
        this.options = new TableOptions();
    }

    public void setPartitionKey(List<Column> partitionKey)
    {
        this.partitionKey = new ArrayList<>(partitionKey);
    }

    public void setClusteringColumns(List<Column> clusteringColumns)
    {
        this.clusteringColumns = new ArrayList<>(clusteringColumns);
    }

    public void addColumn(Column column)
    {
        regularColumns.put(column.getName(), column);
    }

    public void addColumns(Set<Column> columns)
    {
        columns.forEach(this::addColumn);
    }

    public void setOptions(TableOptions options)
    {
        this.options = options;
    }

    public void addBucketing(String bucketStrategy)
    {
        // Add time bucketing column to partition key
        // This is a simplified implementation
        Column bucketColumn = new Column("bucket_id", "INT");
        List<Column> newPartitionKey = new ArrayList<>();
        newPartitionKey.addAll(partitionKey);
        newPartitionKey.add(bucketColumn);
        this.partitionKey = newPartitionKey;
    }

    public String getName() { return name; }
    public List<Column> getPartitionKey() { return new ArrayList<>(partitionKey); }
    public List<Column> getClusteringColumns() { return new ArrayList<>(clusteringColumns); }
    public List<Column> getColumns() { return new ArrayList<>(regularColumns.values()); }
    public TableOptions getOptions() { return options; }
    public String getKeyspace() { return keyspace; }
    public void setKeyspace(String keyspace) { this.keyspace = keyspace; }

    public String toCQL()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE IF NOT EXISTS ");
        if (keyspace != null)
            sb.append(keyspace).append(".");
        sb.append(name).append(" (\n");

        // Partition key columns
        for (Column col : partitionKey)
        {
            sb.append("    ").append(col.getName()).append(" ").append(col.getType()).append(",\n");
        }

        // Clustering columns
        for (Column col : clusteringColumns)
        {
            sb.append("    ").append(col.getName()).append(" ").append(col.getType()).append(",\n");
        }

        // Regular columns
        for (Column col : regularColumns.values())
        {
            sb.append("    ").append(col.getName()).append(" ").append(col.getType()).append(",\n");
        }

        // Primary key
        sb.append("    PRIMARY KEY (");
        if (partitionKey.size() == 1)
        {
            sb.append(partitionKey.get(0).getName());
        }
        else
        {
            sb.append("(");
            sb.append(partitionKey.stream().map(Column::getName).collect(Collectors.joining(", ")));
            sb.append(")");
        }

        if (!clusteringColumns.isEmpty())
        {
            sb.append(", ");
            sb.append(clusteringColumns.stream().map(Column::getName).collect(Collectors.joining(", ")));
        }

        sb.append(")\n)");

        // Table options
        if (options != null)
        {
            String optionsStr = options.toCQL();
            if (!optionsStr.isEmpty())
            {
                sb.append("\n").append(optionsStr);
            }
        }

        sb.append(";");

        return sb.toString();
    }
}
