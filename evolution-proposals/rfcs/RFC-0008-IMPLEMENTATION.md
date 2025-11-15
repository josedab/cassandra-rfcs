# RFC-0008 Enhanced CQL Features - Implementation

This document describes the implementation of RFC-0008, which adds support for Common Table Expressions (CTEs), Window Functions, Recursive Queries, and Enhanced Aggregations to Apache Cassandra.

## Implementation Overview

The implementation follows the design outlined in RFC-0008-enhanced-cql-features.md and consists of the following components:

### 1. Common Table Expressions (CTEs)

**Location**: `src/java/org/apache/cassandra/cql3/cte/`

**Files**:
- `CTEDefinition.java` - Represents a CTE and handles materialization
- `CTEContext.java` - Execution context for CTEs
- `CTEProcessor.java` - Main processor for CTE parsing and execution
- `CTEDependencyGraph.java` - Dependency analysis and topological sorting
- `MaterializedResultSet.java` - In-memory CTE result storage

**Features**:
- Single and multiple CTE support with `WITH` clause
- Dependency ordering with cycle detection
- Memory-efficient result materialization
- Column aliasing support

**Example**:
```sql
WITH recent_orders AS (
    SELECT user_id, order_id, total
    FROM orders
    WHERE partition_date = '2024-11-15'
)
SELECT user_id, COUNT(*) as order_count, SUM(total) as total_spent
FROM recent_orders
GROUP BY user_id;
```

### 2. Window Functions

**Location**: `src/java/org/apache/cassandra/cql3/window/`

**Files**:
- `WindowDefinition.java` - Window specification (PARTITION BY, ORDER BY)
- `WindowFrame.java` - Frame specification (ROWS/RANGE)
- `WindowFunctionProcessor.java` - Main processor with built-in evaluators

**Supported Functions**:
- Ranking: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`, `PERCENT_RANK()`, `NTILE(n)`
- Navigation: `LAG()`, `LEAD()`, `FIRST_VALUE()`, `LAST_VALUE()`, `NTH_VALUE()`
- Distribution: `CUME_DIST()`
- Aggregates: `SUM()`, `AVG()`, `MIN()`, `MAX()`, `COUNT()` over windows

**Example**:
```sql
SELECT
    sensor_id,
    timestamp,
    temperature,
    AVG(temperature) OVER (
        PARTITION BY sensor_id
        ORDER BY timestamp
        ROWS BETWEEN 5 PRECEDING AND CURRENT ROW
    ) as moving_avg
FROM sensor_readings
WHERE date = '2024-11-15';
```

### 3. Recursive Queries

**Location**: `src/java/org/apache/cassandra/cql3/recursive/`

**Files**:
- `RecursiveQueryProcessor.java` - Main recursive query processor
- `TerminationCondition.java` - Recursion termination conditions
- `HierarchicalQueryOptimizer.java` - Query optimization for hierarchies

**Features**:
- Limited recursion with configurable depth limits
- Cycle detection to prevent infinite loops
- Parent-child hierarchy traversal
- Graph traversal patterns

**Example**:
```sql
WITH RECURSIVE org_chart AS (
    SELECT employee_id, name, manager_id, 1 as level
    FROM employees
    WHERE employee_id = ? AND company_id = ?

    UNION ALL

    SELECT e.employee_id, e.name, e.manager_id, oc.level + 1
    FROM employees e
    INNER JOIN org_chart oc ON e.manager_id = oc.employee_id
    WHERE e.company_id = ? AND oc.level < 10
)
SELECT * FROM org_chart;
```

### 4. Enhanced Aggregations

**Location**: `src/java/org/apache/cassandra/cql3/functions/aggregate/`

**Files**:
- `EnhancedAggregates.java` - New aggregate function implementations
  - `StringAggregation` - GROUP_CONCAT, STRING_AGG
  - `StatisticalAggregates` - STDDEV, VARIANCE, PERCENTILE, MEDIAN
  - `ConditionalAggregates` - COUNT_IF, SUM_IF, AVG_IF
  - `ApproximateAggregates` - APPROX_COUNT_DISTINCT, APPROX_PERCENTILE
- `FilteredAggregation.java` - FILTER clause support

**Example**:
```sql
SELECT
    category,
    COUNT(*) as total_items,
    COUNT(*) FILTER (WHERE in_stock = true) as items_in_stock,
    GROUP_CONCAT(DISTINCT brand ORDER BY brand) as all_brands,
    AVG(price) as avg_price,
    STDDEV(price) as price_stddev,
    MEDIAN(price) as median_price,
    APPROX_COUNT_DISTINCT(customer_viewed) as approx_unique_viewers
FROM products
WHERE last_updated >= '2024-11-01'
GROUP BY category;
```

## Configuration

**Location**: `conf/cassandra.yaml`

New configuration section:
```yaml
enhanced_cql:
  # Feature flags
  enable_cte: true
  enable_window_functions: true
  enable_recursive: false  # Disabled by default
  enable_enhanced_aggregates: true

  # CTE limits
  cte_max_memory_mb: 256
  cte_spill_to_disk: true

  # Window function limits
  window_max_partition_size: 100000
  window_memory_limit_mb: 512

  # Recursion limits
  max_recursion_depth: 100
  recursion_timeout_ms: 30000

  # Aggregate limits
  aggregate_max_memory_mb: 128
  approx_aggregate_precision: 0.01
```

## Tests

**Location**: `test/unit/org/apache/cassandra/cql3/validation/`

Test suites:
- `cte/CTEBasicTest.java` - CTE functionality tests
- `window/RankingFunctionTest.java` - Window function tests
- `recursive/RecursiveQueryTest.java` - Recursive query tests
- `aggregates/EnhancedAggregateTest.java` - Enhanced aggregate tests

## Integration Points

### Parser Integration
The implementation requires updates to the CQL parser (ANTLR grammar):
- Add `WITH` clause support for CTEs
- Add `OVER` clause support for window functions
- Add `RECURSIVE` keyword support
- Add `FILTER` clause support for aggregates

### Query Processor Integration
Integration with existing query processing:
- `QueryProcessor.java` - Route CTE/window/recursive queries
- `SelectStatement.java` - Execute enhanced queries
- `Selection.java` - Handle window function results

## Performance Considerations

### Memory Management
- CTEs materialize results in memory with configurable limits
- Spill-to-disk capability for large CTE results
- Window functions process partitions independently
- Recursive queries limited by depth and timeout

### Optimization
- CTEs executed once and cached for multiple references
- Window functions use partition-based processing
- Recursive queries detect and optimize common patterns
- Approximate aggregates use probabilistic data structures (HyperLogLog)

## Migration Path

1. **Testing Phase**: Enable features in development with feature flags
2. **Gradual Rollout**: Enable per-keyspace or cluster-wide
3. **Backward Compatibility**: All features are additive, existing queries unaffected

## Future Enhancements

- Full SQL:2016 window function compliance
- Cross-partition window functions
- Recursive query optimization for distributed execution
- Additional statistical aggregates
- Integration with materialized views

## References

- RFC-0008: Enhanced CQL Features (main design document)
- SQL:2016 Standard
- PostgreSQL Window Functions Documentation
- Apache Cassandra CQL3 Documentation

## Implementation Status

- ✅ Core classes implemented
- ✅ Configuration added
- ✅ Basic tests created
- ⏳ Parser integration (pending)
- ⏳ Query processor integration (pending)
- ⏳ Full test coverage (pending)
- ⏳ Performance benchmarks (pending)

## Contributors

Implementation based on RFC-0008 by the Cassandra Development Team.
