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

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Report summarizing repair effectiveness over a time period.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class EffectivenessReport
{
    private final Duration period;
    private final long totalBytesRepaired;
    private final long totalBytesValidated;
    private final double averageEfficiency;
    private final Map<String, List<RepairMetricEntry>> repairsByKeyspace;
    private final List<Double> discrepancyTrend;
    private final List<String> recommendations;

    private EffectivenessReport(Builder builder)
    {
        this.period = builder.period;
        this.totalBytesRepaired = builder.totalBytesRepaired;
        this.totalBytesValidated = builder.totalBytesValidated;
        this.averageEfficiency = builder.averageEfficiency;
        this.repairsByKeyspace = builder.repairsByKeyspace;
        this.discrepancyTrend = builder.discrepancyTrend;
        this.recommendations = builder.recommendations;
    }

    public Duration getPeriod()
    {
        return period;
    }

    public long getTotalBytesRepaired()
    {
        return totalBytesRepaired;
    }

    public long getTotalBytesValidated()
    {
        return totalBytesValidated;
    }

    public double getAverageEfficiency()
    {
        return averageEfficiency;
    }

    public Map<String, List<RepairMetricEntry>> getRepairsByKeyspace()
    {
        return repairsByKeyspace;
    }

    public List<Double> getDiscrepancyTrend()
    {
        return discrepancyTrend;
    }

    public List<String> getRecommendations()
    {
        return recommendations;
    }

    @Override
    public String toString()
    {
        return String.format("EffectivenessReport{period=%s, repaired=%d bytes, validated=%d bytes, efficiency=%.2f%%}",
                           period, totalBytesRepaired, totalBytesValidated, averageEfficiency * 100);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private Duration period;
        private long totalBytesRepaired;
        private long totalBytesValidated;
        private double averageEfficiency;
        private Map<String, List<RepairMetricEntry>> repairsByKeyspace;
        private List<Double> discrepancyTrend;
        private List<String> recommendations;

        public Builder period(Duration period)
        {
            this.period = period;
            return this;
        }

        public Builder totalBytesRepaired(long totalBytesRepaired)
        {
            this.totalBytesRepaired = totalBytesRepaired;
            return this;
        }

        public Builder totalBytesValidated(long totalBytesValidated)
        {
            this.totalBytesValidated = totalBytesValidated;
            return this;
        }

        public Builder averageEfficiency(double averageEfficiency)
        {
            this.averageEfficiency = averageEfficiency;
            return this;
        }

        public Builder repairsByKeyspace(Map<String, List<RepairMetricEntry>> repairsByKeyspace)
        {
            this.repairsByKeyspace = repairsByKeyspace;
            return this;
        }

        public Builder discrepancyTrend(List<Double> discrepancyTrend)
        {
            this.discrepancyTrend = discrepancyTrend;
            return this;
        }

        public Builder recommendations(List<String> recommendations)
        {
            this.recommendations = recommendations;
            return this;
        }

        public EffectivenessReport build()
        {
            return new EffectivenessReport(this);
        }
    }
}
