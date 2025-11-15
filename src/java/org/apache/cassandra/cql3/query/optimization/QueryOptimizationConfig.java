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
 * Configuration for query optimization features.
 * Controls which optimization techniques are enabled and their parameters.
 */
public class QueryOptimizationConfig
{
    // Master switch for all query optimization
    private boolean enabled;

    // Cost-based optimization
    private boolean enableCostEstimation;
    private boolean cachePlans;
    private int planCacheSize;

    // Index optimization
    private boolean enableIndexIntersection;
    private boolean enableIndexUnion;
    private int maxIndexesPerQuery;

    // Adaptive execution
    private boolean enableAdaptiveExecution;
    private int initialBatchSize;
    private long targetResponseTimeMs;
    private long memoryLimitBytes;

    // Statistics collection
    private boolean enableStatisticsCollection;
    private double statisticsSampleRate;
    private long statisticsUpdateIntervalMs;

    // Recommendations
    private boolean enableRecommendations;
    private int minQueryFrequencyForRecommendation;
    private long recommendationAnalysisIntervalMs;

    // Default values
    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_COST_ESTIMATION = true;
    public static final boolean DEFAULT_CACHE_PLANS = true;
    public static final int DEFAULT_PLAN_CACHE_SIZE = 1000;
    public static final boolean DEFAULT_INDEX_INTERSECTION = true;
    public static final boolean DEFAULT_INDEX_UNION = true;
    public static final int DEFAULT_MAX_INDEXES = 3;
    public static final boolean DEFAULT_ADAPTIVE_EXECUTION = true;
    public static final int DEFAULT_INITIAL_BATCH_SIZE = 100;
    public static final long DEFAULT_TARGET_RESPONSE_TIME_MS = 100;
    public static final long DEFAULT_MEMORY_LIMIT_BYTES = 128 * 1024 * 1024; // 128 MB
    public static final boolean DEFAULT_STATISTICS_ENABLED = true;
    public static final double DEFAULT_SAMPLE_RATE = 0.01;
    public static final long DEFAULT_UPDATE_INTERVAL_MS = 3600000; // 1 hour
    public static final boolean DEFAULT_RECOMMENDATIONS_ENABLED = true;
    public static final int DEFAULT_MIN_QUERY_FREQUENCY = 100;
    public static final long DEFAULT_ANALYSIS_INTERVAL_MS = 86400000; // 24 hours

    public QueryOptimizationConfig()
    {
        this.enabled = DEFAULT_ENABLED;
        this.enableCostEstimation = DEFAULT_COST_ESTIMATION;
        this.cachePlans = DEFAULT_CACHE_PLANS;
        this.planCacheSize = DEFAULT_PLAN_CACHE_SIZE;
        this.enableIndexIntersection = DEFAULT_INDEX_INTERSECTION;
        this.enableIndexUnion = DEFAULT_INDEX_UNION;
        this.maxIndexesPerQuery = DEFAULT_MAX_INDEXES;
        this.enableAdaptiveExecution = DEFAULT_ADAPTIVE_EXECUTION;
        this.initialBatchSize = DEFAULT_INITIAL_BATCH_SIZE;
        this.targetResponseTimeMs = DEFAULT_TARGET_RESPONSE_TIME_MS;
        this.memoryLimitBytes = DEFAULT_MEMORY_LIMIT_BYTES;
        this.enableStatisticsCollection = DEFAULT_STATISTICS_ENABLED;
        this.statisticsSampleRate = DEFAULT_SAMPLE_RATE;
        this.statisticsUpdateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS;
        this.enableRecommendations = DEFAULT_RECOMMENDATIONS_ENABLED;
        this.minQueryFrequencyForRecommendation = DEFAULT_MIN_QUERY_FREQUENCY;
        this.recommendationAnalysisIntervalMs = DEFAULT_ANALYSIS_INTERVAL_MS;
    }

    // Getters and setters

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isEnableCostEstimation()
    {
        return enabled && enableCostEstimation;
    }

    public void setEnableCostEstimation(boolean enableCostEstimation)
    {
        this.enableCostEstimation = enableCostEstimation;
    }

    public boolean isCachePlans()
    {
        return cachePlans;
    }

    public void setCachePlans(boolean cachePlans)
    {
        this.cachePlans = cachePlans;
    }

    public int getPlanCacheSize()
    {
        return planCacheSize;
    }

    public void setPlanCacheSize(int planCacheSize)
    {
        this.planCacheSize = planCacheSize;
    }

    public boolean isEnableIndexIntersection()
    {
        return enabled && enableIndexIntersection;
    }

    public void setEnableIndexIntersection(boolean enableIndexIntersection)
    {
        this.enableIndexIntersection = enableIndexIntersection;
    }

    public boolean isEnableIndexUnion()
    {
        return enabled && enableIndexUnion;
    }

    public void setEnableIndexUnion(boolean enableIndexUnion)
    {
        this.enableIndexUnion = enableIndexUnion;
    }

    public int getMaxIndexesPerQuery()
    {
        return maxIndexesPerQuery;
    }

    public void setMaxIndexesPerQuery(int maxIndexesPerQuery)
    {
        this.maxIndexesPerQuery = maxIndexesPerQuery;
    }

    public boolean isEnableAdaptiveExecution()
    {
        return enabled && enableAdaptiveExecution;
    }

    public void setEnableAdaptiveExecution(boolean enableAdaptiveExecution)
    {
        this.enableAdaptiveExecution = enableAdaptiveExecution;
    }

    public int getInitialBatchSize()
    {
        return initialBatchSize;
    }

    public void setInitialBatchSize(int initialBatchSize)
    {
        this.initialBatchSize = initialBatchSize;
    }

    public long getTargetResponseTimeMs()
    {
        return targetResponseTimeMs;
    }

    public void setTargetResponseTimeMs(long targetResponseTimeMs)
    {
        this.targetResponseTimeMs = targetResponseTimeMs;
    }

    public long getMemoryLimitBytes()
    {
        return memoryLimitBytes;
    }

    public void setMemoryLimitBytes(long memoryLimitBytes)
    {
        this.memoryLimitBytes = memoryLimitBytes;
    }

    public boolean isEnableStatisticsCollection()
    {
        return enableStatisticsCollection;
    }

    public void setEnableStatisticsCollection(boolean enableStatisticsCollection)
    {
        this.enableStatisticsCollection = enableStatisticsCollection;
    }

    public double getStatisticsSampleRate()
    {
        return statisticsSampleRate;
    }

    public void setStatisticsSampleRate(double statisticsSampleRate)
    {
        this.statisticsSampleRate = statisticsSampleRate;
    }

    public long getStatisticsUpdateIntervalMs()
    {
        return statisticsUpdateIntervalMs;
    }

    public void setStatisticsUpdateIntervalMs(long statisticsUpdateIntervalMs)
    {
        this.statisticsUpdateIntervalMs = statisticsUpdateIntervalMs;
    }

    public boolean isEnableRecommendations()
    {
        return enabled && enableRecommendations;
    }

    public void setEnableRecommendations(boolean enableRecommendations)
    {
        this.enableRecommendations = enableRecommendations;
    }

    public int getMinQueryFrequencyForRecommendation()
    {
        return minQueryFrequencyForRecommendation;
    }

    public void setMinQueryFrequencyForRecommendation(int minQueryFrequencyForRecommendation)
    {
        this.minQueryFrequencyForRecommendation = minQueryFrequencyForRecommendation;
    }

    public long getRecommendationAnalysisIntervalMs()
    {
        return recommendationAnalysisIntervalMs;
    }

    public void setRecommendationAnalysisIntervalMs(long recommendationAnalysisIntervalMs)
    {
        this.recommendationAnalysisIntervalMs = recommendationAnalysisIntervalMs;
    }

    @Override
    public String toString()
    {
        return String.format("QueryOptimizationConfig{enabled=%s, costEstimation=%s, indexIntersection=%s, adaptive=%s}",
                             enabled, enableCostEstimation, enableIndexIntersection, enableAdaptiveExecution);
    }
}
