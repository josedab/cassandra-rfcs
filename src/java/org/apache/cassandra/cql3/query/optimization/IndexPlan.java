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
package org.apache.cassandra.cql3.query.optimization;

import java.util.Collections;
import java.util.List;

import org.apache.cassandra.index.SecondaryIndexManager;

/**
 * Represents a plan for using one or more indexes during query execution.
 * Supports single index usage, index intersection, and index union.
 */
public class IndexPlan
{
    private final IndexOperation operation;
    private final List<String> indexNames;
    private final double estimatedCost;
    private final double estimatedSelectivity;

    private IndexPlan(IndexOperation operation,
                     List<String> indexNames,
                     double estimatedCost,
                     double estimatedSelectivity)
    {
        this.operation = operation;
        this.indexNames = indexNames != null ? indexNames : Collections.emptyList();
        this.estimatedCost = estimatedCost;
        this.estimatedSelectivity = estimatedSelectivity;
    }

    /**
     * Create a plan that doesn't use any index.
     */
    public static IndexPlan noIndex()
    {
        return new IndexPlan(IndexOperation.NONE, Collections.emptyList(), 0.0, 1.0);
    }

    /**
     * Create a plan that uses a single index.
     */
    public static IndexPlan singleIndex(String indexName, double cost, double selectivity)
    {
        return new IndexPlan(IndexOperation.SINGLE,
                           Collections.singletonList(indexName),
                           cost,
                           selectivity);
    }

    /**
     * Create a plan that intersects multiple indexes.
     */
    public static IndexPlan intersection(List<String> indexNames, double cost, double selectivity)
    {
        return new IndexPlan(IndexOperation.INTERSECTION, indexNames, cost, selectivity);
    }

    /**
     * Create a plan that unions multiple indexes.
     */
    public static IndexPlan union(List<String> indexNames, double cost, double selectivity)
    {
        return new IndexPlan(IndexOperation.UNION, indexNames, cost, selectivity);
    }

    public IndexOperation getOperation()
    {
        return operation;
    }

    public List<String> getIndexNames()
    {
        return Collections.unmodifiableList(indexNames);
    }

    public double getEstimatedCost()
    {
        return estimatedCost;
    }

    public double getEstimatedSelectivity()
    {
        return estimatedSelectivity;
    }

    public boolean usesIndex()
    {
        return operation != IndexOperation.NONE;
    }

    public boolean isIntersection()
    {
        return operation == IndexOperation.INTERSECTION;
    }

    public boolean isUnion()
    {
        return operation == IndexOperation.UNION;
    }

    public boolean isSingle()
    {
        return operation == IndexOperation.SINGLE;
    }

    @Override
    public String toString()
    {
        return String.format("IndexPlan{operation=%s, indexes=%s, cost=%.2f, selectivity=%.2f}",
                             operation, indexNames, estimatedCost, estimatedSelectivity);
    }

    /**
     * Types of index operations.
     */
    public enum IndexOperation
    {
        NONE,
        SINGLE,
        INTERSECTION,
        UNION
    }
}
