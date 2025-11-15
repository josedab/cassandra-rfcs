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
package org.apache.cassandra.cql3.statements;

import java.util.ArrayList;
import java.util.List;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.cte.CTEContext;
import org.apache.cassandra.cql3.cte.CTEProcessor;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;

/**
 * Extension of SelectStatement that supports Common Table Expressions (CTEs).
 *
 * This class wraps a regular SelectStatement and adds CTE processing capability.
 */
public class SelectStatementWithCTE extends SelectStatement
{
    private final List<CTEClause> cteClaus;
    private final SelectStatement mainQuery;
    private final CTEProcessor cteProcessor;

    protected SelectStatementWithCTE(List<CTEClause> cteClauses,
                                     SelectStatement mainQuery,
                                     QueryState state,
                                     QueryOptions options)
    {
        super(mainQuery.table,
              mainQuery.bindVariables,
              mainQuery.getParameters(),
              mainQuery.getSelection(),
              mainQuery.getRestrictions(),
              mainQuery.isReversed(),
              mainQuery.getAggregationSpecFactory(),
              mainQuery.getPartitionKeyBindVariableIndexes(),
              mainQuery.getPageableRange(),
              mainQuery.getQueryOrder(),
              mainQuery.isRowFilter(),
              mainQuery.getLimit(),
              mainQuery.getPerPartitionLimit(),
              mainQuery.getSelectOptions());

        this.cteClauses = cteClauses;
        this.mainQuery = mainQuery;
        this.cteProcessor = new CTEProcessor(state, options);
    }

    /**
     * Execute the statement with CTE processing.
     */
    @Override
    public ResultMessage execute(QueryState state, QueryOptions options, long queryStartNanoTime)
    {
        // Register all CTEs
        for (CTEClause cte : cteClauses)
        {
            cteProcessor.registerCTE(cte.getName(), cte.getStatement(), cte.getColumns());
        }

        // Execute CTEs in dependency order
        CTEContext context = cteProcessor.executeCTEs();

        // Execute main query with CTE context
        return executeWithCTEContext(state, options, context, queryStartNanoTime);
    }

    /**
     * Execute the main query with access to materialized CTEs.
     */
    private ResultMessage executeWithCTEContext(QueryState state,
                                                QueryOptions options,
                                                CTEContext context,
                                                long queryStartNanoTime)
    {
        // The main query would be rewritten to use materialized CTE results
        // This is a placeholder for the actual implementation
        return mainQuery.execute(state, options, queryStartNanoTime);
    }

    /**
     * Represents a CTE clause in the WITH statement.
     */
    public static class CTEClause
    {
        private final String name;
        private final SelectStatement statement;
        private final List<ColumnIdentifier> columns;
        private final boolean recursive;

        public CTEClause(String name,
                         SelectStatement statement,
                         List<ColumnIdentifier> columns,
                         boolean recursive)
        {
            this.name = name;
            this.statement = statement;
            this.columns = columns;
            this.recursive = recursive;
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

        public boolean isRecursive()
        {
            return recursive;
        }
    }

    /**
     * Builder for creating SelectStatementWithCTE instances.
     */
    public static class Builder
    {
        private final List<CTEClause> cteClauses;
        private SelectStatement mainQuery;

        public Builder()
        {
            this.cteClauses = new ArrayList<>();
        }

        public Builder addCTE(String name,
                              SelectStatement statement,
                              List<ColumnIdentifier> columns,
                              boolean recursive)
        {
            cteClauses.add(new CTEClause(name, statement, columns, recursive));
            return this;
        }

        public Builder setMainQuery(SelectStatement query)
        {
            this.mainQuery = query;
            return this;
        }

        public SelectStatementWithCTE build(QueryState state, QueryOptions options)
        {
            if (mainQuery == null)
            {
                throw new IllegalStateException("Main query must be set");
            }

            return new SelectStatementWithCTE(cteClauses, mainQuery, state, options);
        }
    }

    /**
     * Raw statement for parsing CTE queries.
     */
    public static class RawStatement extends SelectStatement.RawStatement
    {
        private final List<RawCTEClause> rawCTEs;

        public RawStatement(List<RawCTEClause> rawCTEs,
                            SelectStatement.RawStatement mainQuery)
        {
            super(mainQuery.getCFName(),
                  mainQuery.getParameters(),
                  mainQuery.getSelectClause(),
                  mainQuery.getWhereClause(),
                  mainQuery.getLimit(),
                  mainQuery.getPerPartitionLimit(),
                  mainQuery.getSourceString(),
                  mainQuery.getSelectOptions());

            this.rawCTEs = rawCTEs;
        }

        public List<RawCTEClause> getRawCTEs()
        {
            return rawCTEs;
        }
    }

    /**
     * Raw CTE clause from parser.
     */
    public static class RawCTEClause
    {
        private final String name;
        private final SelectStatement.RawStatement statement;
        private final List<ColumnIdentifier> columns;
        private final boolean recursive;

        public RawCTEClause(String name,
                            SelectStatement.RawStatement statement,
                            List<ColumnIdentifier> columns,
                            boolean recursive)
        {
            this.name = name;
            this.statement = statement;
            this.columns = columns;
            this.recursive = recursive;
        }

        public String getName()
        {
            return name;
        }

        public SelectStatement.RawStatement getStatement()
        {
            return statement;
        }

        public List<ColumnIdentifier> getColumns()
        {
            return columns;
        }

        public boolean isRecursive()
        {
            return recursive;
        }
    }
}
