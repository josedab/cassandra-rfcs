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
package org.apache.cassandra.io.storage.tier;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Storage policy for a table, defining how data should be distributed across tiers.
 */
public class TableStoragePolicy
{
    private final String policyClass;
    private final List<TierRule> tierRules;
    private final String promotionPolicy;
    private final String demotionPolicy;
    private final Map<String, String> properties;

    private TableStoragePolicy(Builder builder)
    {
        this.policyClass = builder.policyClass;
        this.tierRules = builder.tierRules;
        this.promotionPolicy = builder.promotionPolicy;
        this.demotionPolicy = builder.demotionPolicy;
        this.properties = builder.properties;
    }

    public String getPolicyClass()
    {
        return policyClass;
    }

    public List<TierRule> getTierRules()
    {
        return Collections.unmodifiableList(tierRules);
    }

    public String getPromotionPolicy()
    {
        return promotionPolicy;
    }

    public String getDemotionPolicy()
    {
        return demotionPolicy;
    }

    public Map<String, String> getProperties()
    {
        return Collections.unmodifiableMap(properties);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Rule for tier placement.
     */
    public static class TierRule
    {
        private final String tierName;
        private final long maxAge;
        private final int minAccessCount;
        private final Map<String, String> conditions;

        public TierRule(String tierName, long maxAge, int minAccessCount, Map<String, String> conditions)
        {
            this.tierName = tierName;
            this.maxAge = maxAge;
            this.minAccessCount = minAccessCount;
            this.conditions = conditions != null ? conditions : Collections.emptyMap();
        }

        public String getTierName()
        {
            return tierName;
        }

        public long getMaxAge()
        {
            return maxAge;
        }

        public int getMinAccessCount()
        {
            return minAccessCount;
        }

        public Map<String, String> getConditions()
        {
            return conditions;
        }

        /**
         * Check if this rule matches the given criteria.
         *
         * @param metadata SSTable metadata
         * @param ageMillis age of the SSTable in milliseconds
         * @return true if the rule matches
         */
        public boolean matches(Object metadata, long ageMillis)
        {
            // Simple age-based matching
            return ageMillis <= maxAge;
        }

        @Override
        public String toString()
        {
            return String.format("TierRule{tier=%s, maxAge=%dms, minAccess=%d}",
                               tierName, maxAge, minAccessCount);
        }
    }

    /**
     * Builder for TableStoragePolicy.
     */
    public static class Builder
    {
        private String policyClass = "TieredStoragePolicy";
        private List<TierRule> tierRules = new ArrayList<>();
        private String promotionPolicy = "access_frequency";
        private String demotionPolicy = "age_based";
        private Map<String, String> properties = new HashMap<>();

        public Builder policyClass(String policyClass)
        {
            this.policyClass = policyClass;
            return this;
        }

        public Builder addTierRule(String tierName, long duration, TimeUnit unit, int minAccessCount)
        {
            long durationMs = unit.toMillis(duration);
            tierRules.add(new TierRule(tierName, durationMs, minAccessCount, null));
            return this;
        }

        public Builder addTierRule(TierRule rule)
        {
            tierRules.add(rule);
            return this;
        }

        public Builder promotionPolicy(String promotionPolicy)
        {
            this.promotionPolicy = promotionPolicy;
            return this;
        }

        public Builder demotionPolicy(String demotionPolicy)
        {
            this.demotionPolicy = demotionPolicy;
            return this;
        }

        public Builder property(String key, String value)
        {
            properties.put(key, value);
            return this;
        }

        public Builder properties(Map<String, String> properties)
        {
            this.properties.putAll(properties);
            return this;
        }

        public TableStoragePolicy build()
        {
            return new TableStoragePolicy(this);
        }
    }

    /**
     * Create a default tiered storage policy.
     *
     * @return default policy with hot/warm/cold tiers
     */
    public static TableStoragePolicy createDefault()
    {
        return builder()
            .addTierRule("hot", 7, TimeUnit.DAYS, 10)
            .addTierRule("warm", 30, TimeUnit.DAYS, 1)
            .addTierRule("cold", 365, TimeUnit.DAYS, 0)
            .build();
    }

    @Override
    public String toString()
    {
        return String.format("TableStoragePolicy{class=%s, rules=%d, promotion=%s, demotion=%s}",
                           policyClass, tierRules.size(), promotionPolicy, demotionPolicy);
    }
}
