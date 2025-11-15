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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for QueryCost class.
 */
public class QueryCostTest
{
    @Test
    public void testQueryCostInitialization()
    {
        QueryCost cost = new QueryCost();

        assertEquals(0, cost.getPartitionsToScan());
        assertEquals(0, cost.getRowsToScan());
        assertEquals(0, cost.getBytesToRead());
        assertEquals(0.0, cost.getNetworkCost(), 0.01);
        assertEquals(0.0, cost.getTotalCost(), 0.01);
    }

    @Test
    public void testQueryCostSetters()
    {
        QueryCost cost = new QueryCost();

        cost.setPartitionsToScan(100);
        cost.setRowsToScan(10000);
        cost.setBytesToRead(1024000);
        cost.setNetworkCost(50.0);
        cost.setTotalCost(1500.0);

        assertEquals(100, cost.getPartitionsToScan());
        assertEquals(10000, cost.getRowsToScan());
        assertEquals(1024000, cost.getBytesToRead());
        assertEquals(50.0, cost.getNetworkCost(), 0.01);
        assertEquals(1500.0, cost.getTotalCost(), 0.01);
    }

    @Test
    public void testQueryCostDescription()
    {
        QueryCost cost = new QueryCost();
        cost.setDescription("Test query cost");

        assertEquals("Test query cost", cost.getDescription());
    }

    @Test
    public void testQueryCostToString()
    {
        QueryCost cost = new QueryCost();
        cost.setPartitionsToScan(10);
        cost.setRowsToScan(100);
        cost.setDescription("Test");

        String str = cost.toString();
        assertTrue(str.contains("partitions=10"));
        assertTrue(str.contains("rows=100"));
        assertTrue(str.contains("Test"));
    }
}
