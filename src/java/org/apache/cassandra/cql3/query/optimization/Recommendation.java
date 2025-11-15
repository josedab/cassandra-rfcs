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
package org.apache.cassandra.cql3.query.optimization;

import java.util.UUID;

/**
 * Base class for query optimization recommendations.
 */
public abstract class Recommendation
{
    private final UUID id;
    private final String type;
    private final Priority priority;
    private final String description;
    private final double estimatedImprovement;
    private final double confidenceScore;

    protected Recommendation(String type,
                           Priority priority,
                           String description,
                           double estimatedImprovement,
                           double confidenceScore)
    {
        this.id = UUID.randomUUID();
        this.type = type;
        this.priority = priority;
        this.description = description;
        this.estimatedImprovement = estimatedImprovement;
        this.confidenceScore = Math.max(0.0, Math.min(1.0, confidenceScore));
    }

    public UUID getId()
    {
        return id;
    }

    public String getType()
    {
        return type;
    }

    public Priority getPriority()
    {
        return priority;
    }

    public String getDescription()
    {
        return description;
    }

    public double getEstimatedImprovement()
    {
        return estimatedImprovement;
    }

    public double getConfidenceScore()
    {
        return confidenceScore;
    }

    /**
     * Get the suggested action to implement this recommendation.
     */
    public abstract String getSuggestedAction();

    @Override
    public String toString()
    {
        return String.format("Recommendation{type=%s, priority=%s, improvement=%.2f, confidence=%.2f, desc='%s'}",
                             type, priority, estimatedImprovement, confidenceScore, description);
    }

    /**
     * Priority levels for recommendations.
     */
    public enum Priority
    {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
