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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.exceptions.InvalidRequestException;

/**
 * Execution context for CTEs during query processing.
 *
 * Maintains the registry of all CTEs defined in the query and their execution state.
 * CTEs are stored in insertion order to maintain dependency ordering.
 */
public class CTEContext
{
    private final Map<String, CTEDefinition> cteDefinitions;
    private final QueryOptions queryOptions;

    public CTEContext(QueryOptions queryOptions)
    {
        this.cteDefinitions = new LinkedHashMap<>();
        this.queryOptions = queryOptions;
    }

    /**
     * Register a CTE definition in the context.
     *
     * @param name the CTE name
     * @param definition the CTE definition
     * @throws InvalidRequestException if a CTE with the same name already exists
     */
    public void register(String name, CTEDefinition definition)
    {
        if (cteDefinitions.containsKey(name))
        {
            throw new InvalidRequestException(String.format("CTE '%s' is already defined", name));
        }
        cteDefinitions.put(name, definition);
    }

    /**
     * Get a CTE definition by name.
     *
     * @param name the CTE name
     * @return the CTE definition
     * @throws InvalidRequestException if the CTE is not found
     */
    public CTEDefinition get(String name)
    {
        CTEDefinition definition = cteDefinitions.get(name);
        if (definition == null)
        {
            throw new InvalidRequestException(String.format("CTE '%s' is not defined", name));
        }
        return definition;
    }

    /**
     * Check if a CTE with the given name exists.
     *
     * @param name the CTE name
     * @return true if the CTE exists, false otherwise
     */
    public boolean contains(String name)
    {
        return cteDefinitions.containsKey(name);
    }

    /**
     * Get all registered CTE definitions in insertion order.
     *
     * @return map of CTE names to definitions
     */
    public Map<String, CTEDefinition> getAllDefinitions()
    {
        return cteDefinitions;
    }

    public QueryOptions getQueryOptions()
    {
        return queryOptions;
    }

    /**
     * Reset all CTE materialization states.
     * Useful for re-executing the query.
     */
    public void resetAll()
    {
        for (CTEDefinition definition : cteDefinitions.values())
        {
            definition.reset();
        }
    }

    /**
     * Get the number of registered CTEs.
     *
     * @return the number of CTEs
     */
    public int size()
    {
        return cteDefinitions.size();
    }

    /**
     * Check if the context is empty.
     *
     * @return true if no CTEs are registered
     */
    public boolean isEmpty()
    {
        return cteDefinitions.isEmpty();
    }
}
