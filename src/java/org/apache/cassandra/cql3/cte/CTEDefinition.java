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

import java.util.List;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;

/**
 * Represents a Common Table Expression (CTE) definition.
 *
 * A CTE is a temporary named result set that can be referenced within a query.
 * This class handles the materialization and caching of CTE results.
 */
public class CTEDefinition
{
    private final String name;
    private final SelectStatement statement;
    private final List<ColumnIdentifier> columns;
    private ResultSet cachedResult;
    private boolean materialized = false;

    public CTEDefinition(String name, SelectStatement statement, List<ColumnIdentifier> columns)
    {
        this.name = name;
        this.statement = statement;
        this.columns = columns;
    }

    /**
     * Execute the CTE query and cache the results.
     * Results are materialized once and reused for all references.
     *
     * @param state the query state
     * @param context the CTE execution context
     * @return the materialized result set
     */
    public ResultSet execute(QueryState state, CTEContext context)
    {
        if (!materialized)
        {
            cachedResult = materialize(state, context);
            materialized = true;
        }
        return cachedResult;
    }

    /**
     * Materialize the CTE by executing its query and storing results in memory.
     *
     * @param state the query state
     * @param context the CTE execution context
     * @return the materialized result set
     */
    private ResultSet materialize(QueryState state, CTEContext context)
    {
        // Execute the CTE query
        ResultMessage result = statement.execute(state, null, System.nanoTime());

        if (result instanceof ResultMessage.Rows)
        {
            ResultMessage.Rows rows = (ResultMessage.Rows) result;
            return new MaterializedResultSet(rows, name, columns);
        }

        throw new IllegalStateException("CTE query must return rows");
    }

    /**
     * Resolve references to other CTEs within this CTE's query.
     *
     * @param context the CTE execution context containing previously executed CTEs
     */
    public void resolveReferences(CTEContext context)
    {
        // Replace references to other CTEs in the statement
        // This would involve rewriting the query plan to use materialized results
        // Implementation depends on the query execution model
    }

    public String getName()
    {
        return name;
    }

    public SelectStatement getStatement()
    {
        return statement;
    }

    public List<ColumnIdentifier> getColumns()
    {
        return columns;
    }

    public boolean isMaterialized()
    {
        return materialized;
    }

    public ResultSet getCachedResult()
    {
        return cachedResult;
    }

    /**
     * Reset the materialization state, forcing re-execution on next access.
     */
    public void reset()
    {
        materialized = false;
        cachedResult = null;
    }
}
