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
package org.apache.cassandra.tools.schemadesigner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validation containing issues found.
 */
public class ValidationResult
{
    private final List<Issue> issues;

    public ValidationResult()
    {
        this.issues = new ArrayList<>();
    }

    public ValidationResult(List<Issue> issues)
    {
        this.issues = new ArrayList<>(issues);
    }

    public void addIssue(Issue issue)
    {
        issues.add(issue);
    }

    public boolean isValid()
    {
        return issues.stream().noneMatch(i -> i.getSeverity() == Severity.ERROR || i.getSeverity() == Severity.CRITICAL);
    }

    public boolean hasWarnings()
    {
        return issues.stream().anyMatch(i -> i.getSeverity() == Severity.WARNING);
    }

    public List<Issue> getIssues() { return new ArrayList<>(issues); }

    public List<Issue> getErrors()
    {
        return issues.stream()
            .filter(i -> i.getSeverity() == Severity.ERROR || i.getSeverity() == Severity.CRITICAL)
            .toList();
    }

    public List<Issue> getWarnings()
    {
        return issues.stream()
            .filter(i -> i.getSeverity() == Severity.WARNING)
            .toList();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Result: ").append(isValid() ? "PASS" : "FAIL").append("\n");
        if (!issues.isEmpty())
        {
            sb.append("Issues found:\n");
            for (Issue issue : issues)
            {
                sb.append("  ").append(issue).append("\n");
            }
        }
        return sb.toString();
    }
}
