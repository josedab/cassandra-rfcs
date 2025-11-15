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
package org.apache.cassandra.cql3.validation.window;

import org.junit.Test;

import org.apache.cassandra.cql3.CQLTester;

/**
 * Tests for window ranking functions (ROW_NUMBER, RANK, DENSE_RANK, etc.).
 */
public class RankingFunctionTest extends CQLTester
{
    @Test
    public void testRowNumber() throws Throwable
    {
        createTable("CREATE TABLE %s (id int, value int, PRIMARY KEY (id))");

        execute("INSERT INTO %s (id, value) VALUES (1, 100)");
        execute("INSERT INTO %s (id, value) VALUES (2, 200)");
        execute("INSERT INTO %s (id, value) VALUES (3, 150)");

        // ROW_NUMBER without partitioning
        String query = "SELECT id, value, ROW_NUMBER() OVER (ORDER BY value) as rn " +
                       "FROM %s";

        // This test is a placeholder - actual implementation would execute the query
        // Expected: (1, 100, 1), (3, 150, 2), (2, 200, 3)
    }

    @Test
    public void testRank() throws Throwable
    {
        createTable("CREATE TABLE %s (id int, category text, value int, PRIMARY KEY (id))");

        execute("INSERT INTO %s (id, category, value) VALUES (1, 'A', 100)");
        execute("INSERT INTO %s (id, category, value) VALUES (2, 'A', 100)");
        execute("INSERT INTO %s (id, category, value) VALUES (3, 'A', 200)");
        execute("INSERT INTO %s (id, category, value) VALUES (4, 'B', 150)");

        // RANK with partitioning
        String query = "SELECT id, category, value, " +
                       "RANK() OVER (PARTITION BY category ORDER BY value DESC) as rank " +
                       "FROM %s";

        // This test is a placeholder - actual implementation would execute the query
        // Expected for category A: (3, 'A', 200, 1), (1, 'A', 100, 2), (2, 'A', 100, 2)
    }

    @Test
    public void testDenseRank() throws Throwable
    {
        createTable("CREATE TABLE %s (id int, value int, PRIMARY KEY (id))");

        execute("INSERT INTO %s (id, value) VALUES (1, 100)");
        execute("INSERT INTO %s (id, value) VALUES (2, 100)");
        execute("INSERT INTO %s (id, value) VALUES (3, 200)");
        execute("INSERT INTO %s (id, value) VALUES (4, 300)");

        // DENSE_RANK
        String query = "SELECT id, value, " +
                       "DENSE_RANK() OVER (ORDER BY value) as dense_rank " +
                       "FROM %s";

        // This test is a placeholder - actual implementation would execute the query
        // Expected: (1, 100, 1), (2, 100, 1), (3, 200, 2), (4, 300, 3)
    }

    @Test
    public void testPercentRank() throws Throwable
    {
        createTable("CREATE TABLE %s (id int, value int, PRIMARY KEY (id))");

        execute("INSERT INTO %s (id, value) VALUES (1, 10)");
        execute("INSERT INTO %s (id, value) VALUES (2, 20)");
        execute("INSERT INTO %s (id, value) VALUES (3, 30)");
        execute("INSERT INTO %s (id, value) VALUES (4, 40)");

        // PERCENT_RANK
        String query = "SELECT id, value, " +
                       "PERCENT_RANK() OVER (ORDER BY value) as percent_rank " +
                       "FROM %s";

        // This test is a placeholder - actual implementation would execute the query
        // Expected: values from 0.0 to 1.0 based on position
    }

    @Test
    public void testNTile() throws Throwable
    {
        createTable("CREATE TABLE %s (id int, value int, PRIMARY KEY (id))");

        for (int i = 1; i <= 10; i++)
        {
            execute("INSERT INTO %s (id, value) VALUES (?, ?)", i, i * 10);
        }

        // NTILE - divide into 4 buckets
        String query = "SELECT id, value, " +
                       "NTILE(4) OVER (ORDER BY value) as quartile " +
                       "FROM %s";

        // This test is a placeholder - actual implementation would execute the query
        // Expected: rows divided into 4 groups
    }
}
