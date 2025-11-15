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

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for a storage tier.
 */
public class TierConfiguration
{
    private final String tierClass;
    private final long capacity;
    private final long maxAge;
    private final double costPerGB;
    private final double accessCostPerGB;
    private final Map<String, String> additionalParams;

    private TierConfiguration(Builder builder)
    {
        this.tierClass = builder.tierClass;
        this.capacity = builder.capacity;
        this.maxAge = builder.maxAge;
        this.costPerGB = builder.costPerGB;
        this.accessCostPerGB = builder.accessCostPerGB;
        this.additionalParams = builder.additionalParams;
    }

    public String getTierClass()
    {
        return tierClass;
    }

    public long getCapacity()
    {
        return capacity;
    }

    public long getMaxAge()
    {
        return maxAge;
    }

    public double getCostPerGB()
    {
        return costPerGB;
    }

    public double getAccessCostPerGB()
    {
        return accessCostPerGB;
    }

    public Map<String, String> getAdditionalParams()
    {
        return additionalParams;
    }

    public String getParam(String key)
    {
        return additionalParams.get(key);
    }

    public String getParam(String key, String defaultValue)
    {
        return additionalParams.getOrDefault(key, defaultValue);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private String tierClass;
        private long capacity = Long.MAX_VALUE;
        private long maxAge = TimeUnit.DAYS.toMillis(365);
        private double costPerGB = 0.0;
        private double accessCostPerGB = 0.0;
        private Map<String, String> additionalParams = Map.of();

        public Builder tierClass(String tierClass)
        {
            this.tierClass = tierClass;
            return this;
        }

        public Builder capacity(long capacity)
        {
            this.capacity = capacity;
            return this;
        }

        public Builder maxAge(long maxAge, TimeUnit unit)
        {
            this.maxAge = unit.toMillis(maxAge);
            return this;
        }

        public Builder costPerGB(double costPerGB)
        {
            this.costPerGB = costPerGB;
            return this;
        }

        public Builder accessCostPerGB(double accessCostPerGB)
        {
            this.accessCostPerGB = accessCostPerGB;
            return this;
        }

        public Builder additionalParams(Map<String, String> additionalParams)
        {
            this.additionalParams = additionalParams;
            return this;
        }

        public TierConfiguration build()
        {
            return new TierConfiguration(this);
        }
    }
}
