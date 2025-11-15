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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents differences between two schemas.
 */
public class SchemaDiff
{
    private final List<String> addedTables;
    private final List<String> removedTables;
    private final List<ColumnChange> columnChanges;
    private boolean hasBreakingChanges;
    private final List<String> breakingChanges;

    public SchemaDiff()
    {
        this.addedTables = new ArrayList<>();
        this.removedTables = new ArrayList<>();
        this.columnChanges = new ArrayList<>();
        this.breakingChanges = new ArrayList<>();
        this.hasBreakingChanges = false;
    }

    public void addTableAdded(String tableName)
    {
        addedTables.add(tableName);
    }

    public void addTableRemoved(String tableName)
    {
        removedTables.add(tableName);
    }

    public void addColumnChange(ColumnChange change)
    {
        columnChanges.add(change);
    }

    public void addBreakingChange(String description)
    {
        breakingChanges.add(description);
        hasBreakingChanges = true;
    }

    public List<String> getAddedTables() { return new ArrayList<>(addedTables); }
    public List<String> getRemovedTables() { return new ArrayList<>(removedTables); }
    public List<ColumnChange> getColumnChanges() { return new ArrayList<>(columnChanges); }

    public boolean hasBreakingChanges() { return hasBreakingChanges; }
    public void setHasBreakingChanges(boolean has) { this.hasBreakingChanges = has; }

    public boolean canMigrateOnline()
    {
        // Can migrate online if no breaking changes
        return !hasBreakingChanges;
    }

    public boolean requiresDataMigration()
    {
        // Requires data migration if tables are removed or PK changes
        return !removedTables.isEmpty() || hasBreakingChanges;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Schema Differences:\n");
        sb.append("  Added Tables: ").append(addedTables.size()).append("\n");
        sb.append("  Removed Tables: ").append(removedTables.size()).append("\n");
        sb.append("  Column Changes: ").append(columnChanges.size()).append("\n");
        sb.append("  Breaking Changes: ").append(hasBreakingChanges ? "YES" : "NO").append("\n");

        if (hasBreakingChanges)
        {
            sb.append("\nBreaking Changes:\n");
            for (String change : breakingChanges)
            {
                sb.append("  - ").append(change).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Represents a column change.
     */
    public static class ColumnChange
    {
        private final String tableName;
        private final String columnName;
        private final ChangeType changeType;
        private final String oldType;
        private final String newType;

        public ColumnChange(String tableName, String columnName, ChangeType changeType,
                           String oldType, String newType)
        {
            this.tableName = tableName;
            this.columnName = columnName;
            this.changeType = changeType;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getTableName() { return tableName; }
        public String getColumnName() { return columnName; }
        public ChangeType getChangeType() { return changeType; }

        public String toCQL()
        {
            switch (changeType)
            {
                case ADD:
                    return String.format("ALTER TABLE %s ADD %s %s", tableName, columnName, newType);
                case REMOVE:
                    return String.format("ALTER TABLE %s DROP %s", tableName, columnName);
                case MODIFY:
                    return String.format("ALTER TABLE %s ALTER %s TYPE %s", tableName, columnName, newType);
                default:
                    return "";
            }
        }
    }

    public enum ChangeType
    {
        ADD, REMOVE, MODIFY
    }
}
