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
package org.apache.cassandra.cql3.query.execution;

import java.util.ArrayList;
import java.util.List;

import org.apache.cassandra.cql3.ResultSet;

/**
 * Collects query results and warnings during execution.
 */
public class ResultCollector
{
    private final List<String> warnings;
    private boolean partialResult;

    public ResultCollector()
    {
        this.warnings = new ArrayList<>();
        this.partialResult = false;
    }

    /**
     * Add a warning message.
     */
    public void addWarning(String warning)
    {
        warnings.add(warning);
    }

    /**
     * Mark results as partial.
     */
    public void setPartialResult(boolean partial)
    {
        this.partialResult = partial;
    }

    /**
     * Check if results are partial.
     */
    public boolean isPartialResult()
    {
        return partialResult;
    }

    /**
     * Get collected warnings.
     */
    public List<String> getWarnings()
    {
        return warnings;
    }

    /**
     * Get the final result set.
     * In a real implementation, this would construct an actual ResultSet.
     */
    public ResultSet getResultSet()
    {
        // Simplified - would construct actual ResultSet with results and warnings
        return null;
    }
}
