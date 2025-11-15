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
package org.apache.cassandra.tools.schemadesigner.analyzer;

import org.apache.cassandra.tools.schemadesigner.model.AccessPattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes queries to extract access patterns and workload characteristics.
 */
public class QueryAnalyzer
{
    private final QueryParser parser;
    private final AccessPatternExtractor extractor;
    private final FrequencyAnalyzer frequencyAnalyzer;

    public QueryAnalyzer()
    {
        this.parser = new QueryParser();
        this.extractor = new AccessPatternExtractor();
        this.frequencyAnalyzer = new FrequencyAnalyzer();
    }

    /**
     * Analyzes a list of queries and extracts access patterns.
     *
     * @param queries List of CQL queries to analyze
     * @return List of access patterns extracted from the queries
     */
    public List<AccessPattern> analyzeQueries(List<String> queries)
    {
        Map<String, AccessPattern> patterns = new HashMap<>();

        for (String query : queries)
        {
            try
            {
                ParsedQuery parsed = parser.parse(query);
                AccessPattern pattern = extractor.extract(parsed);

                // Merge similar patterns
                String patternKey = pattern.getSignature();
                if (patterns.containsKey(patternKey))
                {
                    patterns.get(patternKey).merge(pattern);
                }
                else
                {
                    patterns.put(patternKey, pattern);
                }
            }
            catch (Exception e)
            {
                System.err.println("Failed to parse query: " + query);
                System.err.println("Error: " + e.getMessage());
            }
        }

        // Analyze frequency and importance
        List<AccessPattern> patternList = new ArrayList<>(patterns.values());
        frequencyAnalyzer.analyze(patternList);

        return patternList;
    }

    /**
     * Analyzes a single query and returns its access pattern.
     *
     * @param query CQL query to analyze
     * @return Access pattern extracted from the query
     */
    public AccessPattern analyzeQuery(String query)
    {
        ParsedQuery parsed = parser.parse(query);
        return extractor.extract(parsed);
    }
}
