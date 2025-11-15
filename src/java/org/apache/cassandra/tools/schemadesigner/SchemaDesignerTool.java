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
package org.apache.cassandra.tools.schemadesigner;

import org.apache.cassandra.tools.schemadesigner.analyzer.QueryAnalyzer;
import org.apache.cassandra.tools.schemadesigner.generator.SchemaGenerator;
import org.apache.cassandra.tools.schemadesigner.model.*;
import org.apache.cassandra.tools.schemadesigner.rules.SchemaAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Cassandra Schema Designer Tool.
 *
 * Usage:
 *   cassandra-schema-designer create --queries queries.txt --output schema.cql
 *   cassandra-schema-designer analyze --schema schema.cql --queries queries.txt
 *   cassandra-schema-designer validate --schema schema.cql --queries queries.txt
 */
public class SchemaDesignerTool
{
    private final QueryAnalyzer queryAnalyzer;
    private final SchemaGenerator schemaGenerator;
    private final SchemaAnalyzer schemaAnalyzer;

    public SchemaDesignerTool()
    {
        this.queryAnalyzer = new QueryAnalyzer();
        this.schemaGenerator = new SchemaGenerator();
        this.schemaAnalyzer = new SchemaAnalyzer();
    }

    public static void main(String[] args)
    {
        if (args.length < 1)
        {
            printUsage();
            System.exit(1);
        }

        SchemaDesignerTool tool = new SchemaDesignerTool();

        try
        {
            String command = args[0];

            switch (command.toLowerCase())
            {
                case "create":
                    tool.handleCreate(args);
                    break;
                case "analyze":
                    tool.handleAnalyze(args);
                    break;
                case "validate":
                    tool.handleValidate(args);
                    break;
                case "help":
                case "--help":
                case "-h":
                    printUsage();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        }
        catch (Exception e)
        {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void handleCreate(String[] args) throws IOException
    {
        System.out.println("=== Cassandra Schema Designer - Create Mode ===\n");

        String queriesFile = getArgumentValue(args, "--queries");
        String outputFile = getArgumentValue(args, "--output", "schema.cql");

        if (queriesFile == null)
        {
            System.err.println("Error: --queries argument is required");
            System.exit(1);
        }

        // Read queries from file
        List<String> queries = readQueriesFromFile(queriesFile);
        System.out.println("Loaded " + queries.size() + " queries from " + queriesFile);

        // Analyze queries
        System.out.println("\nAnalyzing queries...");
        List<AccessPattern> patterns = queryAnalyzer.analyzeQueries(queries);
        System.out.println("Identified " + patterns.size() + " unique access patterns");

        // Create workload profile (could be from config file or command line)
        WorkloadProfile workload = createDefaultWorkloadProfile();

        // Generate schema
        System.out.println("\nGenerating schema...");
        Schema schema = schemaGenerator.generateSchema(patterns, workload);
        schema.setKeyspace("my_keyspace");

        // Set default replication
        schema.getReplicationConfig().put("class", "NetworkTopologyStrategy");
        schema.getReplicationConfig().put("dc1", "3");

        // Write to file
        String cql = schema.toCQL();
        Files.writeString(Paths.get(outputFile), cql);

        System.out.println("\n✓ Schema generated successfully!");
        System.out.println("  Output: " + outputFile);
        System.out.println("  Tables: " + schema.getTables().size());
        System.out.println("  Views: " + schema.getViews().size());
        System.out.println("  Indexes: " + schema.getIndexes().size());

        // Analyze generated schema
        System.out.println("\nAnalyzing generated schema for potential issues...");
        SchemaAnalyzer.AnalysisReport report = schemaAnalyzer.analyze(schema, patterns);

        if (report.hasIssues())
        {
            System.out.println("\n" + report);
        }
        else
        {
            System.out.println("✓ No issues detected");
        }
    }

    private void handleAnalyze(String[] args) throws IOException
    {
        System.out.println("=== Cassandra Schema Designer - Analyze Mode ===\n");

        String queriesFile = getArgumentValue(args, "--queries");

        if (queriesFile == null)
        {
            System.err.println("Error: --queries argument is required");
            System.exit(1);
        }

        // Read queries
        List<String> queries = readQueriesFromFile(queriesFile);
        System.out.println("Loaded " + queries.size() + " queries\n");

        // Analyze queries
        List<AccessPattern> patterns = queryAnalyzer.analyzeQueries(queries);

        // Create a minimal schema for analysis
        WorkloadProfile workload = createDefaultWorkloadProfile();
        Schema schema = schemaGenerator.generateSchema(patterns, workload);
        schema.setKeyspace("analysis_keyspace");

        // Perform analysis
        SchemaAnalyzer.AnalysisReport report = schemaAnalyzer.analyze(schema, patterns);

        // Print report
        System.out.println(report);
    }

    private void handleValidate(String[] args) throws IOException
    {
        System.out.println("=== Cassandra Schema Designer - Validate Mode ===\n");

        String queriesFile = getArgumentValue(args, "--queries");

        if (queriesFile == null)
        {
            System.err.println("Error: --queries argument is required");
            System.exit(1);
        }

        // Read and validate queries
        List<String> queries = readQueriesFromFile(queriesFile);
        System.out.println("Validating " + queries.size() + " queries...\n");

        int validQueries = 0;
        int invalidQueries = 0;

        for (int i = 0; i < queries.size(); i++)
        {
            String query = queries.get(i);
            try
            {
                AccessPattern pattern = queryAnalyzer.analyzeQuery(query);
                ValidationResult result = pattern.validate();

                if (!result.isValid())
                {
                    System.out.println("Query " + (i + 1) + ": " + query);
                    System.out.println(result);
                    invalidQueries++;
                }
                else if (result.hasWarnings())
                {
                    System.out.println("Query " + (i + 1) + " (warnings): " + query);
                    System.out.println(result);
                    validQueries++;
                }
                else
                {
                    validQueries++;
                }
            }
            catch (Exception e)
            {
                System.err.println("Query " + (i + 1) + " (parse error): " + query);
                System.err.println("  Error: " + e.getMessage());
                invalidQueries++;
            }
        }

        System.out.println("\n=== Validation Summary ===");
        System.out.println("Valid queries: " + validQueries);
        System.out.println("Invalid queries: " + invalidQueries);
        System.out.println("Total: " + queries.size());
    }

    private List<String> readQueriesFromFile(String filename) throws IOException
    {
        Path path = Paths.get(filename);
        List<String> queries = new ArrayList<>();

        for (String line : Files.readAllLines(path))
        {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//"))
            {
                queries.add(line);
            }
        }

        return queries;
    }

    private WorkloadProfile createDefaultWorkloadProfile()
    {
        WorkloadProfile profile = new WorkloadProfile();
        profile.setExpectedQPS(1000);
        profile.setPeakQPS(5000);
        profile.setDataPattern(WorkloadProfile.DataPattern.RANDOM);
        profile.setRetentionDays(90);
        return profile;
    }

    private String getArgumentValue(String[] args, String key)
    {
        return getArgumentValue(args, key, null);
    }

    private String getArgumentValue(String[] args, String key, String defaultValue)
    {
        for (int i = 0; i < args.length - 1; i++)
        {
            if (args[i].equals(key))
            {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static void printUsage()
    {
        System.out.println("Cassandra Schema Designer Tool");
        System.out.println("===============================");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  cassandra-schema-designer <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  create    Generate schema from query patterns");
        System.out.println("  analyze   Analyze queries and detect anti-patterns");
        System.out.println("  validate  Validate queries against best practices");
        System.out.println("  help      Show this help message");
        System.out.println();
        System.out.println("Create options:");
        System.out.println("  --queries <file>    File containing CQL queries (required)");
        System.out.println("  --output <file>     Output schema file (default: schema.cql)");
        System.out.println();
        System.out.println("Analyze options:");
        System.out.println("  --queries <file>    File containing CQL queries (required)");
        System.out.println();
        System.out.println("Validate options:");
        System.out.println("  --queries <file>    File containing CQL queries (required)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  cassandra-schema-designer create --queries app-queries.txt --output schema.cql");
        System.out.println("  cassandra-schema-designer analyze --queries app-queries.txt");
        System.out.println("  cassandra-schema-designer validate --queries app-queries.txt");
    }
}
