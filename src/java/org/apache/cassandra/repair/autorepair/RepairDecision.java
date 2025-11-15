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

package org.apache.cassandra.repair.autorepair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.google.common.annotations.VisibleForTesting;

/**
 * Represents a repair scheduling decision with full context and reasoning.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairDecision
{
    public enum DecisionType
    {
        SCHEDULED,      // Repair will be executed
        SKIPPED,        // Repair skipped (e.g., below min interval)
        DEFERRED,       // Repair deferred due to load or conflicts
        PRIORITIZED     // Repair prioritized due to high importance
    }

    private final UUID decisionId;
    private final long timestamp;
    private final String keyspace;
    private final String table;
    private final String tokenRange;
    private final DecisionType type;
    private final String reason;
    private final double priority;
    private final long scheduledTime;
    private final Map<String, String> metadata;

    private RepairDecision(Builder builder)
    {
        this.decisionId = builder.decisionId != null ? builder.decisionId : UUID.randomUUID();
        this.timestamp = builder.timestamp;
        this.keyspace = builder.keyspace;
        this.table = builder.table;
        this.tokenRange = builder.tokenRange;
        this.type = builder.type;
        this.reason = builder.reason;
        this.priority = builder.priority;
        this.scheduledTime = builder.scheduledTime;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public UUID getDecisionId()
    {
        return decisionId;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public String getKeyspace()
    {
        return keyspace;
    }

    public String getTable()
    {
        return table;
    }

    public String getTokenRange()
    {
        return tokenRange;
    }

    public DecisionType getType()
    {
        return type;
    }

    public String getReason()
    {
        return reason;
    }

    public double getPriority()
    {
        return priority;
    }

    public long getScheduledTime()
    {
        return scheduledTime;
    }

    public Map<String, String> getMetadata()
    {
        return metadata;
    }

    public boolean shouldRepair()
    {
        return type == DecisionType.SCHEDULED || type == DecisionType.PRIORITIZED;
    }

    @Override
    public String toString()
    {
        return String.format("RepairDecision{id=%s, type=%s, keyspace=%s, table=%s, reason=%s}",
                           decisionId, type, keyspace, table, reason);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepairDecision that = (RepairDecision) o;
        return decisionId.equals(that.decisionId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(decisionId);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private UUID decisionId;
        private long timestamp;
        private String keyspace;
        private String table;
        private String tokenRange;
        private DecisionType type;
        private String reason;
        private double priority;
        private long scheduledTime;
        private Map<String, String> metadata = new HashMap<>();

        public Builder decisionId(UUID decisionId)
        {
            this.decisionId = decisionId;
            return this;
        }

        public Builder timestamp(long timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        public Builder keyspace(String keyspace)
        {
            this.keyspace = keyspace;
            return this;
        }

        public Builder table(String table)
        {
            this.table = table;
            return this;
        }

        public Builder tokenRange(String tokenRange)
        {
            this.tokenRange = tokenRange;
            return this;
        }

        public Builder type(DecisionType type)
        {
            this.type = type;
            return this;
        }

        public Builder reason(String reason)
        {
            this.reason = reason;
            return this;
        }

        public Builder priority(double priority)
        {
            this.priority = priority;
            return this;
        }

        public Builder scheduledTime(long scheduledTime)
        {
            this.scheduledTime = scheduledTime;
            return this;
        }

        public Builder addMetadata(String key, String value)
        {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata)
        {
            this.metadata.putAll(metadata);
            return this;
        }

        public RepairDecision build()
        {
            Objects.requireNonNull(type, "Decision type must be set");
            Objects.requireNonNull(reason, "Decision reason must be set");
            return new RepairDecision(this);
        }
    }
}
