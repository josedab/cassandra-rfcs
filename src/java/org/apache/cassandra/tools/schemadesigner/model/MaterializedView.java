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

/**
 * Represents a materialized view.
 */
public class MaterializedView
{
    private final String name;
    private final String baseTable;
    private final String selectClause;
    private final String whereClause;
    private final String primaryKey;

    public MaterializedView(String name, String baseTable, String selectClause, String whereClause, String primaryKey)
    {
        this.name = name;
        this.baseTable = baseTable;
        this.selectClause = selectClause;
        this.whereClause = whereClause;
        this.primaryKey = primaryKey;
    }

    public String getName() { return name; }
    public String getBaseTable() { return baseTable; }

    public String toCQL()
    {
        return String.format(
            "CREATE MATERIALIZED VIEW IF NOT EXISTS %s AS\n" +
            "    SELECT %s FROM %s\n" +
            "    WHERE %s\n" +
            "    PRIMARY KEY %s;",
            name, selectClause, baseTable, whereClause, primaryKey
        );
    }
}
