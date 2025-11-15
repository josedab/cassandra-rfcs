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
package org.apache.cassandra.cql3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration and feature management for Enhanced CQL Features (RFC-0008).
 *
 * This class provides centralized management of feature flags and configuration
 * for CTEs, window functions, recursive queries, and enhanced aggregations.
 */
public class EnhancedCQLFeatures
{
    private static final Logger logger = LoggerFactory.getLogger(EnhancedCQLFeatures.class);

    // Feature flags (would be loaded from cassandra.yaml)
    private static volatile boolean cteEnabled = true;
    private static volatile boolean windowFunctionsEnabled = true;
    private static volatile boolean recursiveEnabled = false; // Disabled by default
    private static volatile boolean enhancedAggregatesEnabled = true;

    // CTE configuration
    private static volatile int cteMaxMemoryMB = 256;
    private static volatile boolean cteSpillToDisk = true;

    // Window function configuration
    private static volatile int windowMaxPartitionSize = 100000;
    private static volatile int windowMemoryLimitMB = 512;

    // Recursion configuration
    private static volatile int maxRecursionDepth = 100;
    private static volatile long recursionTimeoutMs = 30000;

    // Aggregate configuration
    private static volatile int aggregateMaxMemoryMB = 128;
    private static volatile double approxAggregat​ePrecision = 0.01;

    /**
     * Check if Common Table Expressions are enabled.
     */
    public static boolean isCTEEnabled()
    {
        return cteEnabled;
    }

    /**
     * Check if window functions are enabled.
     */
    public static boolean isWindowFunctionsEnabled()
    {
        return windowFunctionsEnabled;
    }

    /**
     * Check if recursive queries are enabled.
     */
    public static boolean isRecursiveEnabled()
    {
        return recursiveEnabled;
    }

    /**
     * Check if enhanced aggregates are enabled.
     */
    public static boolean isEnhancedAggregatesEnabled()
    {
        return enhancedAggregatesEnabled;
    }

    /**
     * Get maximum memory for CTE materialization in MB.
     */
    public static int getCTEMaxMemoryMB()
    {
        return cteMaxMemoryMB;
    }

    /**
     * Check if CTE should spill to disk when memory limit is reached.
     */
    public static boolean isCTESpillToDiskEnabled()
    {
        return cteSpillToDisk;
    }

    /**
     * Get maximum partition size for window functions.
     */
    public static int getWindowMaxPartitionSize()
    {
        return windowMaxPartitionSize;
    }

    /**
     * Get maximum memory for window functions in MB.
     */
    public static int getWindowMemoryLimitMB()
    {
        return windowMemoryLimitMB;
    }

    /**
     * Get maximum recursion depth for recursive CTEs.
     */
    public static int getMaxRecursionDepth()
    {
        return maxRecursionDepth;
    }

    /**
     * Get recursion timeout in milliseconds.
     */
    public static long getRecursionTimeoutMs()
    {
        return recursionTimeoutMs;
    }

    /**
     * Get maximum memory for aggregates in MB.
     */
    public static int getAggregateMaxMemoryMB()
    {
        return aggregateMaxMemoryMB;
    }

    /**
     * Get precision for approximate aggregates (0.0-1.0).
     */
    public static double getApproxAggregatePrecision()
    {
        return approxAggregatePrecision;
    }

    /**
     * Load configuration from cassandra.yaml.
     * This would be called during system initialization.
     */
    public static void loadConfiguration()
    {
        // TODO: Load from DatabaseDescriptor
        logger.info("Enhanced CQL Features Configuration:");
        logger.info("  CTE enabled: {}", cteEnabled);
        logger.info("  Window functions enabled: {}", windowFunctionsEnabled);
        logger.info("  Recursive queries enabled: {}", recursiveEnabled);
        logger.info("  Enhanced aggregates enabled: {}", enhancedAggregatesEnabled);
        logger.info("  CTE max memory: {} MB", cteMaxMemoryMB);
        logger.info("  Window max partition size: {}", windowMaxPartitionSize);
        logger.info("  Max recursion depth: {}", maxRecursionDepth);
    }

    /**
     * Enable or disable CTEs (for testing).
     */
    public static void setCTEEnabled(boolean enabled)
    {
        cteEnabled = enabled;
        logger.info("CTE enabled set to: {}", enabled);
    }

    /**
     * Enable or disable window functions (for testing).
     */
    public static void setWindowFunctionsEnabled(boolean enabled)
    {
        windowFunctionsEnabled = enabled;
        logger.info("Window functions enabled set to: {}", enabled);
    }

    /**
     * Enable or disable recursive queries (for testing).
     */
    public static void setRecursiveEnabled(boolean enabled)
    {
        recursiveEnabled = enabled;
        logger.info("Recursive queries enabled set to: {}", enabled);
    }

    /**
     * Enable or disable enhanced aggregates (for testing).
     */
    public static void setEnhancedAggregatesEnabled(boolean enabled)
    {
        enhancedAggregatesEnabled = enabled;
        logger.info("Enhanced aggregates enabled set to: {}", enabled);
    }

    /**
     * Reset all configuration to defaults (for testing).
     */
    public static void resetToDefaults()
    {
        cteEnabled = true;
        windowFunctionsEnabled = true;
        recursiveEnabled = false;
        enhancedAggregatesEnabled = true;
        cteMaxMemoryMB = 256;
        cteSpillToDisk = true;
        windowMaxPartitionSize = 100000;
        windowMemoryLimitMB = 512;
        maxRecursionDepth = 100;
        recursionTimeoutMs = 30000;
        aggregateMaxMemoryMB = 128;
        approxAggregatePrecision = 0.01;

        logger.info("Enhanced CQL Features configuration reset to defaults");
    }
}
