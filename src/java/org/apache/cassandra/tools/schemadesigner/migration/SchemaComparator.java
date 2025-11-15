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

import org.apache.cassandra.tools.schemadesigner.model.Column;
import org.apache.cassandra.tools.schemadesigner.model.Schema;
import org.apache.cassandra.tools.schemadesigner.model.Table;

import java.util.*;

/**
 * Compares two schemas and identifies differences.
 */
public class SchemaComparator
{
    public SchemaDiff compare(Schema current, Schema target)
    {
        SchemaDiff diff = new SchemaDiff();

        // Compare tables
        Set<String> currentTables = getTableNames(current);
        Set<String> targetTables = getTableNames(target);

        // Added tables
        for (String tableName : targetTables)
        {
            if (!currentTables.contains(tableName))
            {
                diff.addTableAdded(tableName);
            }
        }

        // Removed tables
        for (String tableName : currentTables)
        {
            if (!targetTables.contains(tableName))
            {
                diff.addTableRemoved(tableName);
            }
        }

        // Modified tables
        for (String tableName : currentTables)
        {
            if (targetTables.contains(tableName))
            {
                Table currentTable = current.getTable(tableName);
                Table targetTable = target.getTable(tableName);
                compareTables(currentTable, targetTable, diff);
            }
        }

        return diff;
    }

    private Set<String> getTableNames(Schema schema)
    {
        Set<String> names = new HashSet<>();
        for (Table table : schema.getTables())
        {
            names.add(table.getName());
        }
        return names;
    }

    private void compareTables(Table current, Table target, SchemaDiff diff)
    {
        String tableName = current.getName();

        // Compare columns
        Set<String> currentCols = getColumnNames(current);
        Set<String> targetCols = getColumnNames(target);

        // Added columns
        for (String colName : targetCols)
        {
            if (!currentCols.contains(colName))
            {
                diff.addColumnChange(new SchemaDiff.ColumnChange(
                    tableName, colName, SchemaDiff.ChangeType.ADD, null, getColumnType(target, colName)
                ));
            }
        }

        // Removed columns
        for (String colName : currentCols)
        {
            if (!targetCols.contains(colName))
            {
                diff.addColumnChange(new SchemaDiff.ColumnChange(
                    tableName, colName, SchemaDiff.ChangeType.REMOVE, getColumnType(current, colName), null
                ));
            }
        }

        // Check for primary key changes (breaking change)
        if (!current.getPartitionKey().equals(target.getPartitionKey()) ||
            !current.getClusteringColumns().equals(target.getClusteringColumns()))
        {
            diff.setHasBreakingChanges(true);
            diff.addBreakingChange("Primary key change in table: " + tableName);
        }
    }

    private Set<String> getColumnNames(Table table)
    {
        Set<String> names = new HashSet<>();

        for (Column col : table.getPartitionKey())
            names.add(col.getName());
        for (Column col : table.getClusteringColumns())
            names.add(col.getName());
        for (Column col : table.getColumns())
            names.add(col.getName());

        return names;
    }

    private String getColumnType(Table table, String columnName)
    {
        for (Column col : table.getColumns())
        {
            if (col.getName().equals(columnName))
                return col.getType();
        }
        return "UNKNOWN";
    }
}
