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

import java.util.HashMap;
import java.util.Map;

/**
 * Represents workload characteristics.
 */
public class WorkloadProfile
{
    public enum QueryType
    {
        READ, WRITE, DELETE
    }

    public enum DataPattern
    {
        TIME_SERIES, RANDOM, SEQUENTIAL
    }

    private final Map<QueryType, Integer> queryDistribution;
    private int expectedQPS;
    private int peakQPS;
    private DataPattern dataPattern;
    private int retentionDays;

    public WorkloadProfile()
    {
        this.queryDistribution = new HashMap<>();
        this.queryDistribution.put(QueryType.READ, 70);
        this.queryDistribution.put(QueryType.WRITE, 25);
        this.queryDistribution.put(QueryType.DELETE, 5);
        this.dataPattern = DataPattern.RANDOM;
        this.expectedQPS = 1000;
        this.peakQPS = 5000;
        this.retentionDays = 90;
    }

    public SchemaRecommendation recommendSchema()
    {
        SchemaRecommendation recommendation = new SchemaRecommendation();

        // Recommend compaction strategy
        if (dataPattern == DataPattern.TIME_SERIES)
        {
            recommendation.setCompactionStrategy("TimeWindowCompactionStrategy");
            recommendation.addParameter("compaction_window_size", "1");
            recommendation.addParameter("compaction_window_unit", "DAYS");
        }
        else if (queryDistribution.get(QueryType.READ) > 80)
        {
            recommendation.setCompactionStrategy("LeveledCompactionStrategy");
        }
        else
        {
            recommendation.setCompactionStrategy("UnifiedCompactionStrategy");
        }

        // Recommend caching
        if (queryDistribution.get(QueryType.READ) > 70)
        {
            recommendation.enableRowCache(true);
            recommendation.setCachingPolicy("ALL");
        }

        // Recommend replication
        if (expectedQPS > 10000)
        {
            recommendation.setReplicationFactor(Math.min(5, expectedQPS / 10000 + 3));
        }
        else
        {
            recommendation.setReplicationFactor(3);
        }

        return recommendation;
    }

    // Getters and setters
    public Map<QueryType, Integer> getQueryDistribution() { return new HashMap<>(queryDistribution); }
    public int getExpectedQPS() { return expectedQPS; }
    public int getPeakQPS() { return peakQPS; }
    public DataPattern getDataPattern() { return dataPattern; }
    public int getRetentionDays() { return retentionDays; }

    public void setQueryDistribution(QueryType type, int percentage)
    {
        queryDistribution.put(type, percentage);
    }

    public void setExpectedQPS(int qps) { this.expectedQPS = qps; }
    public void setPeakQPS(int qps) { this.peakQPS = qps; }
    public void setDataPattern(DataPattern pattern) { this.dataPattern = pattern; }
    public void setRetentionDays(int days) { this.retentionDays = days; }
}
