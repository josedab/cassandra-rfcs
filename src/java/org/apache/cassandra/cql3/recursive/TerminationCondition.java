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
package org.apache.cassandra.cql3.recursive;

/**
 * Condition for terminating recursive query execution.
 *
 * Allows early termination of recursion based on row values or depth.
 */
public interface TerminationCondition
{
    /**
     * Check if recursion should terminate for a given row.
     *
     * @param row the current row being processed
     * @param depth the current recursion depth
     * @return true if recursion should stop for this row
     */
    boolean shouldTerminate(RecursiveQueryProcessor.Row row, int depth);

    /**
     * Default termination condition that never terminates early.
     */
    TerminationCondition NEVER = (row, depth) -> false;

    /**
     * Termination condition based on maximum depth.
     */
    static TerminationCondition maxDepth(int maxDepth)
    {
        return (row, depth) -> depth >= maxDepth;
    }

    /**
     * Termination condition based on a column value.
     */
    static TerminationCondition columnValue(String column, Object value)
    {
        return (row, depth) -> {
            Object columnValue = row.getValue(column);
            return columnValue != null && columnValue.equals(value);
        };
    }

    /**
     * Termination condition based on null column value.
     */
    static TerminationCondition columnIsNull(String column)
    {
        return (row, depth) -> row.getValue(column) == null;
    }
}
