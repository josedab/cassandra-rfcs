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

import org.apache.cassandra.exceptions.InvalidRequestException;

/**
 * Dependency graph for Common Table Expressions.
 *
 * Analyzes and validates dependencies between CTEs to ensure proper execution order
 * and detect circular references.
 */
public class CTEDependencyGraph
{
    private final Map<String, Set<String>> dependencies;
    private final Set<String> cteNames;

    public CTEDependencyGraph()
    {
        this.dependencies = new HashMap<>();
        this.cteNames = new HashSet<>();
    }

    /**
     * Add a CTE to the dependency graph.
     *
     * @param cteName the name of the CTE
     */
    public void addCTE(String cteName)
    {
        cteNames.add(cteName);
        dependencies.putIfAbsent(cteName, new HashSet<>());
    }

    /**
     * Add a dependency between CTEs.
     *
     * @param from the CTE that depends on another
     * @param to the CTE being depended upon
     */
    public void addDependency(String from, String to)
    {
        dependencies.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    /**
     * Check if the dependency graph contains cycles.
     *
     * @return true if there are circular dependencies
     */
    public boolean hasCycles()
    {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String cte : cteNames)
        {
            if (hasCycleUtil(cte, visited, recursionStack))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Utility method for cycle detection using DFS.
     */
    private boolean hasCycleUtil(String cte, Set<String> visited, Set<String> recursionStack)
    {
        if (recursionStack.contains(cte))
        {
            return true;
        }

        if (visited.contains(cte))
        {
            return false;
        }

        visited.add(cte);
        recursionStack.add(cte);

        Set<String> deps = dependencies.get(cte);
        if (deps != null)
        {
            for (String dep : deps)
            {
                if (hasCycleUtil(dep, visited, recursionStack))
                {
                    return true;
                }
            }
        }

        recursionStack.remove(cte);
        return false;
    }

    /**
     * Perform topological sort to determine CTE execution order.
     *
     * @return list of CTE names in dependency order
     * @throws InvalidRequestException if there are circular dependencies
     */
    public List<String> topologicalSort()
    {
        if (hasCycles())
        {
            throw new InvalidRequestException("Circular CTE reference detected");
        }

        Stack<String> stack = new Stack<>();
        Set<String> visited = new HashSet<>();

        for (String cte : cteNames)
        {
            if (!visited.contains(cte))
            {
                topologicalSortUtil(cte, visited, stack);
            }
        }

        List<String> result = new ArrayList<>();
        while (!stack.isEmpty())
        {
            result.add(stack.pop());
        }

        return result;
    }

    /**
     * Utility method for topological sort using DFS.
     */
    private void topologicalSortUtil(String cte, Set<String> visited, Stack<String> stack)
    {
        visited.add(cte);

        Set<String> deps = dependencies.get(cte);
        if (deps != null)
        {
            for (String dep : deps)
            {
                if (!visited.contains(dep))
                {
                    topologicalSortUtil(dep, visited, stack);
                }
            }
        }

        stack.push(cte);
    }

    /**
     * Get all dependencies for a specific CTE.
     *
     * @param cteName the CTE name
     * @return set of CTE names this CTE depends on
     */
    public Set<String> getDependencies(String cteName)
    {
        return dependencies.getOrDefault(cteName, Collections.emptySet());
    }

    /**
     * Get all CTEs that depend on a specific CTE.
     *
     * @param cteName the CTE name
     * @return set of CTE names that depend on this CTE
     */
    public Set<String> getDependents(String cteName)
    {
        Set<String> dependents = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet())
        {
            if (entry.getValue().contains(cteName))
            {
                dependents.add(entry.getKey());
            }
        }
        return dependents;
    }
}
