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
package org.apache.cassandra.cql3.functions.aggregate;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced aggregate functions for CQL.
 *
 * Provides string aggregations, statistical functions, conditional aggregates,
 * and approximate aggregations.
 */
public class EnhancedAggregates
{
    private static final Logger logger = LoggerFactory.getLogger(EnhancedAggregates.class);

    /**
     * String aggregation functions.
     */
    public static class StringAggregation
    {
        /**
         * GROUP_CONCAT - Concatenate strings with a separator.
         */
        public static class GroupConcat
        {
            private final StringBuilder builder;
            private String separator;
            private boolean first;

            public GroupConcat()
            {
                this.builder = new StringBuilder();
                this.separator = ",";
                this.first = true;
            }

            public void aggregate(String value, String sep)
            {
                if (sep != null)
                {
                    separator = sep;
                }
                if (value != null)
                {
                    if (!first)
                    {
                        builder.append(separator);
                    }
                    builder.append(value);
                    first = false;
                }
            }

            public String finalValue()
            {
                return builder.toString();
            }

            public void reset()
            {
                builder.setLength(0);
                first = true;
            }
        }

        /**
         * STRING_AGG - Alias for GROUP_CONCAT (PostgreSQL compatibility).
         */
        public static class StringAgg extends GroupConcat
        {
            // Inherits all behavior from GroupConcat
        }
    }

    /**
     * Statistical aggregate functions.
     */
    public static class StatisticalAggregates
    {
        /**
         * STDDEV - Standard deviation.
         */
        public static class StandardDeviation
        {
            private double sum = 0;
            private double sumSquares = 0;
            private long count = 0;

            public void aggregate(double value)
            {
                sum += value;
                sumSquares += value * value;
                count++;
            }

            public Double finalValue()
            {
                if (count < 2)
                {
                    return null;
                }

                double mean = sum / count;
                double variance = (sumSquares / count) - (mean * mean);
                return Math.sqrt(Math.max(0, variance)); // Protect against numerical errors
            }

            public void reset()
            {
                sum = 0;
                sumSquares = 0;
                count = 0;
            }
        }

        /**
         * VARIANCE - Variance.
         */
        public static class Variance
        {
            private double sum = 0;
            private double sumSquares = 0;
            private long count = 0;

            public void aggregate(double value)
            {
                sum += value;
                sumSquares += value * value;
                count++;
            }

            public Double finalValue()
            {
                if (count < 2)
                {
                    return null;
                }

                double mean = sum / count;
                return (sumSquares / count) - (mean * mean);
            }

            public void reset()
            {
                sum = 0;
                sumSquares = 0;
                count = 0;
            }
        }

        /**
         * PERCENTILE - Calculate percentile.
         */
        public static class Percentile
        {
            private final List<Double> values;

            public Percentile()
            {
                this.values = new ArrayList<>();
            }

            public void aggregate(double value)
            {
                values.add(value);
            }

            public Double finalValue(double p)
            {
                if (values.isEmpty())
                {
                    return null;
                }

                Collections.sort(values);
                int index = (int) Math.ceil(p * values.size()) - 1;
                index = Math.max(0, Math.min(index, values.size() - 1));
                return values.get(index);
            }

            public void reset()
            {
                values.clear();
            }
        }

        /**
         * MEDIAN - Calculate median (50th percentile).
         */
        public static class Median
        {
            private final List<Double> values;

            public Median()
            {
                this.values = new ArrayList<>();
            }

            public void aggregate(double value)
            {
                values.add(value);
            }

            public Double finalValue()
            {
                if (values.isEmpty())
                {
                    return null;
                }

                Collections.sort(values);
                int size = values.size();

                if (size % 2 == 0)
                {
                    // Even number of elements - average the two middle values
                    return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
                }
                else
                {
                    // Odd number of elements - return middle value
                    return values.get(size / 2);
                }
            }

            public void reset()
            {
                values.clear();
            }
        }
    }

    /**
     * Conditional aggregate functions.
     */
    public static class ConditionalAggregates
    {
        /**
         * COUNT_IF - Count rows matching a condition.
         */
        public static class CountIf
        {
            private long count = 0;

            public void aggregate(boolean condition)
            {
                if (condition)
                {
                    count++;
                }
            }

            public long finalValue()
            {
                return count;
            }

            public void reset()
            {
                count = 0;
            }
        }

        /**
         * SUM_IF - Sum values matching a condition.
         */
        public static class SumIf
        {
            private double sum = 0;

            public void aggregate(double value, boolean condition)
            {
                if (condition)
                {
                    sum += value;
                }
            }

            public double finalValue()
            {
                return sum;
            }

            public void reset()
            {
                sum = 0;
            }
        }

        /**
         * AVG_IF - Average values matching a condition.
         */
        public static class AvgIf
        {
            private double sum = 0;
            private long count = 0;

            public void aggregate(double value, boolean condition)
            {
                if (condition)
                {
                    sum += value;
                    count++;
                }
            }

            public Double finalValue()
            {
                if (count == 0)
                {
                    return null;
                }
                return sum / count;
            }

            public void reset()
            {
                sum = 0;
                count = 0;
            }
        }
    }

    /**
     * Approximate aggregate functions using probabilistic data structures.
     */
    public static class ApproximateAggregates
    {
        /**
         * APPROX_COUNT_DISTINCT - Approximate distinct count using HyperLogLog.
         */
        public static class ApproxCountDistinct
        {
            private final SimpleHyperLogLog hll;

            public ApproxCountDistinct()
            {
                this.hll = new SimpleHyperLogLog(14); // 2^14 registers
            }

            public void aggregate(Object value)
            {
                if (value != null)
                {
                    hll.offer(value);
                }
            }

            public long finalValue()
            {
                return hll.cardinality();
            }

            public void reset()
            {
                hll.reset();
            }
        }

        /**
         * APPROX_PERCENTILE - Approximate percentile calculation.
         */
        public static class ApproxPercentile
        {
            private final List<Double> samples;
            private final int maxSamples;

            public ApproxPercentile()
            {
                this.maxSamples = 10000;
                this.samples = new ArrayList<>();
            }

            public void aggregate(double value)
            {
                if (samples.size() < maxSamples)
                {
                    samples.add(value);
                }
                else
                {
                    // Reservoir sampling - replace random element
                    int index = new Random().nextInt(maxSamples);
                    samples.set(index, value);
                }
            }

            public double finalValue(double percentile)
            {
                if (samples.isEmpty())
                {
                    return 0.0;
                }

                Collections.sort(samples);
                int index = (int) (percentile * samples.size());
                index = Math.max(0, Math.min(index, samples.size() - 1));
                return samples.get(index);
            }

            public void reset()
            {
                samples.clear();
            }
        }

        /**
         * Simple HyperLogLog implementation for approximate distinct counting.
         */
        private static class SimpleHyperLogLog
        {
            private final int[] registers;
            private final int p; // precision

            public SimpleHyperLogLog(int p)
            {
                this.p = p;
                this.registers = new int[1 << p]; // 2^p registers
            }

            public void offer(Object value)
            {
                long hash = hash64(value);
                int registerIndex = (int) (hash >>> (64 - p));
                int runLength = Long.numberOfLeadingZeros((hash << p) | (1L << (p - 1))) + 1;
                registers[registerIndex] = Math.max(registers[registerIndex], runLength);
            }

            public long cardinality()
            {
                double sum = 0;
                int m = registers.length;

                for (int register : registers)
                {
                    sum += Math.pow(2, -register);
                }

                double alpha = 0.7213 / (1 + 1.079 / m);
                double estimate = alpha * m * m / sum;

                return Math.round(estimate);
            }

            public void reset()
            {
                Arrays.fill(registers, 0);
            }

            private long hash64(Object value)
            {
                // Simple hash function - in production use MurmurHash or similar
                return value.hashCode();
            }
        }
    }
}
