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
package org.apache.cassandra.cql3.cte;

import java.util.ArrayList;
import java.util.List;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.transport.messages.ResultMessage;

/**
 * In-memory materialized result set for CTE results.
 *
 * Stores the complete result set of a CTE query in memory for reuse.
 * This class provides efficient access to materialized CTE data.
 */
public class MaterializedResultSet extends ResultSet
{
    private final String cteName;
    private final List<ColumnIdentifier> columnAliases;
    private final long memoryUsage;

    public MaterializedResultSet(ResultMessage.Rows rows, String cteName, List<ColumnIdentifier> columnAliases)
    {
        super(rows.result);
        this.cteName = cteName;
        this.columnAliases = columnAliases;
        this.memoryUsage = estimateMemoryUsage();
    }

    /**
     * Get the name of the CTE that produced this result set.
     *
     * @return the CTE name
     */
    public String getCteName()
    {
        return cteName;
    }

    /**
     * Get the column aliases defined for this CTE.
     *
     * @return the column aliases, or null if none were specified
     */
    public List<ColumnIdentifier> getColumnAliases()
    {
        return columnAliases;
    }

    /**
     * Estimate the memory usage of this materialized result set.
     *
     * @return estimated memory usage in bytes
     */
    private long estimateMemoryUsage()
    {
        // Base object overhead
        long size = 64; // Object header + references

        // Metadata overhead
        ResultSet.ResultMetadata metadata = this.metadata;
        size += metadata.getColumnCount() * 128; // Rough estimate per column spec

        // Row data
        int rowCount = this.size();
        if (rowCount > 0)
        {
            // Estimate based on first row and multiply
            // This is a rough approximation
            size += rowCount * 256; // Rough estimate per row
        }

        return size;
    }

    /**
     * Get the estimated memory usage of this result set.
     *
     * @return memory usage in bytes
     */
    public long getMemoryUsage()
    {
        return memoryUsage;
    }

    /**
     * Get a column specification by alias or original name.
     *
     * @param identifier the column identifier
     * @return the column specification
     */
    public ColumnSpecification getColumnSpecification(ColumnIdentifier identifier)
    {
        ResultSet.ResultMetadata metadata = this.metadata;
        List<ColumnSpecification> columnSpecs = metadata.requestNames();

        // First try to match by alias
        if (columnAliases != null)
        {
            for (int i = 0; i < columnAliases.size() && i < columnSpecs.size(); i++)
            {
                if (columnAliases.get(i).equals(identifier))
                {
                    return columnSpecs.get(i);
                }
            }
        }

        // Fall back to matching by column name
        for (ColumnSpecification spec : columnSpecs)
        {
            if (spec.name.equals(identifier))
            {
                return spec;
            }
        }

        return null;
    }

    @Override
    public String toString()
    {
        return String.format("MaterializedResultSet[cte=%s, rows=%d, memory=%d bytes]",
                             cteName, size(), memoryUsage);
    }
}
