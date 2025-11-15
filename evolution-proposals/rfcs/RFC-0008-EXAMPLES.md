# RFC-0008 Enhanced CQL Features - Examples and Usage Guide

This document provides comprehensive examples of using the Enhanced CQL Features introduced in RFC-0008.

## Table of Contents

1. [Common Table Expressions (CTEs)](#common-table-expressions-ctes)
2. [Window Functions](#window-functions)
3. [Recursive Queries](#recursive-queries)
4. [Enhanced Aggregations](#enhanced-aggregations)
5. [Combined Features](#combined-features)
6. [Performance Tips](#performance-tips)

---

## Common Table Expressions (CTEs)

CTEs allow you to define temporary named result sets that can be referenced in your query.

### Example 1: Simple CTE for Query Organization

```sql
-- Find users with above-average order totals
WITH user_totals AS (
    SELECT user_id, SUM(amount) as total
    FROM orders
    WHERE order_date >= '2024-01-01'
    GROUP BY user_id
),
avg_total AS (
    SELECT AVG(total) as avg_value
    FROM user_totals
)
SELECT ut.user_id, ut.total
FROM user_totals ut, avg_total at
WHERE ut.total > at.avg_value;
```

### Example 2: Multiple CTEs for Complex Analysis

```sql
-- E-commerce sales analysis with multiple CTEs
WITH
-- Step 1: Get recent orders
recent_orders AS (
    SELECT user_id, product_id, amount, order_date
    FROM orders
    WHERE order_date >= '2024-11-01'
),
-- Step 2: Calculate user spending
user_spending AS (
    SELECT user_id, SUM(amount) as total_spent, COUNT(*) as order_count
    FROM recent_orders
    GROUP BY user_id
),
-- Step 3: Identify top products
top_products AS (
    SELECT product_id, COUNT(*) as sales_count
    FROM recent_orders
    GROUP BY product_id
    ORDER BY sales_count DESC
    LIMIT 10
)
-- Main query: Combine insights
SELECT
    us.user_id,
    us.total_spent,
    us.order_count,
    us.total_spent / us.order_count as avg_order_value
FROM user_spending us
WHERE us.total_spent > 1000
ORDER BY us.total_spent DESC;
```

### Example 3: CTE with Column Aliases

```sql
WITH monthly_stats (month, revenue, order_count) AS (
    SELECT
        DATE_TRUNC('month', order_date),
        SUM(amount),
        COUNT(*)
    FROM orders
    WHERE order_date >= '2024-01-01'
    GROUP BY DATE_TRUNC('month', order_date)
)
SELECT month, revenue, order_count, revenue / order_count as avg_order
FROM monthly_stats
ORDER BY month;
```

---

## Window Functions

Window functions perform calculations across a set of rows related to the current row.

### Example 1: Ranking Functions

```sql
-- Rank products by sales within each category
SELECT
    category,
    product_id,
    product_name,
    sales_count,
    ROW_NUMBER() OVER (PARTITION BY category ORDER BY sales_count DESC) as row_num,
    RANK() OVER (PARTITION BY category ORDER BY sales_count DESC) as rank,
    DENSE_RANK() OVER (PARTITION BY category ORDER BY sales_count DESC) as dense_rank,
    PERCENT_RANK() OVER (PARTITION BY category ORDER BY sales_count DESC) as percentile
FROM product_sales
WHERE month = '2024-11';
```

**Output Example:**
```
category | product_id | product_name | sales_count | row_num | rank | dense_rank | percentile
---------+------------+--------------+-------------+---------+------+------------+-----------
Electronics | P001 | Laptop | 150 | 1 | 1 | 1 | 0.0
Electronics | P002 | Phone | 150 | 2 | 1 | 1 | 0.0
Electronics | P003 | Tablet | 120 | 3 | 3 | 2 | 0.667
Books | B001 | Novel | 200 | 1 | 1 | 1 | 0.0
```

### Example 2: Moving Averages

```sql
-- Calculate moving average for sensor data
SELECT
    sensor_id,
    timestamp,
    temperature,
    -- 5-row moving average
    AVG(temperature) OVER (
        PARTITION BY sensor_id
        ORDER BY timestamp
        ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
    ) as moving_avg_5,
    -- 10-row moving average
    AVG(temperature) OVER (
        PARTITION BY sensor_id
        ORDER BY timestamp
        ROWS BETWEEN 9 PRECEDING AND CURRENT ROW
    ) as moving_avg_10
FROM sensor_readings
WHERE reading_date = '2024-11-15';
```

### Example 3: LAG and LEAD for Comparisons

```sql
-- Year-over-year revenue comparison
SELECT
    month,
    revenue,
    LAG(revenue, 12) OVER (ORDER BY month) as revenue_last_year,
    revenue - LAG(revenue, 12) OVER (ORDER BY month) as yoy_change,
    CASE
        WHEN LAG(revenue, 12) OVER (ORDER BY month) IS NOT NULL
        THEN (revenue - LAG(revenue, 12) OVER (ORDER BY month)) * 100.0 / LAG(revenue, 12) OVER (ORDER BY month)
        ELSE NULL
    END as yoy_change_pct
FROM monthly_revenue
WHERE account_id = 'ACC123'
ORDER BY month;
```

### Example 4: Running Totals

```sql
-- Calculate cumulative sales
SELECT
    order_date,
    order_id,
    amount,
    SUM(amount) OVER (
        PARTITION BY customer_id
        ORDER BY order_date
        ROWS UNBOUNDED PRECEDING
    ) as running_total,
    COUNT(*) OVER (
        PARTITION BY customer_id
        ORDER BY order_date
        ROWS UNBOUNDED PRECEDING
    ) as order_sequence
FROM orders
WHERE customer_id = 'CUST456'
ORDER BY order_date;
```

### Example 5: NTILE for Quartiles

```sql
-- Divide customers into quartiles based on spending
SELECT
    customer_id,
    total_spent,
    NTILE(4) OVER (ORDER BY total_spent) as spending_quartile
FROM customer_lifetime_value
WHERE year = 2024;
```

---

## Recursive Queries

Recursive CTEs allow querying hierarchical or graph-like data structures.

### Example 1: Organization Hierarchy

```sql
-- Get complete organization chart starting from CEO
WITH RECURSIVE org_chart AS (
    -- Anchor: Start with CEO
    SELECT
        employee_id,
        name,
        title,
        manager_id,
        1 as level,
        name as path
    FROM employees
    WHERE manager_id IS NULL AND company_id = 'COMP001'

    UNION ALL

    -- Recursive: Add direct reports
    SELECT
        e.employee_id,
        e.name,
        e.title,
        e.manager_id,
        oc.level + 1,
        oc.path || ' > ' || e.name
    FROM employees e
    INNER JOIN org_chart oc ON e.manager_id = oc.employee_id
    WHERE e.company_id = 'COMP001' AND oc.level < 10
)
SELECT
    employee_id,
    name,
    title,
    level,
    path
FROM org_chart
ORDER BY path;
```

### Example 2: Bill of Materials (Parts Hierarchy)

```sql
-- Get all components needed to build a product
WITH RECURSIVE parts_tree AS (
    -- Anchor: Top-level product
    SELECT
        product_id,
        component_id,
        component_name,
        quantity,
        1 as level
    FROM product_components
    WHERE product_id = 'PROD123'

    UNION ALL

    -- Recursive: Get sub-components
    SELECT
        pc.product_id,
        pc.component_id,
        pc.component_name,
        pt.quantity * pc.quantity as quantity,
        pt.level + 1
    FROM product_components pc
    INNER JOIN parts_tree pt ON pc.product_id = pt.component_id
    WHERE pt.level < 20
)
SELECT
    component_id,
    component_name,
    SUM(quantity) as total_quantity,
    MAX(level) as deepest_level
FROM parts_tree
GROUP BY component_id, component_name
ORDER BY component_name;
```

### Example 3: Social Network - Friends of Friends

```sql
-- Find all connections up to 3 degrees of separation
WITH RECURSIVE connections AS (
    -- Anchor: Direct friends
    SELECT
        user_id,
        friend_id,
        1 as degree
    FROM friendships
    WHERE user_id = 'USER123'

    UNION ALL

    -- Recursive: Friends of friends
    SELECT
        c.user_id,
        f.friend_id,
        c.degree + 1
    FROM connections c
    INNER JOIN friendships f ON c.friend_id = f.user_id
    WHERE c.degree < 3
)
SELECT DISTINCT
    friend_id,
    MIN(degree) as shortest_path
FROM connections
GROUP BY friend_id
ORDER BY shortest_path, friend_id;
```

### Example 4: Category Tree Navigation

```sql
-- Get all subcategories under a parent category
WITH RECURSIVE category_tree AS (
    -- Anchor: Starting category
    SELECT
        category_id,
        category_name,
        parent_category_id,
        0 as depth
    FROM categories
    WHERE category_id = 'CAT_ELECTRONICS'

    UNION ALL

    -- Recursive: Child categories
    SELECT
        c.category_id,
        c.category_name,
        c.parent_category_id,
        ct.depth + 1
    FROM categories c
    INNER JOIN category_tree ct ON c.parent_category_id = ct.category_id
    WHERE ct.depth < 5
)
SELECT
    category_id,
    category_name,
    depth,
    REPEAT('  ', depth) || category_name as indented_name
FROM category_tree
ORDER BY category_id;
```

---

## Enhanced Aggregations

New aggregate functions for statistical analysis, string operations, and conditional aggregation.

### Example 1: Statistical Aggregates

```sql
-- Comprehensive statistics on product prices
SELECT
    category,
    COUNT(*) as product_count,
    AVG(price) as avg_price,
    STDDEV(price) as price_stddev,
    VARIANCE(price) as price_variance,
    MEDIAN(price) as median_price,
    MIN(price) as min_price,
    MAX(price) as max_price,
    PERCENTILE(price, 0.25) as p25,
    PERCENTILE(price, 0.75) as p75,
    PERCENTILE(price, 0.95) as p95
FROM products
WHERE active = true
GROUP BY category;
```

### Example 2: String Aggregation

```sql
-- Concatenate product features
SELECT
    product_id,
    product_name,
    GROUP_CONCAT(feature_name ORDER BY feature_name) as features_comma,
    GROUP_CONCAT(feature_name, ' | ' ORDER BY feature_name) as features_pipe,
    STRING_AGG(DISTINCT category, ', ') as categories
FROM product_features
GROUP BY product_id, product_name;
```

**Output:**
```
product_id | product_name | features_comma | features_pipe | categories
-----------+--------------+----------------+---------------+------------
P001 | Laptop | Bluetooth,USB-C,WiFi | Bluetooth | USB-C | WiFi | Electronics
```

### Example 3: Conditional Aggregates with FILTER

```sql
-- Sales analysis with conditional counting
SELECT
    region,
    date,
    COUNT(*) as total_orders,
    COUNT(*) FILTER (WHERE status = 'completed') as completed_orders,
    COUNT(*) FILTER (WHERE status = 'cancelled') as cancelled_orders,
    COUNT(*) FILTER (WHERE status = 'pending') as pending_orders,
    SUM(amount) as total_revenue,
    SUM(amount) FILTER (WHERE status = 'completed') as completed_revenue,
    AVG(amount) FILTER (WHERE status = 'completed') as avg_completed_order
FROM orders
WHERE date >= '2024-11-01'
GROUP BY region, date;
```

### Example 4: COUNT_IF and SUM_IF

```sql
-- Customer segmentation
SELECT
    customer_segment,
    COUNT_IF(annual_revenue > 100000) as high_value_count,
    COUNT_IF(annual_revenue BETWEEN 10000 AND 100000) as medium_value_count,
    COUNT_IF(annual_revenue < 10000) as low_value_count,
    SUM_IF(annual_revenue, annual_revenue > 100000) as high_value_revenue,
    AVG_IF(annual_revenue, annual_revenue > 100000) as avg_high_value_revenue
FROM customers
GROUP BY customer_segment;
```

### Example 5: Approximate Aggregates for Large Datasets

```sql
-- Efficient distinct counting on large datasets
SELECT
    event_date,
    event_type,
    COUNT(*) as total_events,
    COUNT(DISTINCT user_id) as exact_unique_users,
    APPROX_COUNT_DISTINCT(user_id) as approx_unique_users,
    APPROX_PERCENTILE(session_duration, 0.50) as median_session_approx,
    APPROX_PERCENTILE(session_duration, 0.95) as p95_session_approx
FROM user_events
WHERE event_date >= '2024-11-01'
GROUP BY event_date, event_type;
```

---

## Combined Features

Examples using multiple enhanced features together.

### Example 1: CTE + Window Functions

```sql
-- Sales ranking with trend analysis
WITH daily_sales AS (
    SELECT
        sale_date,
        product_id,
        SUM(quantity) as units_sold,
        SUM(amount) as revenue
    FROM sales
    WHERE sale_date >= '2024-11-01'
    GROUP BY sale_date, product_id
)
SELECT
    sale_date,
    product_id,
    units_sold,
    revenue,
    ROW_NUMBER() OVER (PARTITION BY sale_date ORDER BY revenue DESC) as daily_rank,
    LAG(revenue, 1) OVER (PARTITION BY product_id ORDER BY sale_date) as prev_day_revenue,
    revenue - LAG(revenue, 1) OVER (PARTITION BY product_id ORDER BY sale_date) as revenue_change,
    AVG(revenue) OVER (
        PARTITION BY product_id
        ORDER BY sale_date
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ) as moving_avg_7day
FROM daily_sales
ORDER BY sale_date, daily_rank;
```

### Example 2: Recursive CTE + Aggregation

```sql
-- Department budget rollup
WITH RECURSIVE dept_hierarchy AS (
    SELECT
        dept_id,
        dept_name,
        parent_dept_id,
        budget,
        1 as level
    FROM departments
    WHERE parent_dept_id IS NULL

    UNION ALL

    SELECT
        d.dept_id,
        d.dept_name,
        d.parent_dept_id,
        d.budget,
        dh.level + 1
    FROM departments d
    INNER JOIN dept_hierarchy dh ON d.parent_dept_id = dh.dept_id
    WHERE dh.level < 10
)
SELECT
    dept_id,
    dept_name,
    level,
    budget as dept_budget,
    SUM(budget) as total_budget_with_subdepts
FROM dept_hierarchy
GROUP BY dept_id, dept_name, level, budget
ORDER BY level, dept_id;
```

### Example 3: CTE + Window + Enhanced Aggregates

```sql
-- Comprehensive customer analytics
WITH customer_orders AS (
    SELECT
        customer_id,
        order_date,
        order_id,
        amount,
        status
    FROM orders
    WHERE order_date >= '2024-01-01'
),
customer_stats AS (
    SELECT
        customer_id,
        COUNT(*) as total_orders,
        COUNT(*) FILTER (WHERE status = 'completed') as completed_orders,
        SUM(amount) as total_spent,
        AVG(amount) as avg_order_value,
        STDDEV(amount) as order_value_stddev,
        MEDIAN(amount) as median_order_value,
        STRING_AGG(DISTINCT EXTRACT(MONTH FROM order_date)::text, ',') as active_months
    FROM customer_orders
    GROUP BY customer_id
)
SELECT
    cs.customer_id,
    cs.total_orders,
    cs.completed_orders,
    cs.total_spent,
    cs.avg_order_value,
    cs.order_value_stddev,
    cs.median_order_value,
    cs.active_months,
    NTILE(10) OVER (ORDER BY cs.total_spent DESC) as spending_decile,
    PERCENT_RANK() OVER (ORDER BY cs.total_spent) as spending_percentile,
    RANK() OVER (ORDER BY cs.total_spent DESC) as spending_rank
FROM customer_stats cs
WHERE cs.total_orders >= 3
ORDER BY cs.total_spent DESC;
```

---

## Performance Tips

### 1. CTE Best Practices

```sql
-- ✅ GOOD: CTE filters early, reducing materialized data
WITH recent_active_users AS (
    SELECT user_id, last_login
    FROM users
    WHERE last_login >= '2024-11-01' AND status = 'active'
)
SELECT * FROM recent_active_users;

-- ❌ AVOID: CTE materializes all data, then filters
WITH all_users AS (
    SELECT user_id, last_login, status
    FROM users
)
SELECT * FROM all_users
WHERE last_login >= '2024-11-01' AND status = 'active';
```

### 2. Window Function Optimization

```sql
-- ✅ GOOD: Single window definition reused
SELECT
    product_id,
    AVG(price) OVER w as avg_price,
    MAX(price) OVER w as max_price,
    MIN(price) OVER w as min_price
FROM products
WINDOW w AS (PARTITION BY category ORDER BY created_date);

-- ⚠️ ACCEPTABLE but less efficient: Repeated window definitions
SELECT
    product_id,
    AVG(price) OVER (PARTITION BY category ORDER BY created_date) as avg_price,
    MAX(price) OVER (PARTITION BY category ORDER BY created_date) as max_price
FROM products;
```

### 3. Recursive Query Limits

```sql
-- ✅ GOOD: Explicit depth limit in WHERE clause
WITH RECURSIVE tree AS (
    SELECT id, parent_id, 0 as depth FROM nodes WHERE id = 1
    UNION ALL
    SELECT n.id, n.parent_id, t.depth + 1
    FROM nodes n
    INNER JOIN tree t ON n.parent_id = t.id
    WHERE t.depth < 5  -- Explicit limit
)
SELECT * FROM tree;
```

### 4. Approximate vs. Exact Aggregates

```sql
-- For large datasets (millions of rows), use approximate aggregates
SELECT
    category,
    APPROX_COUNT_DISTINCT(user_id) as approx_users,  -- Fast, ~1-2% error
    APPROX_PERCENTILE(price, 0.95) as approx_p95     -- Fast, acceptable accuracy
FROM large_events_table
GROUP BY category;

-- For small datasets or when exactness is critical, use exact aggregates
SELECT
    category,
    COUNT(DISTINCT user_id) as exact_users,          -- Slower but exact
    PERCENTILE(price, 0.95) as exact_p95             -- Slower but exact
FROM small_products_table
GROUP BY category;
```

---

## Configuration

Enable/disable features via `cassandra.yaml`:

```yaml
enhanced_cql:
  enable_cte: true
  enable_window_functions: true
  enable_recursive: false  # Enable only when needed
  enable_enhanced_aggregates: true

  # Memory limits
  cte_max_memory_mb: 256
  window_max_partition_size: 100000
  max_recursion_depth: 100
```

For more information, see:
- RFC-0008-enhanced-cql-features.md (Design document)
- RFC-0008-IMPLEMENTATION.md (Implementation details)
- RFC-0008-GRAMMAR-EXTENSIONS.md (Grammar specification)
