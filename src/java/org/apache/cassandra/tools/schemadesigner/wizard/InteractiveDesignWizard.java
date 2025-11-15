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
package org.apache.cassandra.tools.schemadesigner.wizard;

import org.apache.cassandra.tools.schemadesigner.analyzer.QueryAnalyzer;
import org.apache.cassandra.tools.schemadesigner.generator.SchemaGenerator;
import org.apache.cassandra.tools.schemadesigner.model.*;
import org.apache.cassandra.tools.schemadesigner.rules.SchemaAnalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive wizard for guided schema design.
 */
public class InteractiveDesignWizard
{
    private final BufferedReader reader;
    private final QueryAnalyzer queryAnalyzer;
    private final SchemaGenerator schemaGenerator;
    private final SchemaAnalyzer schemaAnalyzer;
    private final WizardContext context;

    public InteractiveDesignWizard()
    {
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.queryAnalyzer = new QueryAnalyzer();
        this.schemaGenerator = new SchemaGenerator();
        this.schemaAnalyzer = new SchemaAnalyzer();
        this.context = new WizardContext();
    }

    /**
     * Runs the interactive wizard.
     *
     * @return Generated schema
     */
    public Schema run() throws IOException
    {
        printWelcome();

        // Step 1: Gather basic information
        gatherBasicInfo();

        // Step 2: Define entities
        defineEntities();

        // Step 3: Define queries
        defineQueries();

        // Step 4: Specify requirements
        specifyRequirements();

        // Step 5: Generate and refine schema
        Schema schema = generateAndRefine();

        // Step 6: Validate and explain
        validateAndExplain(schema);

        return schema;
    }

    private void printWelcome()
    {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Cassandra Interactive Schema Designer         ║");
        System.out.println("║   Query-First Schema Design Made Easy           ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("This wizard will guide you through designing an optimal");
        System.out.println("Cassandra schema based on your queries and requirements.");
        System.out.println();
    }

    private void gatherBasicInfo() throws IOException
    {
        System.out.println("=== Step 1: Basic Information ===\n");

        // Application type
        System.out.println("What type of application is this?");
        System.out.println("1. Web application");
        System.out.println("2. Mobile application");
        System.out.println("3. IoT/Time-series");
        System.out.println("4. Analytics");
        System.out.println("5. Other");
        int appType = readChoice(1, 5);

        // Keyspace name
        System.out.print("\nKeyspace name: ");
        String keyspace = reader.readLine().trim();
        context.setKeyspace(keyspace.isEmpty() ? "my_keyspace" : keyspace);

        // Expected load
        System.out.print("\nExpected read QPS (queries per second): ");
        int readQPS = readInt(100);

        System.out.print("Expected write QPS: ");
        int writeQPS = readInt(50);

        WorkloadProfile workload = new WorkloadProfile();
        workload.setExpectedQPS(readQPS + writeQPS);
        workload.setPeakQPS((readQPS + writeQPS) * 3);

        // Calculate read/write ratio
        int total = readQPS + writeQPS;
        workload.setQueryDistribution(WorkloadProfile.QueryType.READ, (readQPS * 100) / total);
        workload.setQueryDistribution(WorkloadProfile.QueryType.WRITE, (writeQPS * 100) / total);

        // Data pattern
        if (appType == 3) // IoT/Time-series
        {
            workload.setDataPattern(WorkloadProfile.DataPattern.TIME_SERIES);
        }

        context.setWorkloadProfile(workload);

        System.out.println("\n✓ Basic information gathered\n");
    }

    private void defineEntities() throws IOException
    {
        System.out.println("=== Step 2: Define Entities ===\n");

        System.out.println("Let's define the main entities in your data model.");
        System.out.println("Examples: users, orders, products, sessions, etc.\n");

        boolean addMore = true;
        while (addMore)
        {
            System.out.print("Entity name (or 'done' to continue): ");
            String entityName = reader.readLine().trim();

            if (entityName.equalsIgnoreCase("done"))
            {
                break;
            }

            if (entityName.isEmpty())
            {
                continue;
            }

            Entity entity = new Entity(entityName);

            // Define fields
            System.out.println("Define fields for " + entityName + " (comma-separated):");
            System.out.print("Example: id, name, email, created_at\n> ");
            String fieldsInput = reader.readLine().trim();

            if (!fieldsInput.isEmpty())
            {
                String[] fields = fieldsInput.split(",");
                for (String field : fields)
                {
                    entity.addField(field.trim());
                }
            }

            context.addEntity(entity);
            System.out.println("✓ Entity '" + entityName + "' added\n");
        }

        if (context.getEntities().isEmpty())
        {
            System.out.println("No entities defined. Creating a default 'data' entity.");
            context.addEntity(new Entity("data"));
        }

        System.out.println("✓ " + context.getEntities().size() + " entities defined\n");
    }

    private void defineQueries() throws IOException
    {
        System.out.println("=== Step 3: Define Queries ===\n");

        System.out.println("Now let's define your application queries.");
        System.out.println("You can enter queries manually or load from a file.\n");

        System.out.println("1. Enter queries manually");
        System.out.println("2. Load from file");
        int choice = readChoice(1, 2);

        List<String> queries = new ArrayList<>();

        if (choice == 1)
        {
            // Manual entry
            System.out.println("\nEnter your CQL SELECT queries (one per line).");
            System.out.println("Type 'done' when finished.\n");

            while (true)
            {
                System.out.print("> ");
                String query = reader.readLine().trim();

                if (query.equalsIgnoreCase("done"))
                {
                    break;
                }

                if (!query.isEmpty())
                {
                    queries.add(query);
                    System.out.println("  ✓ Query added");
                }
            }
        }
        else
        {
            // Load from file
            System.out.print("\nQuery file path: ");
            String filePath = reader.readLine().trim();

            try
            {
                queries = Files.readAllLines(Paths.get(filePath));
                queries.removeIf(q -> q.trim().isEmpty() || q.trim().startsWith("#"));
                System.out.println("✓ Loaded " + queries.size() + " queries from file");
            }
            catch (IOException e)
            {
                System.err.println("Error reading file: " + e.getMessage());
                System.out.println("Continuing with no queries...");
            }
        }

        context.setQueries(queries);
        System.out.println("\n✓ " + queries.size() + " queries defined\n");
    }

    private void specifyRequirements() throws IOException
    {
        System.out.println("=== Step 4: Additional Requirements ===\n");

        // Data retention
        System.out.print("Data retention period (days, 0 for unlimited): ");
        int retention = readInt(0);
        context.getWorkloadProfile().setRetentionDays(retention);

        // Consistency requirements
        System.out.println("\nConsistency requirements:");
        System.out.println("1. Eventual consistency (best performance)");
        System.out.println("2. Strong consistency (quorum reads/writes)");
        int consistency = readChoice(1, 2);
        context.setRequiresStrongConsistency(consistency == 2);

        System.out.println("\n✓ Requirements specified\n");
    }

    private Schema generateAndRefine() throws IOException
    {
        System.out.println("=== Step 5: Schema Generation ===\n");

        System.out.println("Analyzing queries and generating schema...");

        // Analyze queries
        List<AccessPattern> patterns = queryAnalyzer.analyzeQueries(context.getQueries());
        context.setAccessPatterns(patterns);

        System.out.println("✓ Identified " + patterns.size() + " access patterns");

        // Generate schema
        Schema schema = schemaGenerator.generateSchema(patterns, context.getWorkloadProfile());
        schema.setKeyspace(context.getKeyspace());

        // Set replication
        schema.getReplicationConfig().put("class", "NetworkTopologyStrategy");
        schema.getReplicationConfig().put("dc1", "3");

        System.out.println("✓ Generated schema with " + schema.getTables().size() + " tables");

        // Preview schema
        System.out.println("\n--- Schema Preview ---");
        System.out.println(schema.toCQL());
        System.out.println("---------------------\n");

        // Offer refinement
        System.out.print("Accept this schema? (y/n): ");
        String accept = reader.readLine().trim();

        if (!accept.equalsIgnoreCase("y"))
        {
            System.out.println("\nTo refine the schema, please:");
            System.out.println("1. Modify your queries to better express access patterns");
            System.out.println("2. Re-run the wizard");
            System.out.println("\nUsing current schema for now...");
        }

        return schema;
    }

    private void validateAndExplain(Schema schema) throws IOException
    {
        System.out.println("\n=== Step 6: Validation & Recommendations ===\n");

        System.out.println("Analyzing schema for potential issues...");

        SchemaAnalyzer.AnalysisReport report = schemaAnalyzer.analyze(schema, context.getAccessPatterns());

        System.out.println("\n" + report);

        // Save option
        System.out.print("\nSave schema to file? (y/n): ");
        String save = reader.readLine().trim();

        if (save.equalsIgnoreCase("y"))
        {
            System.out.print("Output file path: ");
            String outputPath = reader.readLine().trim();

            if (outputPath.isEmpty())
            {
                outputPath = "schema.cql";
            }

            try
            {
                Files.writeString(Paths.get(outputPath), schema.toCQL());
                System.out.println("✓ Schema saved to: " + outputPath);
            }
            catch (IOException e)
            {
                System.err.println("Error saving file: " + e.getMessage());
            }
        }

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║   Schema Design Complete!                        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    private int readChoice(int min, int max) throws IOException
    {
        while (true)
        {
            System.out.print("Choice [" + min + "-" + max + "]: ");
            try
            {
                int choice = Integer.parseInt(reader.readLine().trim());
                if (choice >= min && choice <= max)
                {
                    return choice;
                }
                System.out.println("Please enter a number between " + min + " and " + max);
            }
            catch (NumberFormatException e)
            {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private int readInt(int defaultValue) throws IOException
    {
        try
        {
            String input = reader.readLine().trim();
            return input.isEmpty() ? defaultValue : Integer.parseInt(input);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}
