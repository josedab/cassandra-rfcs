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
package org.apache.cassandra.tools.schemadesigner.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a phase in a migration plan.
 */
public class MigrationPhase
{
    private final String name;
    private final String description;
    private final List<MigrationStep> steps;
    private final Duration estimatedDuration;
    private final boolean requiresDowntime;

    public MigrationPhase(String name, String description, List<MigrationStep> steps,
                         Duration estimatedDuration, boolean requiresDowntime)
    {
        this.name = name;
        this.description = description;
        this.steps = new ArrayList<>(steps);
        this.estimatedDuration = estimatedDuration;
        this.requiresDowntime = requiresDowntime;
    }

    public void addStep(MigrationStep step)
    {
        steps.add(step);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<MigrationStep> getSteps() { return new ArrayList<>(steps); }
    public Duration getEstimatedDuration() { return estimatedDuration; }
    public boolean requiresDowntime() { return requiresDowntime; }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Phase: ").append(name).append("\n");
        sb.append("Description: ").append(description).append("\n");
        sb.append("Steps:\n");
        for (MigrationStep step : steps)
        {
            sb.append("  - ").append(step).append("\n");
        }
        return sb.toString();
    }
}
