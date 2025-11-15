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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete database schema.
 */
public class Schema
{
    private String keyspace;
    private final Map<String, Table> tables;
    private final List<MaterializedView> views;
    private final List<Index> indexes;
    private Map<String, String> replicationConfig;

    public Schema()
    {
        this.tables = new HashMap<>();
        this.views = new ArrayList<>();
        this.indexes = new ArrayList<>();
        this.replicationConfig = new HashMap<>();
    }

    public Schema(String keyspace)
    {
        this();
        this.keyspace = keyspace;
    }

    public void addTable(Table table)
    {
        tables.put(table.getName(), table);
    }

    public void addView(MaterializedView view)
    {
        views.add(view);
    }

    public void addViews(List<MaterializedView> views)
    {
        this.views.addAll(views);
    }

    public void addIndex(Index index)
    {
        indexes.add(index);
    }

    public void addIndexes(List<Index> indexes)
    {
        this.indexes.addAll(indexes);
    }

    public Table getTable(String name)
    {
        return tables.get(name);
    }

    public List<Table> getTables()
    {
        return new ArrayList<>(tables.values());
    }

    public List<MaterializedView> getViews()
    {
        return new ArrayList<>(views);
    }

    public List<Index> getIndexes()
    {
        return new ArrayList<>(indexes);
    }

    public String getKeyspace() { return keyspace; }
    public void setKeyspace(String keyspace) { this.keyspace = keyspace; }

    public void setReplicationConfig(Map<String, String> config)
    {
        this.replicationConfig = new HashMap<>(config);
    }

    public Map<String, String> getReplicationConfig()
    {
        return new HashMap<>(replicationConfig);
    }

    public String toCQL()
    {
        StringBuilder sb = new StringBuilder();

        // Keyspace creation
        sb.append("CREATE KEYSPACE IF NOT EXISTS ").append(keyspace).append("\n");
        sb.append("WITH replication = {");
        sb.append("'class': '").append(replicationConfig.getOrDefault("class", "NetworkTopologyStrategy")).append("'");
        replicationConfig.entrySet().stream()
            .filter(e -> !e.getKey().equals("class"))
            .forEach(e -> sb.append(", '").append(e.getKey()).append("': ").append(e.getValue()));
        sb.append("};\n\n");

        // Tables
        for (Table table : tables.values())
        {
            sb.append(table.toCQL()).append("\n\n");
        }

        // Materialized views
        for (MaterializedView view : views)
        {
            sb.append(view.toCQL()).append("\n\n");
        }

        // Indexes
        for (Index index : indexes)
        {
            sb.append(index.toCQL()).append("\n\n");
        }

        return sb.toString();
    }
}
