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

import java.util.Comparator;
import java.util.List;

/**
 * Analyzes and assigns importance scores to access patterns based on frequency.
 */
public class FrequencyAnalyzer
{
    /**
     * Analyzes access patterns and assigns relative importance scores.
     *
     * @param patterns List of access patterns to analyze
     */
    public void analyze(List<AccessPattern> patterns)
    {
        if (patterns.isEmpty())
        {
            return;
        }

        // Calculate total frequency
        long totalFrequency = patterns.stream()
            .mapToLong(AccessPattern::getEstimatedFrequency)
            .sum();

        // Normalize frequencies (if needed in future)
        // For now, patterns already have their frequencies set

        // Sort by frequency (most frequent first)
        patterns.sort(Comparator.comparingLong(AccessPattern::getEstimatedFrequency).reversed());
    }

    /**
     * Calculates the relative importance of a pattern.
     *
     * @param pattern The pattern to evaluate
     * @param totalFrequency Total frequency across all patterns
     * @return Importance score (0-100)
     */
    public double calculateImportance(AccessPattern pattern, long totalFrequency)
    {
        if (totalFrequency == 0)
        {
            return 0;
        }

        return (double) pattern.getEstimatedFrequency() / totalFrequency * 100;
    }
}
