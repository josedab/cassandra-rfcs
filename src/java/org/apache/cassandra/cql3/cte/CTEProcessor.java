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

import java.util.*;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.service.QueryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main processor for Common Table Expressions (CTEs).
 *
 * Handles parsing, dependency analysis, and execution coordination for WITH clauses.
 */
public class CTEProcessor
{
    private static final Logger logger = LoggerFactory.getLogger(CTEProcessor.class);

    private final Map<String, CTEDefinition> cteDefinitions;
    private final QueryState queryState;
    private final QueryOptions queryOptions;

    public CTEProcessor(QueryState queryState, QueryOptions queryOptions)
    {
        this.cteDefinitions = new LinkedHashMap<>();
        this.queryState = queryState;
        this.queryOptions = queryOptions;
    }

    /**
     * Register a CTE with the processor.
     *
     * @param name the CTE name
     * @param statement the SELECT statement defining the CTE
     * @param columns optional column aliases
     */
    public void registerCTE(String name, SelectStatement statement, List<ColumnIdentifier> columns)
    {
        if (cteDefinitions.containsKey(name))
        {
            throw new InvalidRequestException(String.format("CTE '%s' is already defined", name));
        }

        CTEDefinition definition = new CTEDefinition(name, statement, columns);
        cteDefinitions.put(name, definition);

        logger.debug("Registered CTE: {}", name);
    }

    /**
     * Validate and order CTEs based on dependencies.
     *
     * @return list of CTE names in dependency order
     * @throws InvalidRequestException if circular dependencies are detected
     */
    public List<String> validateAndOrderCTEs()
    {
        CTEDependencyGraph graph = buildDependencyGraph();

        if (graph.hasCycles())
        {
            throw new InvalidRequestException("Circular CTE reference detected");
        }

        List<String> orderedCTEs = graph.topologicalSort();
        logger.debug("CTE execution order: {}", orderedCTEs);

        return orderedCTEs;
    }

    /**
     * Build dependency graph by analyzing CTE references.
     *
     * @return the dependency graph
     */
    private CTEDependencyGraph buildDependencyGraph()
    {
        CTEDependencyGraph graph = new CTEDependencyGraph();

        // Add all CTEs to the graph
        for (String cteName : cteDefinitions.keySet())
        {
            graph.addCTE(cteName);
        }

        // Analyze dependencies
        for (Map.Entry<String, CTEDefinition> entry : cteDefinitions.entrySet())
        {
            String cteName = entry.getKey();
            SelectStatement statement = entry.getValue().getStatement();

            // Extract referenced table names from the statement
            // In a real implementation, this would analyze the FROM clause
            Set<String> referencedTables = extractTableReferences(statement);

            for (String referencedTable : referencedTables)
            {
                // If the referenced table is another CTE, add the dependency
                if (cteDefinitions.containsKey(referencedTable))
                {
                    graph.addDependency(cteName, referencedTable);
                }
            }
        }

        return graph;
    }

    /**
     * Extract table references from a SELECT statement.
     *
     * @param statement the SELECT statement
     * @return set of referenced table names
     */
    private Set<String> extractTableReferences(SelectStatement statement)
    {
        // This is a placeholder for actual table reference extraction
        // In a real implementation, this would traverse the query AST
        // to find all table references in FROM clauses
        Set<String> references = new HashSet<>();

        // TODO: Implement actual table reference extraction from statement
        // This would involve analyzing the statement's table metadata

        return references;
    }

    /**
     * Execute all CTEs in dependency order.
     *
     * @return CTE execution context with all materialized results
     */
    public CTEContext executeCTEs()
    {
        CTEContext context = new CTEContext(queryOptions);
        List<String> executionOrder = validateAndOrderCTEs();

        for (String cteName : executionOrder)
        {
            CTEDefinition definition = cteDefinitions.get(cteName);

            // Resolve references to previously executed CTEs
            definition.resolveReferences(context);

            // Execute and materialize the CTE
            ResultSet result = definition.execute(queryState, context);

            // Register in context for subsequent CTEs
            context.register(cteName, definition);

            logger.debug("Executed CTE '{}': {} rows", cteName, result.size());
        }

        return context;
    }

    /**
     * Get a specific CTE definition.
     *
     * @param name the CTE name
     * @return the CTE definition
     */
    public CTEDefinition getCTE(String name)
    {
        return cteDefinitions.get(name);
    }

    /**
     * Check if a CTE with the given name is registered.
     *
     * @param name the CTE name
     * @return true if the CTE exists
     */
    public boolean hasCTE(String name)
    {
        return cteDefinitions.containsKey(name);
    }

    /**
     * Get all registered CTE names.
     *
     * @return set of CTE names
     */
    public Set<String> getCTENames()
    {
        return cteDefinitions.keySet();
    }

    /**
     * Get the number of registered CTEs.
     *
     * @return the number of CTEs
     */
    public int getCTECount()
    {
        return cteDefinitions.size();
    }
}
