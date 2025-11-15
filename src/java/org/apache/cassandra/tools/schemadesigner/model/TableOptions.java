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
 * Represents table options (compaction, compression, etc).
 */
public class TableOptions
{
    private String compactionStrategy;
    private Map<String, String> compactionParams;
    private String compression;
    private double bloomFilterFpChance;
    private String caching;

    public TableOptions()
    {
        this.compactionStrategy = "UnifiedCompactionStrategy";
        this.compactionParams = new HashMap<>();
        this.compression = "LZ4";
        this.bloomFilterFpChance = 0.01;
        this.caching = "KEYS_ONLY";
    }

    public void setCompactionStrategy(String strategy)
    {
        this.compactionStrategy = strategy;
    }

    public void addCompactionParameter(String key, String value)
    {
        compactionParams.put(key, value);
    }

    public void setCompression(String compression)
    {
        this.compression = compression;
    }

    public void setBloomFilterFpChance(double chance)
    {
        this.bloomFilterFpChance = chance;
    }

    public void setCaching(String caching)
    {
        this.caching = caching;
    }

    public String getCompactionStrategy() { return compactionStrategy; }
    public String getCompression() { return compression; }
    public double getBloomFilterFpChance() { return bloomFilterFpChance; }
    public String getCaching() { return caching; }

    public String toCQL()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("WITH ");

        // Compaction
        sb.append("compaction = {'class': '").append(compactionStrategy).append("'");
        for (Map.Entry<String, String> entry : compactionParams.entrySet())
        {
            sb.append(", '").append(entry.getKey()).append("': '").append(entry.getValue()).append("'");
        }
        sb.append("}");

        // Compression
        sb.append("\n  AND compression = {'class': '").append(compression).append("Compressor'}");

        // Bloom filter
        sb.append("\n  AND bloom_filter_fp_chance = ").append(bloomFilterFpChance);

        // Caching
        sb.append("\n  AND caching = {'keys': '").append(caching).append("'}");

        return sb.toString();
    }
}
