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

import org.apache.cassandra.tools.schemadesigner.model.AccessPattern;
import org.apache.cassandra.tools.schemadesigner.model.WorkloadProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Context object holding wizard state.
 */
public class WizardContext
{
    private String keyspace;
    private final List<Entity> entities;
    private List<String> queries;
    private List<AccessPattern> accessPatterns;
    private WorkloadProfile workloadProfile;
    private boolean requiresStrongConsistency;

    public WizardContext()
    {
        this.entities = new ArrayList<>();
        this.queries = new ArrayList<>();
        this.accessPatterns = new ArrayList<>();
        this.workloadProfile = new WorkloadProfile();
        this.requiresStrongConsistency = false;
    }

    public String getKeyspace() { return keyspace; }
    public void setKeyspace(String keyspace) { this.keyspace = keyspace; }

    public List<Entity> getEntities() { return entities; }
    public void addEntity(Entity entity) { entities.add(entity); }

    public List<String> getQueries() { return queries; }
    public void setQueries(List<String> queries) { this.queries = new ArrayList<>(queries); }

    public List<AccessPattern> getAccessPatterns() { return accessPatterns; }
    public void setAccessPatterns(List<AccessPattern> patterns) { this.accessPatterns = new ArrayList<>(patterns); }

    public WorkloadProfile getWorkloadProfile() { return workloadProfile; }
    public void setWorkloadProfile(WorkloadProfile profile) { this.workloadProfile = profile; }

    public boolean requiresStrongConsistency() { return requiresStrongConsistency; }
    public void setRequiresStrongConsistency(boolean requires) { this.requiresStrongConsistency = requires; }
}
