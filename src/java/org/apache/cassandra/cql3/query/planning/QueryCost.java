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
 * Represents the estimated cost of executing a query.
 * This includes metrics like partitions to scan, rows to scan, bytes to read,
 * and network cost.
 */
public class QueryCost
{
    private long partitionsToScan;
    private long rowsToScan;
    private long bytesToRead;
    private double networkCost;
    private double totalCost;
    private String description;

    public QueryCost()
    {
        this.partitionsToScan = 0;
        this.rowsToScan = 0;
        this.bytesToRead = 0;
        this.networkCost = 0.0;
        this.totalCost = 0.0;
        this.description = "";
    }

    public long getPartitionsToScan()
    {
        return partitionsToScan;
    }

    public void setPartitionsToScan(long partitionsToScan)
    {
        this.partitionsToScan = partitionsToScan;
    }

    public long getRowsToScan()
    {
        return rowsToScan;
    }

    public void setRowsToScan(long rowsToScan)
    {
        this.rowsToScan = rowsToScan;
    }

    public long getBytesToRead()
    {
        return bytesToRead;
    }

    public void setBytesToRead(long bytesToRead)
    {
        this.bytesToRead = bytesToRead;
    }

    public double getNetworkCost()
    {
        return networkCost;
    }

    public void setNetworkCost(double networkCost)
    {
        this.networkCost = networkCost;
    }

    public double getTotalCost()
    {
        return totalCost;
    }

    public void setTotalCost(double totalCost)
    {
        this.totalCost = totalCost;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @Override
    public String toString()
    {
        return String.format("QueryCost{partitions=%d, rows=%d, bytes=%d, network=%.2f, total=%.2f, description='%s'}",
                             partitionsToScan, rowsToScan, bytesToRead, networkCost, totalCost, description);
    }
}
