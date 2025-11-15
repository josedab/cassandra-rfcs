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

import org.apache.cassandra.tools.schemadesigner.model.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Assessment of migration risks.
 */
public class RiskAssessment
{
    private final List<RiskAssessor.Risk> risks;

    public RiskAssessment()
    {
        this.risks = new ArrayList<>();
    }

    public void addRisk(RiskAssessor.Risk risk)
    {
        risks.add(risk);
    }

    public List<RiskAssessor.Risk> getRisks()
    {
        return new ArrayList<>(risks);
    }

    public boolean hasRisks()
    {
        return !risks.isEmpty();
    }

    public boolean hasHighRisks()
    {
        return risks.stream().anyMatch(r -> r.getSeverity() == Severity.HIGH || r.getSeverity() == Severity.CRITICAL);
    }

    @Override
    public String toString()
    {
        if (risks.isEmpty())
        {
            return "No risks identified";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Risk Assessment ===\n\n");
        for (RiskAssessor.Risk risk : risks)
        {
            sb.append(risk).append("\n\n");
        }
        return sb.toString();
    }
}
