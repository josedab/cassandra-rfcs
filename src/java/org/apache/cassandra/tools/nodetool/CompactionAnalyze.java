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

import java.time.Duration;

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.unified.UCSWorkloadAnalyzer;
import org.apache.cassandra.db.compaction.unified.UCSWorkloadAnalyzer.AnalysisResult;
import org.apache.cassandra.tools.NodeProbe;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Analyze current workload and recommend UCS configuration.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
@Command(name = "compaction-analyze", description = "Analyze workload and recommend UCS configuration")
public class CompactionAnalyze extends AbstractCommand
{
    @Parameters(index = "0", description = "Keyspace name")
    private String keyspace;

    @Parameters(index = "1", description = "Table name")
    private String table;

    @Option(names = { "--duration" }, description = "Analysis duration (e.g., '24h', '7d')", defaultValue = "24h")
    private String duration;

    @Override
    public void execute(NodeProbe probe)
    {
        try
        {
            Duration analysisDuration = parseDuration(duration);

            System.out.printf("Workload Analysis for %s.%s%n", keyspace, table);
            System.out.println("=".repeat(60));
            System.out.printf("Analysis Period: %s%n%n", duration);

            // Get the ColumnFamilyStore
            Keyspace ks = Keyspace.open(keyspace);
            ColumnFamilyStore cfs = ks.getColumnFamilyStore(table);

            // Perform analysis
            UCSWorkloadAnalyzer analyzer = new UCSWorkloadAnalyzer();
            AnalysisResult result = analyzer.analyze(cfs, analysisDuration);

            // Print workload profile
            System.out.println("Workload Profile:");
            System.out.printf("  Type: %s (confidence: %.0f%%)%n",
                            result.getWorkloadType(),
                            result.getWorkloadProfile().getConfidence() * 100);
            System.out.printf("  Write Rate: %,d ops/sec%n", result.getWorkloadProfile().getWriteRate());
            System.out.printf("  Read Rate: %,d ops/sec%n", result.getWorkloadProfile().getReadRate());
            System.out.printf("  Read/Write Ratio: %.2f%n%n",
                            (double) result.getWorkloadProfile().getReadRate() /
                            Math.max(result.getWorkloadProfile().getWriteRate(), 1));

            // Print data characteristics
            System.out.println("Data Characteristics:");
            System.out.printf("  Average Partition Size: %.1f KB%n",
                            result.getWorkloadProfile().getAveragePartitionSize() / 1024.0);
            System.out.printf("  P99 Partition Size: %.1f KB%n",
                            result.getWorkloadProfile().getP99PartitionSize() / 1024.0);
            System.out.printf("  Deletion Rate: %.0f%%%n%n",
                            result.getWorkloadProfile().getDeletionRate() * 100);

            // Print current efficiency
            System.out.println("Current Efficiency:");
            System.out.printf("  Write Amplification: %.1fx%n",
                            result.getCurrentEfficiency().getWriteAmplification());
            System.out.printf("  Read Latency (P99): %.2f ms%n",
                            result.getCurrentEfficiency().getReadLatency());
            System.out.printf("  Space Amplification: %.1fx%n%n",
                            result.getCurrentEfficiency().getSpaceAmplification());

            // Print recommendation
            System.out.println("Recommended: UnifiedCompactionStrategy");
            System.out.println("  Configuration:");
            System.out.printf("    scaling_parameter: %d%n",
                            result.getRecommendedConfiguration().getScalingParameter());
            System.out.printf("    target_sstable_size: %d MB%n",
                            result.getRecommendedConfiguration().getTargetSStableSize() / (1024 * 1024));
            System.out.printf("    num_shards: %d%n%n",
                            result.getRecommendedConfiguration().getNumShards());

            // Print expected performance
            System.out.println("  Expected Performance:");
            System.out.printf("    Write Amplification: %.1f%% improvement%n",
                            result.getExpectedImprovement().getWriteAmpImprovement() * 100);
            System.out.printf("    Read Amplification: %.1f%% improvement%n",
                            result.getExpectedImprovement().getReadAmpImprovement() * 100);
            System.out.printf("    Space Amplification: %.1f%% improvement%n",
                            result.getExpectedImprovement().getSpaceAmpImprovement() * 100);

        }
        catch (Exception e)
        {
            throw new RuntimeException("Error analyzing workload: " + e.getMessage(), e);
        }
    }

    private Duration parseDuration(String duration)
    {
        String value = duration.substring(0, duration.length() - 1);
        char unit = duration.charAt(duration.length() - 1);

        long amount = Long.parseLong(value);
        return switch (unit)
        {
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'm' -> Duration.ofMinutes(amount);
            default -> throw new IllegalArgumentException("Invalid duration format: " + duration);
        };
    }
}
