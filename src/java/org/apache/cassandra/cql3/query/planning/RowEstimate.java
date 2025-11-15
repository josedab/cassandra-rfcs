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
package org.apache.cassandra.cql3.query.planning;

/**
 * Represents an estimate of the number of rows that will be scanned for a query.
 */
public class RowEstimate
{
    private final long count;
    private final double selectivity;

    public RowEstimate(long count)
    {
        this(count, 1.0);
    }

    public RowEstimate(long count, double selectivity)
    {
        this.count = count;
        this.selectivity = Math.max(0.0, Math.min(1.0, selectivity));
    }

    public long getCount()
    {
        return count;
    }

    public double getSelectivity()
    {
        return selectivity;
    }

    @Override
    public String toString()
    {
        return String.format("RowEstimate{count=%d, selectivity=%.2f}", count, selectivity);
    }
}
