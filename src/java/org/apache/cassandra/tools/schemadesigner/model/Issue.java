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

/**
 * Represents a validation issue or warning.
 */
public class Issue
{
    private final Severity severity;
    private final String message;
    private String details;

    public Issue(Severity severity, String message)
    {
        this.severity = severity;
        this.message = message;
    }

    public Issue(Severity severity, String message, String details)
    {
        this.severity = severity;
        this.message = message;
        this.details = details;
    }

    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ").append(message);
        if (details != null)
            sb.append("\n  Details: ").append(details);
        return sb.toString();
    }
}
