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

import java.util.HashMap;
import java.util.Map;

/**
 * Schema recommendations based on workload analysis.
 */
public class SchemaRecommendation
{
    private String compactionStrategy;
    private Map<String, String> compactionParameters;
    private boolean rowCacheEnabled;
    private String cachingPolicy;
    private int replicationFactor;

    public SchemaRecommendation()
    {
        this.compactionParameters = new HashMap<>();
        this.rowCacheEnabled = false;
        this.cachingPolicy = "KEYS_ONLY";
        this.replicationFactor = 3;
    }

    public void setCompactionStrategy(String strategy)
    {
        this.compactionStrategy = strategy;
    }

    public void addParameter(String key, String value)
    {
        compactionParameters.put(key, value);
    }

    public void enableRowCache(boolean enabled)
    {
        this.rowCacheEnabled = enabled;
    }

    public void setCachingPolicy(String policy)
    {
        this.cachingPolicy = policy;
    }

    public void setReplicationFactor(int factor)
    {
        this.replicationFactor = factor;
    }

    public String getCompactionStrategy() { return compactionStrategy; }
    public Map<String, String> getCompactionParameters() { return new HashMap<>(compactionParameters); }
    public boolean isRowCacheEnabled() { return rowCacheEnabled; }
    public String getCachingPolicy() { return cachingPolicy; }
    public int getReplicationFactor() { return replicationFactor; }
}
