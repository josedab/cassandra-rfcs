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

/**
 * Represents a single migration step.
 */
public class MigrationStep
{
    private final String type;
    private final String cql;
    private final String description;

    public MigrationStep(String type, String cql, String description)
    {
        this.type = type;
        this.cql = cql;
        this.description = description;
    }

    public String getType() { return type; }
    public String getCql() { return cql; }
    public String getDescription() { return description; }

    /**
     * Creates the inverse step for rollback.
     */
    public MigrationStep createInverse()
    {
        switch (type)
        {
            case "CREATE_TABLE":
                String tableName = extractTableName(cql);
                return new MigrationStep("DROP_TABLE", "DROP TABLE " + tableName, "Drop table: " + tableName);

            case "DROP_TABLE":
                // Can't easily inverse a DROP - need original schema
                return null;

            case "ADD_COLUMN":
                // Extract and create ALTER DROP
                return null;

            case "ALTER_TABLE":
                // Complex - might not be invertible
                return null;

            default:
                return null;
        }
    }

    private String extractTableName(String cql)
    {
        // Simple extraction - would need more robust parsing
        String[] parts = cql.split("\\s+");
        for (int i = 0; i < parts.length - 1; i++)
        {
            if (parts[i].equalsIgnoreCase("TABLE"))
            {
                return parts[i + 1];
            }
        }
        return "unknown";
    }

    @Override
    public String toString()
    {
        return description + " [" + type + "]";
    }
}
