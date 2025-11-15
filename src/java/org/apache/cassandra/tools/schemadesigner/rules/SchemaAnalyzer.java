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
package org.apache.cassandra.tools.schemadesigner.rules;

import org.apache.cassandra.tools.schemadesigner.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes schemas for anti-patterns and provides recommendations.
 */
public class SchemaAnalyzer
{
    private final AntiPatternDetector antiPatternDetector;

    public SchemaAnalyzer()
    {
        this.antiPatternDetector = new AntiPatternDetector();
    }

    /**
     * Analyzes a schema and generates a comprehensive report.
     *
     * @param schema Schema to analyze
     * @param accessPatterns Access patterns for the schema
     * @return Analysis report
     */
    public AnalysisReport analyze(Schema schema, List<AccessPattern> accessPatterns)
    {
        AnalysisReport report = new AnalysisReport();

        // Detect anti-patterns
        List<AntiPattern> antiPatterns = antiPatternDetector.detect(schema, accessPatterns);
        report.setAntiPatterns(antiPatterns);

        // Generate recommendations
        List<Recommendation> recommendations = generateRecommendations(antiPatterns, schema);
        report.setRecommendations(recommendations);

        return report;
    }

    private List<Recommendation> generateRecommendations(List<AntiPattern> antiPatterns, Schema schema)
    {
        List<Recommendation> recommendations = new ArrayList<>();

        for (AntiPattern pattern : antiPatterns)
        {
            recommendations.addAll(pattern.getRecommendations());
        }

        return recommendations;
    }

    /**
     * Analysis report containing findings and recommendations.
     */
    public static class AnalysisReport
    {
        private List<AntiPattern> antiPatterns;
        private List<Recommendation> recommendations;

        public AnalysisReport()
        {
            this.antiPatterns = new ArrayList<>();
            this.recommendations = new ArrayList<>();
        }

        public void setAntiPatterns(List<AntiPattern> patterns)
        {
            this.antiPatterns = new ArrayList<>(patterns);
        }

        public void setRecommendations(List<Recommendation> recs)
        {
            this.recommendations = new ArrayList<>(recs);
        }

        public List<AntiPattern> getAntiPatterns() { return new ArrayList<>(antiPatterns); }
        public List<Recommendation> getRecommendations() { return new ArrayList<>(recommendations); }

        public boolean hasIssues()
        {
            return !antiPatterns.isEmpty();
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Schema Analysis Report ===\n\n");

            if (antiPatterns.isEmpty())
            {
                sb.append("✓ No anti-patterns detected\n");
            }
            else
            {
                sb.append("Anti-patterns found:\n");
                for (AntiPattern pattern : antiPatterns)
                {
                    sb.append("\n").append(pattern.toString()).append("\n");
                }
            }

            if (!recommendations.isEmpty())
            {
                sb.append("\nRecommendations:\n");
                for (Recommendation rec : recommendations)
                {
                    sb.append("  • ").append(rec.getDescription()).append("\n");
                }
            }

            return sb.toString();
        }
    }

    /**
     * Represents a recommendation for schema improvement.
     */
    public static class Recommendation
    {
        private final String description;
        private final Severity severity;

        public Recommendation(String description, Severity severity)
        {
            this.description = description;
            this.severity = severity;
        }

        public String getDescription() { return description; }
        public Severity getSeverity() { return severity; }
    }
}
