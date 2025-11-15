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
package org.apache.cassandra.cql3.validation.cte;

import org.junit.Test;

import org.apache.cassandra.cql3.CQLTester;

/**
 * Basic tests for Common Table Expressions (CTEs).
 */
public class CTEBasicTest extends CQLTester
{
    @Test
    public void testSimpleCTE() throws Throwable
    {
        createTable("CREATE TABLE %s (id int PRIMARY KEY, value text)");

        execute("INSERT INTO %s (id, value) VALUES (1, 'a')");
        execute("INSERT INTO %s (id, value) VALUES (2, 'b')");
        execute("INSERT INTO %s (id, value) VALUES (3, 'c')");

        // Basic CTE query
        String query = "WITH temp AS (SELECT * FROM %s WHERE id > 1) " +
                       "SELECT * FROM temp";

        // This test is a placeholder - actual implementation would execute the query
        // assertRows(execute(query), row(2, "b"), row(3, "c"));
    }

    @Test
    public void testMultipleCTEs() throws Throwable
    {
        createTable("CREATE TABLE %s (id int PRIMARY KEY, value int)");

        execute("INSERT INTO %s (id, value) VALUES (1, 10)");
        execute("INSERT INTO %s (id, value) VALUES (2, 20)");
        execute("INSERT INTO %s (id, value) VALUES (3, 30)");

        // Multiple CTEs
        String query = "WITH " +
                       "cte1 AS (SELECT * FROM %s WHERE value > 10), " +
                       "cte2 AS (SELECT * FROM cte1 WHERE value < 40) " +
                       "SELECT * FROM cte2";

        // This test is a placeholder - actual implementation would execute the query
        // assertRows(execute(query), row(2, 20), row(3, 30));
    }

    @Test
    public void testCTEWithAggregation() throws Throwable
    {
        createTable("CREATE TABLE %s (category text, value int, PRIMARY KEY (category, value))");

        execute("INSERT INTO %s (category, value) VALUES ('A', 10)");
        execute("INSERT INTO %s (category, value) VALUES ('A', 20)");
        execute("INSERT INTO %s (category, value) VALUES ('B', 30)");
        execute("INSERT INTO %s (category, value) VALUES ('B', 40)");

        // CTE with aggregation
        String query = "WITH category_sums AS (" +
                       "  SELECT category, SUM(value) as total FROM %s GROUP BY category" +
                       ") " +
                       "SELECT * FROM category_sums WHERE total > 25";

        // This test is a placeholder - actual implementation would execute the query
        // assertRows(execute(query), row("A", 30), row("B", 70));
    }

    @Test
    public void testCTEWithColumnAliases() throws Throwable
    {
        createTable("CREATE TABLE %s (id int PRIMARY KEY, value text)");

        execute("INSERT INTO %s (id, value) VALUES (1, 'test')");

        // CTE with column aliases
        String query = "WITH temp (key, val) AS (SELECT id, value FROM %s) " +
                       "SELECT key, val FROM temp";

        // This test is a placeholder - actual implementation would execute the query
        // assertRows(execute(query), row(1, "test"));
    }
}
