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
package org.apache.cassandra.tools.schemadesigner.generator;

import org.apache.cassandra.tools.schemadesigner.model.AccessPattern;
import org.apache.cassandra.tools.schemadesigner.model.Index;
import org.apache.cassandra.tools.schemadesigner.model.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Determines optimal secondary index placement.
 */
public class IndexOptimizer
{
    /**
     * Generates recommended secondary indexes based on access patterns.
     *
     * @param table Base table
     * @param patterns Access patterns
     * @return List of recommended indexes
     */
    public List<Index> generateIndexes(Table table, List<AccessPattern> patterns)
    {
        List<Index> indexes = new ArrayList<>();
        Set<String> indexedColumns = new HashSet<>();

        for (AccessPattern pattern : patterns)
        {
            // Consider indexes for low-cardinality equality predicates
            for (String column : pattern.getRegularColumns())
            {
                if (!indexedColumns.contains(column) && shouldIndex(column, pattern))
                {
                    String indexName = table.getName() + "_" + column + "_idx";
                    indexes.add(new Index(indexName, table.getName(), column));
                    indexedColumns.add(column);
                }
            }
        }

        return indexes;
    }

    private boolean shouldIndex(String column, AccessPattern pattern)
    {
        // Only index if:
        // 1. Column appears in predicates
        // 2. Pattern doesn't use ALLOW FILTERING
        // 3. Column is not already part of primary key
        return pattern.getPredicates().stream()
            .anyMatch(p -> p.getColumn().equals(column)) &&
            !pattern.isAllowFiltering() &&
            !pattern.getPartitionKeys().contains(column) &&
            !pattern.getClusteringKeys().contains(column);
    }
}
