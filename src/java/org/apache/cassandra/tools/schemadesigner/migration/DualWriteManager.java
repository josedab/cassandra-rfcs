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
package org.apache.cassandra.tools.schemadesigner.migration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages dual-write operations during schema migrations.
 * Writes to both old and new schemas during the migration period.
 */
public class DualWriteManager
{
    private final Map<String, DualWriteConfig> configs;
    private final Map<String, DualWriteStatistics> statistics;

    public DualWriteManager()
    {
        this.configs = new ConcurrentHashMap<>();
        this.statistics = new ConcurrentHashMap<>();
    }

    /**
     * Enables dual-write mode for a table.
     *
     * @param table Table name
     * @param config Dual-write configuration
     */
    public void enableDualWrite(String table, DualWriteConfig config)
    {
        configs.put(table, config);
        statistics.put(table, new DualWriteStatistics());
        System.out.println("Dual-write enabled for table: " + table);
    }

    /**
     * Disables dual-write mode for a table.
     *
     * @param table Table name
     */
    public void disableDualWrite(String table)
    {
        configs.remove(table);
        DualWriteStatistics stats = statistics.remove(table);
        if (stats != null)
        {
            System.out.println("Dual-write disabled for table: " + table);
            System.out.println(stats);
        }
    }

    /**
     * Executes a write operation in dual-write mode.
     *
     * @param table Table name
     * @param writeOperation Write operation to execute
     * @return true if successful
     */
    public boolean executeWrite(String table, WriteOperation writeOperation)
    {
        DualWriteConfig config = configs.get(table);
        if (config == null)
        {
            // Not in dual-write mode, execute normally
            return writeOperation.executeOnOld();
        }

        DualWriteStatistics stats = statistics.get(table);
        stats.incrementTotal();

        try
        {
            // Write to old schema
            boolean oldSuccess = writeOperation.executeOnOld();

            if (!oldSuccess && config.requiresBothSucceed())
            {
                stats.incrementErrors();
                return false;
            }

            // Write to new schema
            boolean newSuccess = writeOperation.executeOnNew();

            if (!newSuccess)
            {
                stats.incrementNewErrors();
                if (config.requiresBothSucceed())
                {
                    stats.incrementErrors();
                    return false;
                }
            }

            if (oldSuccess && newSuccess)
            {
                stats.incrementSuccessful();
            }

            // Check migration progress periodically
            if (stats.getTotalWrites() % 1000 == 0)
            {
                checkMigrationProgress(table, stats);
            }

            return oldSuccess; // Primary write determines success
        }
        catch (Exception e)
        {
            stats.incrementErrors();
            System.err.println("Error in dual-write for " + table + ": " + e.getMessage());
            return false;
        }
    }

    private void checkMigrationProgress(String table, DualWriteStatistics stats)
    {
        double errorRate = stats.getErrorRate();
        double newErrorRate = stats.getNewErrorRate();

        System.out.println(String.format(
            "Dual-write progress for %s: %d writes, %.2f%% errors, %.2f%% new-schema errors",
            table, stats.getTotalWrites(), errorRate * 100, newErrorRate * 100
        ));

        // Alert if error rate is too high
        if (errorRate > 0.05) // > 5% error rate
        {
            System.err.println("WARNING: High error rate detected for " + table);
        }
    }

    /**
     * Gets statistics for a table.
     *
     * @param table Table name
     * @return Statistics or null if not in dual-write mode
     */
    public DualWriteStatistics getStatistics(String table)
    {
        return statistics.get(table);
    }

    /**
     * Configuration for dual-write behavior.
     */
    public static class DualWriteConfig
    {
        private final boolean requiresBothSucceed;
        private final boolean validateConsistency;
        private final int asyncWriteThreads;

        public DualWriteConfig(boolean requiresBothSucceed, boolean validateConsistency, int asyncWriteThreads)
        {
            this.requiresBothSucceed = requiresBothSucceed;
            this.validateConsistency = validateConsistency;
            this.asyncWriteThreads = asyncWriteThreads;
        }

        public static DualWriteConfig defaultConfig()
        {
            return new DualWriteConfig(false, true, 4);
        }

        public boolean requiresBothSucceed() { return requiresBothSucceed; }
        public boolean shouldValidateConsistency() { return validateConsistency; }
        public int getAsyncWriteThreads() { return asyncWriteThreads; }
    }

    /**
     * Statistics for dual-write operations.
     */
    public static class DualWriteStatistics
    {
        private final AtomicLong totalWrites = new AtomicLong(0);
        private final AtomicLong successfulWrites = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);
        private final AtomicLong newSchemaErrors = new AtomicLong(0);

        public void incrementTotal() { totalWrites.incrementAndGet(); }
        public void incrementSuccessful() { successfulWrites.incrementAndGet(); }
        public void incrementErrors() { errors.incrementAndGet(); }
        public void incrementNewErrors() { newSchemaErrors.incrementAndGet(); }

        public long getTotalWrites() { return totalWrites.get(); }
        public long getSuccessfulWrites() { return successfulWrites.get(); }
        public long getErrors() { return errors.get(); }
        public long getNewSchemaErrors() { return newSchemaErrors.get(); }

        public double getErrorRate()
        {
            long total = totalWrites.get();
            return total > 0 ? (double) errors.get() / total : 0.0;
        }

        public double getNewErrorRate()
        {
            long total = totalWrites.get();
            return total > 0 ? (double) newSchemaErrors.get() / total : 0.0;
        }

        @Override
        public String toString()
        {
            return String.format(
                "Statistics: Total=%d, Successful=%d, Errors=%d, New Schema Errors=%d, Error Rate=%.2f%%",
                totalWrites.get(), successfulWrites.get(), errors.get(), newSchemaErrors.get(),
                getErrorRate() * 100
            );
        }
    }

    /**
     * Interface for write operations.
     */
    public interface WriteOperation
    {
        boolean executeOnOld();
        boolean executeOnNew();
    }
}
