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
package org.apache.cassandra.cql3.window;

import java.util.List;

import org.apache.cassandra.cql3.ColumnIdentifier;

/**
 * Definition of a window for window functions.
 *
 * A window defines how to partition and order rows for window function evaluation.
 */
public class WindowDefinition
{
    private final List<ColumnIdentifier> partitionBy;
    private final List<OrderByClause> orderBy;
    private final WindowFrame frame;
    private final String name;

    public WindowDefinition(String name,
                            List<ColumnIdentifier> partitionBy,
                            List<OrderByClause> orderBy,
                            WindowFrame frame)
    {
        this.name = name;
        this.partitionBy = partitionBy;
        this.orderBy = orderBy;
        this.frame = frame != null ? frame : WindowFrame.defaultFrame();
    }

    /**
     * Get the columns used for partitioning.
     *
     * @return list of partition columns
     */
    public List<ColumnIdentifier> getPartitionBy()
    {
        return partitionBy;
    }

    /**
     * Get the ORDER BY clauses for window ordering.
     *
     * @return list of order by clauses
     */
    public List<OrderByClause> getOrderBy()
    {
        return orderBy;
    }

    /**
     * Get the window frame definition.
     *
     * @return the window frame
     */
    public WindowFrame getFrame()
    {
        return frame;
    }

    /**
     * Get the window name (for named windows).
     *
     * @return the window name, or null for inline windows
     */
    public String getName()
    {
        return name;
    }

    /**
     * Check if this window has partitioning.
     *
     * @return true if PARTITION BY is specified
     */
    public boolean hasPartitioning()
    {
        return partitionBy != null && !partitionBy.isEmpty();
    }

    /**
     * Check if this window has ordering.
     *
     * @return true if ORDER BY is specified
     */
    public boolean hasOrdering()
    {
        return orderBy != null && !orderBy.isEmpty();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (name != null)
        {
            sb.append(name).append(": ");
        }

        if (hasPartitioning())
        {
            sb.append("PARTITION BY ").append(partitionBy);
        }

        if (hasOrdering())
        {
            if (hasPartitioning())
            {
                sb.append(" ");
            }
            sb.append("ORDER BY ").append(orderBy);
        }

        if (frame != null)
        {
            if (hasPartitioning() || hasOrdering())
            {
                sb.append(" ");
            }
            sb.append(frame);
        }

        return sb.toString();
    }

    /**
     * Represents an ORDER BY clause in a window definition.
     */
    public static class OrderByClause
    {
        private final ColumnIdentifier column;
        private final boolean ascending;
        private final boolean nullsFirst;

        public OrderByClause(ColumnIdentifier column, boolean ascending, boolean nullsFirst)
        {
            this.column = column;
            this.ascending = ascending;
            this.nullsFirst = nullsFirst;
        }

        public ColumnIdentifier getColumn()
        {
            return column;
        }

        public boolean isAscending()
        {
            return ascending;
        }

        public boolean isNullsFirst()
        {
            return nullsFirst;
        }

        @Override
        public String toString()
        {
            return column + (ascending ? " ASC" : " DESC") +
                   (nullsFirst ? " NULLS FIRST" : " NULLS LAST");
        }
    }
}
