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

import org.apache.cassandra.tools.schemadesigner.model.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan for rolling back a migration.
 */
public class RollbackPlan
{
    private final List<MigrationStep> steps;
    private Schema restoreSchema;

    public RollbackPlan()
    {
        this.steps = new ArrayList<>();
    }

    public void addStep(MigrationStep step)
    {
        steps.add(step);
    }

    public List<MigrationStep> getSteps()
    {
        return new ArrayList<>(steps);
    }

    public void setRestoreSchema(Schema schema)
    {
        this.restoreSchema = schema;
    }

    public Schema getRestoreSchema()
    {
        return restoreSchema;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Rollback Plan ===\n\n");
        sb.append("Steps to rollback:\n");
        for (int i = 0; i < steps.size(); i++)
        {
            sb.append(String.format("%d. %s\n", i + 1, steps.get(i)));
        }
        return sb.toString();
    }
}
