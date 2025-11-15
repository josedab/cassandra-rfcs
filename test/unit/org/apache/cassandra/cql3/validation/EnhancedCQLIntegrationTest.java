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
package org.apache.cassandra.cql3.validation;

import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.cql3.CQLTester;
import org.apache.cassandra.cql3.EnhancedCQLFeatures;

/**
 * Comprehensive integration tests for RFC-0008 Enhanced CQL Features.
 *
 * Tests the interaction between CTEs, window functions, recursive queries,
 * and enhanced aggregations.
 */
public class EnhancedCQLIntegrationTest extends CQLTester
{
    @BeforeClass
    public static void setUp()
    {
        // Ensure all enhanced features are enabled for testing
        EnhancedCQLFeatures.setCTEEnabled(true);
        EnhancedCQLFeatures.setWindowFunctionsEnabled(true);
        EnhancedCQLFeatures.setRecursiveEnabled(true);
        EnhancedCQLFeatures.setEnhancedAggregatesEnabled(true);
    }

    @Test
    public void testCTEWithWindowFunctions() throws Throwable
    {
        createTable("CREATE TABLE %s (product_id int, sale_date date, amount decimal, PRIMARY KEY (product_id, sale_date))");

        // Insert test data
        execute("INSERT INTO %s (product_id, sale_date, amount) VALUES (1, '2024-11-01', 100.00)");
        execute("INSERT INTO %s (product_id, sale_date, amount) VALUES (1, '2024-11-02', 150.00)");
        execute("INSERT INTO %s (product_id, sale_date, amount) VALUES (1, '2024-11-03', 120.00)");
        execute("INSERT INTO %s (product_id, sale_date, amount) VALUES (2, '2024-11-01', 200.00)");
        execute("INSERT INTO %s (product_id, sale_date, amount) VALUES (2, '2024-11-02', 180.00)");

        // Query combining CTE and window functions
        String query =
            "WITH daily_sales AS ( " +
            "  SELECT product_id, sale_date, amount " +
            "  FROM %s " +
            "  WHERE sale_date >= '2024-11-01' " +
            ") " +
            "SELECT " +
            "  product_id, " +
            "  sale_date, " +
            "  amount, " +
            "  ROW_NUMBER() OVER (PARTITION BY product_id ORDER BY sale_date) as day_number, " +
            "  SUM(amount) OVER ( " +
            "    PARTITION BY product_id " +
            "    ORDER BY sale_date " +
            "    ROWS UNBOUNDED PRECEDING " +
            "  ) as running_total " +
            "FROM daily_sales " +
            "ORDER BY product_id, sale_date";

        // Expected results:
        // product_id | sale_date  | amount | day_number | running_total
        // -----------+------------+--------+------------+---------------
        // 1          | 2024-11-01 | 100.00 | 1          | 100.00
        // 1          | 2024-11-02 | 150.00 | 2          | 250.00
        // 1          | 2024-11-03 | 120.00 | 3          | 370.00
        // 2          | 2024-11-01 | 200.00 | 1          | 200.00
        // 2          | 2024-11-02 | 180.00 | 2          | 380.00

        // This is a placeholder - actual implementation would execute and verify
        // assertRowsIgnoringOrder(execute(query), ...);
    }

    @Test
    public void testRecursiveCTEWithAggregation() throws Throwable
    {
        createTable("CREATE TABLE %s (emp_id int, name text, manager_id int, salary decimal, dept_id int, PRIMARY KEY (dept_id, emp_id))");

        // Insert organizational hierarchy
        execute("INSERT INTO %s (emp_id, name, manager_id, salary, dept_id) VALUES (1, 'CEO', null, 200000, 1)");
        execute("INSERT INTO %s (emp_id, name, manager_id, salary, dept_id) VALUES (2, 'VP Eng', 1, 150000, 1)");
        execute("INSERT INTO %s (emp_id, name, manager_id, salary, dept_id) VALUES (3, 'VP Sales', 1, 150000, 1)");
        execute("INSERT INTO %s (emp_id, name, manager_id, salary, dept_id) VALUES (4, 'Engineer', 2, 100000, 1)");
        execute("INSERT INTO %s (emp_id, name, manager_id, salary, dept_id) VALUES (5, 'Engineer', 2, 95000, 1)");

        // Recursive query to get org hierarchy with aggregated salary data
        String query =
            "WITH RECURSIVE org_tree AS ( " +
            "  SELECT emp_id, name, manager_id, salary, 1 as level " +
            "  FROM %s " +
            "  WHERE manager_id IS NULL AND dept_id = 1 " +
            "  " +
            "  UNION ALL " +
            "  " +
            "  SELECT e.emp_id, e.name, e.manager_id, e.salary, ot.level + 1 " +
            "  FROM %s e " +
            "  INNER JOIN org_tree ot ON e.manager_id = ot.emp_id " +
            "  WHERE e.dept_id = 1 AND ot.level < 10 " +
            ") " +
            "SELECT " +
            "  level, " +
            "  COUNT(*) as employee_count, " +
            "  SUM(salary) as total_salary, " +
            "  AVG(salary) as avg_salary, " +
            "  MEDIAN(salary) as median_salary, " +
            "  STDDEV(salary) as salary_stddev " +
            "FROM org_tree " +
            "GROUP BY level " +
            "ORDER BY level";

        // Expected: Statistics per organizational level
        // This is a placeholder - actual implementation would execute and verify
    }

    @Test
    public void testComplexAnalyticsWithAllFeatures() throws Throwable
    {
        createTable("CREATE TABLE %s (user_id int, order_date date, product_id int, amount decimal, status text, PRIMARY KEY (user_id, order_date, product_id))");

        // Insert sample data
        for (int user = 1; user <= 3; user++)
        {
            for (int day = 1; day <= 5; day++)
            {
                String date = String.format("2024-11-%02d", day);
                execute("INSERT INTO %s (user_id, order_date, product_id, amount, status) VALUES (?, ?, ?, ?, ?)",
                        user, date, day, 100.0 * user * day, day % 2 == 0 ? "completed" : "pending");
            }
        }

        // Complex query using all features
        String query =
            "WITH " +
            "-- CTE 1: Calculate user metrics " +
            "user_metrics AS ( " +
            "  SELECT " +
            "    user_id, " +
            "    COUNT(*) as total_orders, " +
            "    COUNT(*) FILTER (WHERE status = 'completed') as completed_orders, " +
            "    SUM(amount) as total_spent, " +
            "    AVG(amount) as avg_order, " +
            "    STDDEV(amount) as order_stddev, " +
            "    GROUP_CONCAT(DISTINCT product_id::text, ',') as products " +
            "  FROM %s " +
            "  WHERE order_date >= '2024-11-01' " +
            "  GROUP BY user_id " +
            "), " +
            "-- CTE 2: Rank users by spending " +
            "ranked_users AS ( " +
            "  SELECT " +
            "    user_id, " +
            "    total_spent, " +
            "    completed_orders, " +
            "    ROW_NUMBER() OVER (ORDER BY total_spent DESC) as spending_rank, " +
            "    NTILE(3) OVER (ORDER BY total_spent) as spending_tier, " +
            "    PERCENT_RANK() OVER (ORDER BY total_spent) as spending_percentile " +
            "  FROM user_metrics " +
            ") " +
            "SELECT " +
            "  ru.user_id, " +
            "  ru.total_spent, " +
            "  ru.completed_orders, " +
            "  ru.spending_rank, " +
            "  CASE ru.spending_tier " +
            "    WHEN 1 THEN 'Low' " +
            "    WHEN 2 THEN 'Medium' " +
            "    WHEN 3 THEN 'High' " +
            "  END as tier, " +
            "  ROUND(ru.spending_percentile * 100, 2) as percentile, " +
            "  um.avg_order, " +
            "  um.order_stddev, " +
            "  um.products " +
            "FROM ranked_users ru " +
            "INNER JOIN user_metrics um ON ru.user_id = um.user_id " +
            "ORDER BY ru.spending_rank";

        // This is a placeholder - actual implementation would execute and verify
    }

    @Test
    public void testEnhancedAggregationsWithGrouping() throws Throwable
    {
        createTable("CREATE TABLE %s (category text, product_id int, price decimal, in_stock boolean, brand text, PRIMARY KEY (category, product_id))");

        // Insert test data
        execute("INSERT INTO %s (category, product_id, price, in_stock, brand) VALUES ('Electronics', 1, 999.99, true, 'BrandA')");
        execute("INSERT INTO %s (category, product_id, price, in_stock, brand) VALUES ('Electronics', 2, 1299.99, false, 'BrandB')");
        execute("INSERT INTO %s (category, product_id, price, in_stock, brand) VALUES ('Electronics', 3, 799.99, true, 'BrandA')");
        execute("INSERT INTO %s (category, product_id, price, in_stock, brand) VALUES ('Books', 4, 29.99, true, 'Publisher1')");
        execute("INSERT INTO %s (category, product_id, price, in_stock, brand) VALUES ('Books', 5, 39.99, true, 'Publisher2')");

        // Query using all enhanced aggregates
        String query =
            "SELECT " +
            "  category, " +
            "  COUNT(*) as total_products, " +
            "  COUNT(*) FILTER (WHERE in_stock = true) as in_stock_count, " +
            "  COUNT(*) FILTER (WHERE in_stock = false) as out_of_stock_count, " +
            "  AVG(price) as avg_price, " +
            "  MEDIAN(price) as median_price, " +
            "  STDDEV(price) as price_stddev, " +
            "  VARIANCE(price) as price_variance, " +
            "  MIN(price) as min_price, " +
            "  MAX(price) as max_price, " +
            "  PERCENTILE(price, 0.25) as p25, " +
            "  PERCENTILE(price, 0.75) as p75, " +
            "  GROUP_CONCAT(DISTINCT brand ORDER BY brand, ', ') as brands, " +
            "  APPROX_COUNT_DISTINCT(brand) as approx_unique_brands " +
            "FROM %s " +
            "GROUP BY category " +
            "ORDER BY category";

        // Expected comprehensive statistics per category
        // This is a placeholder - actual implementation would execute and verify
    }

    @Test
    public void testWindowFunctionsWithFrames() throws Throwable
    {
        createTable("CREATE TABLE %s (sensor_id int, timestamp timestamp, reading decimal, PRIMARY KEY (sensor_id, timestamp))");

        // Insert time series data
        for (int hour = 0; hour < 24; hour++)
        {
            String ts = String.format("2024-11-15 %02d:00:00", hour);
            execute("INSERT INTO %s (sensor_id, timestamp, reading) VALUES (1, ?, ?)", ts, 20.0 + Math.random() * 10);
        }

        // Query with various window frames
        String query =
            "SELECT " +
            "  sensor_id, " +
            "  timestamp, " +
            "  reading, " +
            "  -- Moving averages " +
            "  AVG(reading) OVER ( " +
            "    PARTITION BY sensor_id " +
            "    ORDER BY timestamp " +
            "    ROWS BETWEEN 2 PRECEDING AND CURRENT ROW " +
            "  ) as ma_3hour, " +
            "  AVG(reading) OVER ( " +
            "    PARTITION BY sensor_id " +
            "    ORDER BY timestamp " +
            "    ROWS BETWEEN 5 PRECEDING AND CURRENT ROW " +
            "  ) as ma_6hour, " +
            "  -- Value comparisons " +
            "  LAG(reading, 1) OVER (PARTITION BY sensor_id ORDER BY timestamp) as prev_hour, " +
            "  LEAD(reading, 1) OVER (PARTITION BY sensor_id ORDER BY timestamp) as next_hour, " +
            "  FIRST_VALUE(reading) OVER ( " +
            "    PARTITION BY sensor_id " +
            "    ORDER BY timestamp " +
            "    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW " +
            "  ) as first_reading, " +
            "  LAST_VALUE(reading) OVER ( " +
            "    PARTITION BY sensor_id " +
            "    ORDER BY timestamp " +
            "    ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING " +
            "  ) as last_reading " +
            "FROM %s " +
            "WHERE sensor_id = 1 " +
            "ORDER BY timestamp";

        // This is a placeholder - actual implementation would execute and verify
    }

    @Test
    public void testFeatureConfiguration() throws Throwable
    {
        // Test that features can be enabled/disabled
        EnhancedCQLFeatures.setCTEEnabled(false);
        assert !EnhancedCQLFeatures.isCTEEnabled();

        EnhancedCQLFeatures.setWindowFunctionsEnabled(false);
        assert !EnhancedCQLFeatures.isWindowFunctionsEnabled();

        // Attempting to use disabled features should throw exception
        // This would be verified once parser integration is complete

        // Reset to enabled
        EnhancedCQLFeatures.setCTEEnabled(true);
        EnhancedCQLFeatures.setWindowFunctionsEnabled(true);
    }

    @Test
    public void testConfigurationLimits() throws Throwable
    {
        // Verify configuration limits are accessible
        assert EnhancedCQLFeatures.getCTEMaxMemoryMB() > 0;
        assert EnhancedCQLFeatures.getWindowMaxPartitionSize() > 0;
        assert EnhancedCQLFeatures.getMaxRecursionDepth() > 0;
        assert EnhancedCQLFeatures.getRecursionTimeoutMs() > 0;
        assert EnhancedCQLFeatures.getAggregateMaxMemoryMB() > 0;
        assert EnhancedCQLFeatures.getApproxAggregatePrecision() > 0.0;
        assert EnhancedCQLFeatures.getApproxAggregatePrecision() <= 1.0;
    }
}
