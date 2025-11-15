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

package org.apache.cassandra.tools.nodetool;

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.unified.UCSMigrationManager;
import org.apache.cassandra.db.compaction.unified.UCSMigrationManager.MigrationOptions;
import org.apache.cassandra.db.compaction.unified.UCSMigrationManager.MigrationResult;
import org.apache.cassandra.tools.NodeProbe;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Migrate table to UCS with analysis.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
@Command(name = "compaction-migrate-to-ucs", description = "Migrate table to Unified Compaction Strategy")
public class CompactionMigrateToUCS extends AbstractCommand
{
    @Parameters(index = "0", description = "Keyspace name")
    private String keyspace;

    @Parameters(index = "1", description = "Table name")
    private String table;

    @Option(names = { "--analyze-first" }, description = "Run workload analysis before migration")
    private boolean analyzeFirst = false;

    @Option(names = { "--gradual" }, description = "Perform gradual migration with monitoring")
    private boolean gradual = false;

    @Option(names = { "--dry-run" }, description = "Show migration plan without executing")
    private boolean dryRun = false;

    @Override
    public void execute(NodeProbe probe)
    {
        try
        {
            System.out.printf("Migrating %s.%s to UnifiedCompactionStrategy%n", keyspace, table);
            if (dryRun)
            {
                System.out.println("DRY RUN - No changes will be made");
            }
            System.out.println("=".repeat(60));

            // Get the ColumnFamilyStore
            Keyspace ks = Keyspace.open(keyspace);
            ColumnFamilyStore cfs = ks.getColumnFamilyStore(table);

            // Configure migration options
            MigrationOptions options = new MigrationOptions()
                .setAnalyzeFirst(analyzeFirst)
                .setGradual(gradual)
                .setDryRun(dryRun);

            // Perform migration
            UCSMigrationManager manager = new UCSMigrationManager();
            MigrationResult result = manager.migrateToUCS(cfs, options);

            // Print result
            if (result.isSuccess())
            {
                if (result.wasDryRun())
                {
                    System.out.println("\nDry run completed successfully");
                    System.out.println("Execute without --dry-run to perform actual migration");
                }
                else
                {
                    System.out.println("\nMigration completed successfully!");
                    System.out.printf("Table %s.%s is now using UnifiedCompactionStrategy%n", keyspace, table);
                }
            }
            else
            {
                System.err.println("\nMigration failed: " + result.getError().getMessage());
                throw new RuntimeException("Migration failed", result.getError());
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error migrating to UCS: " + e.getMessage(), e);
        }
    }
}
