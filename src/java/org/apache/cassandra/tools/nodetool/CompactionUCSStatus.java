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
import org.apache.cassandra.db.compaction.unified.UCSMonitor;
import org.apache.cassandra.db.compaction.unified.UCSMonitor.ShardStatistics;
import org.apache.cassandra.db.compaction.unified.UCSMonitor.EfficiencyMetrics;
import org.apache.cassandra.tools.NodeProbe;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Get detailed UCS metrics.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
@Command(name = "compaction-ucs-status", description = "Display UCS status and metrics")
public class CompactionUCSStatus extends AbstractCommand
{
    @Option(names = { "--keyspace" }, description = "Keyspace name")
    private String keyspace;

    @Option(names = { "--table" }, description = "Table name")
    private String table;

    @Override
    public void execute(NodeProbe probe)
    {
        try
        {
            UCSMonitor monitor = new UCSMonitor();

            System.out.println("UCS Performance Dashboard");
            System.out.println("=".repeat(60));

            if (keyspace != null && table != null)
            {
                displayTableStatus(monitor, keyspace, table);
            }
            else if (keyspace != null)
            {
                displayKeyspaceStatus(monitor, keyspace);
            }
            else
            {
                displayAllStatus(monitor);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error getting UCS status: " + e.getMessage(), e);
        }
    }

    private void displayTableStatus(UCSMonitor monitor, String keyspaceName, String tableName)
    {
        Keyspace ks = Keyspace.open(keyspaceName);
        ColumnFamilyStore cfs = ks.getColumnFamilyStore(tableName);

        System.out.printf("\nTable: %s.%s%n", keyspaceName, tableName);
        System.out.println("-".repeat(60));

        // Display shard distribution
        ShardStatistics shardStats = monitor.getShardStatistics(cfs);
        System.out.println("\nShard Distribution:");
        for (UCSMonitor.ShardInfo shard : shardStats.shards)
        {
            long totalSize = shard.getTotalSize();
            int sstableCount = shard.levels.stream().mapToInt(l -> l.sstableCount).sum();

            System.out.printf("  Shard %d: %d SSTables, %.2f GB (read amp: %.2f)%n",
                            shard.shardId, sstableCount, totalSize / (1024.0 * 1024 * 1024),
                            shard.readAmplification);
        }
        System.out.printf("  Balance Score: %.2f%n", shardStats.imbalanceScore);

        // Display efficiency metrics
        System.out.println("\nEfficiency Metrics:");
        EfficiencyMetrics efficiency = monitor.getEfficiencyMetrics(cfs);
        System.out.printf("  Write Amplification: %.2fx%n", efficiency.writeAmplification);
        System.out.printf("  Read Amplification: %.2fx%n", efficiency.readAmplification);
        System.out.printf("  Space Amplification: %.2fx%n", efficiency.spaceAmplification);

        System.out.println("\nComparison to Other Strategies:");
        System.out.println("  vs STCS:");
        efficiency.comparisonToSTCS.forEach((metric, change) ->
            System.out.printf("    %s: %+.1f%%%n", metric, change * 100));
        System.out.println("  vs LCS:");
        efficiency.comparisonToLCS.forEach((metric, change) ->
            System.out.printf("    %s: %+.1f%%%n", metric, change * 100));
    }

    private void displayKeyspaceStatus(UCSMonitor monitor, String keyspaceName)
    {
        Keyspace ks = Keyspace.open(keyspaceName);
        System.out.printf("\nKeyspace: %s%n", keyspaceName);
        System.out.println("-".repeat(60));

        for (ColumnFamilyStore cfs : ks.getColumnFamilyStores())
        {
            displayTableStatus(monitor, keyspaceName, cfs.name);
        }
    }

    private void displayAllStatus(UCSMonitor monitor)
    {
        for (String keyspaceName : Keyspace.all())
        {
            displayKeyspaceStatus(monitor, keyspaceName);
        }
    }
}
