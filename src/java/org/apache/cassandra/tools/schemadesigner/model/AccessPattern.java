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
package org.apache.cassandra.tools.schemadesigner.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a data access pattern extracted from queries.
 */
public class AccessPattern
{
    private final List<String> partitionKeys;
    private final List<String> clusteringKeys;
    private final List<String> regularColumns;
    private final List<Predicate> predicates;
    private final OrderBy ordering;
    private long estimatedFrequency;
    private final boolean allowFiltering;
    private final String entity;

    private static final long MAX_PARTITION_SIZE = 100_000_000; // 100MB

    public AccessPattern(String entity,
                        List<String> partitionKeys,
                        List<String> clusteringKeys,
                        List<String> regularColumns,
                        List<Predicate> predicates,
                        OrderBy ordering,
                        long estimatedFrequency,
                        boolean allowFiltering)
    {
        this.entity = entity;
        this.partitionKeys = new ArrayList<>(partitionKeys);
        this.clusteringKeys = new ArrayList<>(clusteringKeys);
        this.regularColumns = new ArrayList<>(regularColumns);
        this.predicates = new ArrayList<>(predicates);
        this.ordering = ordering;
        this.estimatedFrequency = estimatedFrequency;
        this.allowFiltering = allowFiltering;
    }

    public ValidationResult validate()
    {
        List<Issue> issues = new ArrayList<>();

        // Check for full table scans
        if (partitionKeys.isEmpty() && !allowFiltering)
        {
            issues.add(new Issue(
                Severity.ERROR,
                "Query requires full table scan without partition key"
            ));
        }

        // Check for large partitions
        if (estimatedPartitionSize() > MAX_PARTITION_SIZE)
        {
            issues.add(new Issue(
                Severity.WARNING,
                String.format("Partition may exceed %dMB", MAX_PARTITION_SIZE / 1_000_000)
            ));
        }

        // Check for clustering key ordering
        if (!isOrderingCompatible())
        {
            issues.add(new Issue(
                Severity.ERROR,
                "Query ordering incompatible with clustering key order"
            ));
        }

        return new ValidationResult(issues);
    }

    public long estimatedPartitionSize()
    {
        // Simple estimation based on number of clustering columns and estimated rows
        int estimatedRowsPerPartition = Math.max(1, clusteringKeys.size() * 100);
        int avgColumnSize = 100; // bytes
        return estimatedRowsPerPartition * regularColumns.size() * avgColumnSize;
    }

    public boolean isOrderingCompatible()
    {
        if (ordering == null || clusteringKeys.isEmpty())
            return true;

        // Check if ordering matches clustering key order
        List<String> orderColumns = ordering.getColumns();
        if (orderColumns.size() > clusteringKeys.size())
            return false;

        for (int i = 0; i < orderColumns.size(); i++)
        {
            if (!orderColumns.get(i).equals(clusteringKeys.get(i)))
                return false;
        }

        return true;
    }

    public String getSignature()
    {
        return String.format("%s:%s:%s",
            entity,
            String.join(",", partitionKeys),
            String.join(",", clusteringKeys));
    }

    public void merge(AccessPattern other)
    {
        this.estimatedFrequency += other.estimatedFrequency;
    }

    // Getters
    public String getEntity() { return entity; }
    public List<String> getPartitionKeys() { return new ArrayList<>(partitionKeys); }
    public List<String> getClusteringKeys() { return new ArrayList<>(clusteringKeys); }
    public List<String> getRegularColumns() { return new ArrayList<>(regularColumns); }
    public List<Predicate> getPredicates() { return new ArrayList<>(predicates); }
    public OrderBy getOrdering() { return ordering; }
    public long getEstimatedFrequency() { return estimatedFrequency; }
    public boolean isAllowFiltering() { return allowFiltering; }

    public void setEstimatedFrequency(long frequency)
    {
        this.estimatedFrequency = frequency;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessPattern that = (AccessPattern) o;
        return Objects.equals(getSignature(), that.getSignature());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSignature());
    }
}
