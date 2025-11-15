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

/**
 * Recommendation to add an index to improve query performance.
 */
public class IndexRecommendation extends Recommendation
{
    private final String columnName;
    private final String indexType;
    private final long estimatedIndexCost;

    public IndexRecommendation(String columnName,
                              double estimatedImprovement,
                              long estimatedIndexCost)
    {
        this(columnName, "SECONDARY", estimatedImprovement, estimatedIndexCost);
    }

    public IndexRecommendation(String columnName,
                              String indexType,
                              double estimatedImprovement,
                              long estimatedIndexCost)
    {
        super("ADD_INDEX",
              determinePriority(estimatedImprovement),
              String.format("Add index on column '%s' to improve query performance", columnName),
              estimatedImprovement,
              calculateConfidence(estimatedImprovement, estimatedIndexCost));

        this.columnName = columnName;
        this.indexType = indexType;
        this.estimatedIndexCost = estimatedIndexCost;
    }

    @Override
    public String getSuggestedAction()
    {
        return String.format("CREATE INDEX ON table_name (%s);", columnName);
    }

    public String getColumnName()
    {
        return columnName;
    }

    public String getIndexType()
    {
        return indexType;
    }

    public long getEstimatedIndexCost()
    {
        return estimatedIndexCost;
    }

    private static Priority determinePriority(double improvement)
    {
        if (improvement > 0.7)
            return Priority.HIGH;
        else if (improvement > 0.4)
            return Priority.MEDIUM;
        else
            return Priority.LOW;
    }

    private static double calculateConfidence(double improvement, long indexCost)
    {
        // Higher confidence for larger improvements and lower index costs
        double confidenceBase = improvement * 0.7;

        // Reduce confidence for expensive indexes
        if (indexCost > 1000000)
            confidenceBase *= 0.8;

        return Math.min(1.0, confidenceBase);
    }
}
