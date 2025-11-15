# RFC-0008: Enhanced CQL Features

- **Status:** Draft
- **Type:** Enhancement
- **Priority:** P3-Medium
- **Start Date:** 2024-11-15
- **Author(s):** Cassandra Development Team
- **Ticket:** CASSANDRA-XXXXX
- **Discussion:** [Dev mailing list thread]

## Summary

This RFC proposes significant enhancements to the Cassandra Query Language (CQL), including support for Common Table Expressions (CTEs), window functions, limited recursive queries, and enhanced aggregations. These additions will make CQL more expressive and powerful, reducing the need for application-side data processing while maintaining Cassandra's distributed nature and performance characteristics.

## Motivation

CQL has matured significantly but lacks several features that developers expect from modern query languages. This gap forces complex application-side processing, increases network traffic, and makes Cassandra less competitive with other NoSQL and NewSQL databases that offer richer query capabilities.

### Current State

CQL currently supports:
- Basic CRUD operations
- Simple aggregations (COUNT, MIN, MAX, SUM, AVG)
- Limited JOIN capability (via batch operations)
- User-defined functions (UDFs) and aggregates (UDAs)
- Materialized views
- Secondary indexes

### Problem Statement

Limitations causing developer friction:
1. **No Query Composition**: Cannot break complex queries into reusable parts
2. **Limited Analytics**: Missing window functions for time-series analysis
3. **No Hierarchical Queries**: Cannot traverse graph-like relationships
4. **Basic Aggregations**: Missing statistical and string aggregations
5. **Verbose Queries**: Repetitive code for common patterns

## Detailed Design

### 1. Common Table Expressions (CTEs)

#### Syntax and Semantics

```sql
-- Basic CTE syntax
WITH cte_name [(column_list)] AS (
    -- CQL SELECT statement
    SELECT ...
)
SELECT ... FROM cte_name ...;

-- Multiple CTEs
WITH 
    cte1 AS (SELECT ...),
    cte2 AS (SELECT ... FROM cte1 ...)
SELECT ... FROM cte2 ...;
```

#### Implementation

```java
public class CTEProcessor {
    private final Map<String, CTEDefinition> cteDefinitions = new LinkedHashMap<>();
    private final ExecutionContext context;
    
    public class CTEDefinition {
        private final String name;
        private final SelectStatement statement;
        private final List<ColumnIdentifier> columns;
        private ResultSet cachedResult;
        private boolean materialized = false;
        
        public ResultSet execute() {
            if (!materialized) {
                cachedResult = materialize();
                materialized = true;
            }
            return cachedResult;
        }
        
        private ResultSet materialize() {
            // Execute the CTE query once and cache results
            QueryOptions options = context.getQueryOptions();
            ResultMessage.Rows rows = statement.execute(options);
            
            // Store in memory-efficient format
            return new MaterializedResultSet(rows);
        }
    }
    
    public class CTEParser {
        public ParsedStatement parse(String cql) {
            // Parse WITH clause
            List<CTEClause> ctes = parseWithClause(cql);
            
            // Build dependency graph
            CTEDependencyGraph graph = buildDependencyGraph(ctes);
            
            // Validate no cycles
            if (graph.hasCycles()) {
                throw new InvalidRequestException("Circular CTE reference detected");
            }
            
            // Order CTEs by dependencies
            List<CTEClause> ordered = graph.topologicalSort();
            
            // Parse main query
            SelectStatement mainQuery = parseMainQuery(cql);
            
            return new CTEStatement(ordered, mainQuery);
        }
    }
    
    public class CTEExecutor {
        public ResultSet execute(CTEStatement statement, QueryState state) {
            // Initialize CTE context
            CTEContext context = new CTEContext();
            
            // Execute CTEs in dependency order
            for (CTEClause cte : statement.getCTEs()) {
                CTEDefinition definition = new CTEDefinition(
                    cte.getName(),
                    cte.getStatement(),
                    cte.getColumns()
                );
                
                // Replace CTE references with materialized results
                definition.resolveReferences(context);
                
                // Execute and cache
                context.register(cte.getName(), definition);
            }
            
            // Execute main query with CTE context
            return executeWithContext(statement.getMainQuery(), context);
        }
        
        private ResultSet executeWithContext(SelectStatement query, CTEContext context) {
            // Replace CTE table references with materialized results
            SelectStatement rewritten = rewriteQuery(query, context);
            
            // Execute rewritten query
            return rewritten.execute();
        }
    }
}
```

#### Example Use Cases

```sql
-- 1. Recent orders summary
WITH recent_orders AS (
    SELECT user_id, order_id, total, order_date
    FROM orders
    WHERE partition_date = '2024-11-15'
    AND order_date > currentTimestamp() - 7d
)
SELECT user_id, COUNT(*) as order_count, SUM(total) as total_spent
FROM recent_orders
GROUP BY user_id;

-- 2. Multi-level aggregation
WITH 
    daily_stats AS (
        SELECT date, category, SUM(amount) as daily_total
        FROM transactions
        WHERE date >= '2024-11-01' AND date < '2024-12-01'
        GROUP BY date, category
    ),
    category_avg AS (
        SELECT category, AVG(daily_total) as avg_daily
        FROM daily_stats
        GROUP BY category
    )
SELECT * FROM category_avg WHERE avg_daily > 1000;

-- 3. Data deduplication
WITH ranked_events AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY timestamp DESC) as rn
    FROM events
    WHERE partition_date = '2024-11-15'
)
SELECT * FROM ranked_events WHERE rn = 1;
```

### 2. Window Functions

#### Supported Functions

```java
public enum WindowFunction {
    // Ranking functions
    ROW_NUMBER,      // Sequential row numbering
    RANK,            // Ranking with gaps
    DENSE_RANK,      // Ranking without gaps
    PERCENT_RANK,    // Relative rank (0-1)
    NTILE,          // Bucket assignment
    
    // Value functions
    LAG,            // Previous row value
    LEAD,           // Next row value
    FIRST_VALUE,    // First value in window
    LAST_VALUE,     // Last value in window
    NTH_VALUE,      // Nth value in window
    
    // Aggregate functions (over window)
    SUM, AVG, MIN, MAX, COUNT,
    STDDEV, VARIANCE,
    
    // Distribution functions
    CUME_DIST,      // Cumulative distribution
    PERCENTILE_CONT, // Continuous percentile
    PERCENTILE_DISC  // Discrete percentile
}
```

#### Implementation

```java
public class WindowFunctionProcessor {
    
    public class WindowDefinition {
        private final List<ColumnIdentifier> partitionBy;
        private final List<OrderByClause> orderBy;
        private final WindowFrame frame;
        
        public enum FrameType {
            ROWS,    // Physical rows
            RANGE    // Logical range
        }
        
        public class WindowFrame {
            private final FrameType type;
            private final FrameBound start;
            private final FrameBound end;
            
            public enum BoundType {
                UNBOUNDED_PRECEDING,
                UNBOUNDED_FOLLOWING,
                CURRENT_ROW,
                PRECEDING(int offset),
                FOLLOWING(int offset)
            }
        }
    }
    
    public class WindowExecutor {
        private final Map<String, WindowDefinition> windows;
        private final Map<WindowFunction, WindowFunctionEvaluator> evaluators;
        
        public ResultSet execute(SelectStatement statement) {
            // Fetch base data
            ResultSet baseData = fetchBaseData(statement);
            
            // Partition data according to PARTITION BY
            Map<PartitionKey, List<Row>> partitions = partitionData(baseData);
            
            // Process each partition
            List<Row> results = new ArrayList<>();
            for (Map.Entry<PartitionKey, List<Row>> partition : partitions.entrySet()) {
                List<Row> partitionRows = partition.getValue();
                
                // Sort according to ORDER BY
                sortPartition(partitionRows, statement.getWindowOrderBy());
                
                // Apply window functions
                applyWindowFunctions(partitionRows, statement.getWindowFunctions());
                
                results.addAll(partitionRows);
            }
            
            return new WindowResultSet(results);
        }
        
        private void applyWindowFunctions(List<Row> rows, List<WindowFunctionCall> functions) {
            for (WindowFunctionCall function : functions) {
                WindowFunctionEvaluator evaluator = getEvaluator(function.getType());
                
                for (int i = 0; i < rows.size(); i++) {
                    Row currentRow = rows.get(i);
                    
                    // Determine window frame for current row
                    WindowFrame frame = function.getFrame();
                    List<Row> windowRows = getWindowRows(rows, i, frame);
                    
                    // Evaluate function over window
                    Object result = evaluator.evaluate(windowRows, currentRow, function.getArgs());
                    
                    // Add result to row
                    currentRow.addWindowResult(function.getAlias(), result);
                }
            }
        }
    }
    
    public class RankingFunctions {
        
        public static class RowNumberEvaluator implements WindowFunctionEvaluator {
            private final AtomicInteger counter = new AtomicInteger(1);
            
            @Override
            public Object evaluate(List<Row> window, Row current, List<Object> args) {
                // ROW_NUMBER is simply the position in the ordered partition
                return counter.getAndIncrement();
            }
            
            @Override
            public void resetPartition() {
                counter.set(1);
            }
        }
        
        public static class RankEvaluator implements WindowFunctionEvaluator {
            private int currentRank = 1;
            private int sameValueCount = 0;
            private Object lastValue = null;
            
            @Override
            public Object evaluate(List<Row> window, Row current, List<Object> args) {
                Object currentValue = current.getValue(args.get(0));
                
                if (lastValue != null && !lastValue.equals(currentValue)) {
                    currentRank += sameValueCount;
                    sameValueCount = 1;
                } else {
                    sameValueCount++;
                }
                
                lastValue = currentValue;
                return currentRank;
            }
        }
        
        public static class PercentileEvaluator implements WindowFunctionEvaluator {
            @Override
            public Object evaluate(List<Row> window, Row current, List<Object> args) {
                double percentile = (Double) args.get(0);
                List<Double> values = extractValues(window, args.get(1));
                
                Collections.sort(values);
                
                // Continuous percentile calculation
                double index = percentile * (values.size() - 1);
                int lower = (int) Math.floor(index);
                int upper = (int) Math.ceil(index);
                
                if (lower == upper) {
                    return values.get(lower);
                }
                
                // Linear interpolation
                double weight = index - lower;
                return values.get(lower) * (1 - weight) + values.get(upper) * weight;
            }
        }
    }
    
    public class NavigationFunctions {
        
        public static class LagLeadEvaluator implements WindowFunctionEvaluator {
            private final boolean isLag;
            
            public LagLeadEvaluator(boolean isLag) {
                this.isLag = isLag;
            }
            
            @Override
            public Object evaluate(List<Row> window, Row current, List<Object> args) {
                String column = (String) args.get(0);
                int offset = args.size() > 1 ? (Integer) args.get(1) : 1;
                Object defaultValue = args.size() > 2 ? args.get(2) : null;
                
                int currentIndex = window.indexOf(current);
                int targetIndex = isLag ? currentIndex - offset : currentIndex + offset;
                
                if (targetIndex < 0 || targetIndex >= window.size()) {
                    return defaultValue;
                }
                
                return window.get(targetIndex).getValue(column);
            }
        }
    }
}
```

#### Example Queries

```sql
-- 1. Ranking users by purchase amount
SELECT 
    user_id,
    total_purchases,
    RANK() OVER (ORDER BY total_purchases DESC) as purchase_rank,
    PERCENT_RANK() OVER (ORDER BY total_purchases DESC) as percentile
FROM user_stats
WHERE month = '2024-11';

-- 2. Moving averages for time series
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

-- 3. Year-over-year comparison
SELECT 
    month,
    revenue,
    LAG(revenue, 12) OVER (ORDER BY month) as revenue_last_year,
    revenue - LAG(revenue, 12) OVER (ORDER BY month) as yoy_change
FROM monthly_revenue
WHERE account_id = ?;

-- 4. Percentile analysis
SELECT 
    category,
    product_id,
    price,
    PERCENTILE_CONT(0.5) OVER (PARTITION BY category) as median_price,
    PERCENTILE_CONT(0.95) OVER (PARTITION BY category) as p95_price
FROM products;
```

### 3. Recursive Queries (Limited)

#### Design Constraints

- Limited to single partition to maintain performance
- Maximum recursion depth configurable
- Must specify termination condition
- Read-only operations

#### Implementation

```java
public class RecursiveQueryProcessor {
    private static final int DEFAULT_MAX_DEPTH = 100;
    
    public class RecursiveCTE {
        private final String name;
        private final SelectStatement anchor;      // Non-recursive part
        private final SelectStatement recursive;   // Recursive part
        private final TerminationCondition termination;
        private final int maxDepth;
        
        public ResultSet execute(QueryState state) {
            // Initialize with anchor results
            Set<Row> results = new HashSet<>();
            ResultSet anchorResults = anchor.execute(state);
            results.addAll(anchorResults.getRows());
            
            // Working set for current iteration
            Set<Row> workingSet = new HashSet<>(anchorResults.getRows());
            
            int depth = 0;
            while (!workingSet.isEmpty() && depth < maxDepth) {
                Set<Row> nextSet = new HashSet<>();
                
                for (Row row : workingSet) {
                    // Bind current row values to recursive query
                    BoundStatement bound = bindRow(recursive, row);
                    
                    // Execute recursive part
                    ResultSet recursiveResults = bound.execute(state);
                    
                    for (Row newRow : recursiveResults) {
                        // Check termination condition
                        if (termination.shouldTerminate(newRow, depth)) {
                            continue;
                        }
                        
                        // Add if not already seen (cycle detection)
                        if (results.add(newRow)) {
                            nextSet.add(newRow);
                        }
                    }
                }
                
                workingSet = nextSet;
                depth++;
            }
            
            if (depth >= maxDepth) {
                logger.warn("Recursive query hit max depth limit: {}", maxDepth);
            }
            
            return new MaterializedResultSet(results);
        }
    }
    
    public class HierarchicalQueryOptimizer {
        // Optimize for common hierarchical patterns
        
        public OptimizedPlan optimizeTreeTraversal(RecursiveCTE cte) {
            // Detect parent-child relationship pattern
            if (isParentChildPattern(cte)) {
                return new ParentChildTraversalPlan(cte);
            }
            
            // Detect graph traversal pattern
            if (isGraphPattern(cte)) {
                return new GraphTraversalPlan(cte);
            }
            
            return new GenericRecursivePlan(cte);
        }
    }
    
    // Example: Organization hierarchy traversal
    public class OrganizationHierarchyExample {
        public String generateQuery() {
            return """
                WITH RECURSIVE org_tree AS (
                    -- Anchor: Start with top-level manager
                    SELECT employee_id, name, manager_id, 0 as level
                    FROM employees
                    WHERE employee_id = ? AND company_id = ?
                    
                    UNION ALL
                    
                    -- Recursive: Find direct reports
                    SELECT e.employee_id, e.name, e.manager_id, t.level + 1
                    FROM employees e
                    INNER JOIN org_tree t ON e.manager_id = t.employee_id
                    WHERE e.company_id = ? AND t.level < 10
                )
                SELECT * FROM org_tree;
                """;
        }
    }
}
```

### 4. Enhanced Aggregations

#### New Aggregate Functions

```java
public class EnhancedAggregates {
    
    // String aggregations
    public static class StringAggregation {
        
        @UDAggregate(name = "group_concat")
        public static class GroupConcat {
            private StringBuilder builder = new StringBuilder();
            private String separator = ",";
            private boolean first = true;
            
            @UDAggregateFunction
            public void aggregate(String value, String sep) {
                if (sep != null) {
                    separator = sep;
                }
                if (value != null) {
                    if (!first) {
                        builder.append(separator);
                    }
                    builder.append(value);
                    first = false;
                }
            }
            
            @UDAggregateFunction
            public String finalValue() {
                return builder.toString();
            }
        }
        
        @UDAggregate(name = "string_agg")
        public static class StringAgg extends GroupConcat {
            // Alias for PostgreSQL compatibility
        }
    }
    
    // Statistical aggregations
    public static class StatisticalAggregates {
        
        @UDAggregate(name = "stddev")
        public static class StandardDeviation {
            private double sum = 0;
            private double sumSquares = 0;
            private long count = 0;
            
            @UDAggregateFunction
            public void aggregate(double value) {
                sum += value;
                sumSquares += value * value;
                count++;
            }
            
            @UDAggregateFunction
            public Double finalValue() {
                if (count < 2) return null;
                double mean = sum / count;
                double variance = (sumSquares / count) - (mean * mean);
                return Math.sqrt(variance);
            }
        }
        
        @UDAggregate(name = "percentile")
        public static class Percentile {
            private final List<Double> values = new ArrayList<>();
            
            @UDAggregateFunction
            public void aggregate(double value) {
                values.add(value);
            }
            
            @UDAggregateFunction
            public Double finalValue(double p) {
                if (values.isEmpty()) return null;
                
                Collections.sort(values);
                int index = (int) Math.ceil(p * values.size()) - 1;
                return values.get(Math.max(0, Math.min(index, values.size() - 1)));
            }
        }
        
        @UDAggregate(name = "median")
        public static class Median extends Percentile {
            @Override
            public Double finalValue() {
                return super.finalValue(0.5);
            }
        }
    }
    
    // Conditional aggregations
    public static class ConditionalAggregates {
        
        @UDAggregate(name = "count_if")
        public static class CountIf {
            private long count = 0;
            
            @UDAggregateFunction
            public void aggregate(boolean condition) {
                if (condition) {
                    count++;
                }
            }
            
            @UDAggregateFunction
            public long finalValue() {
                return count;
            }
        }
        
        @UDAggregate(name = "sum_if")
        public static class SumIf {
            private double sum = 0;
            
            @UDAggregateFunction
            public void aggregate(double value, boolean condition) {
                if (condition) {
                    sum += value;
                }
            }
            
            @UDAggregateFunction
            public double finalValue() {
                return sum;
            }
        }
    }
    
    // Approximate aggregations
    public static class ApproximateAggregates {
        
        @UDAggregate(name = "approx_count_distinct")
        public static class ApproxCountDistinct {
            private final HyperLogLogPlus hll;
            
            public ApproxCountDistinct() {
                this.hll = new HyperLogLogPlus(14); // 2^14 registers
            }
            
            @UDAggregateFunction
            public void aggregate(Object value) {
                if (value != null) {
                    hll.offer(value);
                }
            }
            
            @UDAggregateFunction
            public long finalValue() {
                return hll.cardinality();
            }
        }
        
        @UDAggregate(name = "approx_percentile")
        public static class ApproxPercentile {
            private final TDigest tdigest;
            
            public ApproxPercentile() {
                this.tdigest = new TDigest(100); // Compression parameter
            }
            
            @UDAggregateFunction
            public void aggregate(double value) {
                tdigest.add(value);
            }
            
            @UDAggregateFunction
            public double finalValue(double percentile) {
                return tdigest.quantile(percentile);
            }
        }
    }
}
```

#### FILTER Clause for Aggregates

```java
public class FilteredAggregation {
    
    public class AggregateWithFilter {
        private final AggregateFunction function;
        private final Expression filter;
        
        public Object evaluate(List<Row> rows) {
            // Apply filter before aggregation
            List<Row> filtered = rows.stream()
                .filter(row -> filter.evaluate(row))
                .collect(Collectors.toList());
            
            return function.evaluate(filtered);
        }
    }
    
    // Parser support
    public class EnhancedCQLParser {
        public AggregateExpression parseAggregate(String expression) {
            // Parse: COUNT(*) FILTER (WHERE status = 'active')
            Pattern pattern = Pattern.compile(
                "(\\w+)\\s*\\(([^)]+)\\)\\s*(?:FILTER\\s*\\(WHERE\\s+(.+)\\))?"
            );
            
            Matcher matcher = pattern.matcher(expression);
            if (matcher.matches()) {
                String functionName = matcher.group(1);
                String arguments = matcher.group(2);
                String filterClause = matcher.group(3);
                
                AggregateFunction function = getAggregateFunction(functionName);
                Expression filter = filterClause != null ? 
                    parseExpression(filterClause) : AlwaysTrue.INSTANCE;
                
                return new FilteredAggregate(function, arguments, filter);
            }
            
            throw new InvalidRequestException("Invalid aggregate expression: " + expression);
        }
    }
}
```

### 5. Example Complex Queries

```sql
-- 1. Sales analysis with multiple techniques
WITH monthly_sales AS (
    SELECT 
        month,
        region,
        SUM(amount) as total_sales,
        COUNT(DISTINCT customer_id) as unique_customers
    FROM sales
    WHERE year = 2024
    GROUP BY month, region
),
ranked_regions AS (
    SELECT 
        month,
        region,
        total_sales,
        unique_customers,
        RANK() OVER (PARTITION BY month ORDER BY total_sales DESC) as sales_rank,
        total_sales / SUM(total_sales) OVER (PARTITION BY month) as market_share
    FROM monthly_sales
)
SELECT 
    month,
    region,
    total_sales,
    unique_customers,
    sales_rank,
    ROUND(market_share * 100, 2) as market_share_pct,
    LAG(total_sales, 1) OVER (PARTITION BY region ORDER BY month) as prev_month_sales
FROM ranked_regions
WHERE sales_rank <= 5;

-- 2. Customer behavior analysis
SELECT 
    customer_id,
    purchase_date,
    amount,
    -- Running total
    SUM(amount) OVER (
        PARTITION BY customer_id 
        ORDER BY purchase_date 
        ROWS UNBOUNDED PRECEDING
    ) as running_total,
    -- Days since last purchase
    DATEDIFF('day', 
        LAG(purchase_date) OVER (PARTITION BY customer_id ORDER BY purchase_date),
        purchase_date
    ) as days_since_last,
    -- Purchase percentile
    PERCENT_RANK() OVER (
        PARTITION BY DATE_TRUNC('month', purchase_date) 
        ORDER BY amount
    ) as amount_percentile
FROM purchases
WHERE purchase_date >= '2024-01-01';

-- 3. Hierarchical organization query
WITH RECURSIVE org_chart AS (
    -- CEO level
    SELECT 
        employee_id, 
        name, 
        title,
        manager_id,
        1 as level,
        name as path
    FROM employees
    WHERE manager_id IS NULL AND company_id = ?
    
    UNION ALL
    
    -- Recursive part
    SELECT 
        e.employee_id,
        e.name,
        e.title,
        e.manager_id,
        oc.level + 1,
        oc.path || ' > ' || e.name as path
    FROM employees e
    INNER JOIN org_chart oc ON e.manager_id = oc.employee_id
    WHERE e.company_id = ? AND oc.level < 10
)
SELECT * FROM org_chart ORDER BY path;

-- 4. Advanced aggregations
SELECT 
    category,
    COUNT(*) as total_items,
    COUNT(DISTINCT brand) as unique_brands,
    -- Conditional counting
    COUNT(*) FILTER (WHERE in_stock = true) as items_in_stock,
    -- String aggregation
    GROUP_CONCAT(DISTINCT brand ORDER BY brand) as all_brands,
    -- Statistical measures
    AVG(price) as avg_price,
    STDDEV(price) as price_stddev,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY price) as median_price,
    -- Approximate distinct count
    APPROX_COUNT_DISTINCT(customer_viewed) as approx_unique_viewers
FROM products
WHERE last_updated >= '2024-11-01'
GROUP BY category;
```

## Performance Considerations

### Query Planning

```java
public class EnhancedQueryPlanner {
    
    public QueryPlan planQuery(ParsedStatement statement) {
        QueryPlan plan = new QueryPlan();
        
        // CTE materialization strategy
        if (statement.hasCTEs()) {
            for (CTE cte : statement.getCTEs()) {
                MaterializationStrategy strategy = chooseMaterializationStrategy(cte);
                plan.addCTEPlan(cte, strategy);
            }
        }
        
        // Window function execution strategy
        if (statement.hasWindowFunctions()) {
            WindowExecutionPlan windowPlan = planWindowExecution(statement);
            plan.setWindowPlan(windowPlan);
        }
        
        // Recursion depth limits
        if (statement.hasRecursion()) {
            RecursionPlan recursionPlan = planRecursion(statement);
            plan.setRecursionPlan(recursionPlan);
        }
        
        return plan;
    }
    
    private MaterializationStrategy chooseMaterializationStrategy(CTE cte) {
        long estimatedSize = estimateCTESize(cte);
        int referenceCount = countReferences(cte);
        
        if (estimatedSize < 1_000_000 && referenceCount > 1) {
            return MaterializationStrategy.EAGER; // Materialize once
        } else if (estimatedSize > 100_000_000) {
            return MaterializationStrategy.STREAMING; // Stream results
        } else {
            return MaterializationStrategy.LAZY; // Materialize on demand
        }
    }
}
```

### Memory Management

```yaml
# cassandra.yaml
enhanced_cql:
  # CTE memory limits
  cte_max_memory_mb: 256
  cte_spill_to_disk: true
  cte_temp_directory: /var/cassandra/temp
  
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

## Alternatives Considered

### Alternative 1: Full SQL Compatibility

Implement complete SQL:2016 standard.

**Why not chosen**:
- Conflicts with Cassandra's distributed model
- Would require fundamental architecture changes
- Performance implications unacceptable

### Alternative 2: Query Federation Layer

Build separate query engine on top of Cassandra.

**Why not chosen**:
- Additional complexity and maintenance
- Performance overhead of translation layer
- Inconsistent with Cassandra philosophy

### Alternative 3: Limited UDF Extensions

Extend UDF capabilities instead of native support.

**Why not chosen**:
- Poor performance compared to native implementation
- Limited optimization opportunities
- Security and sandboxing concerns

## Migration Path

### Backward Compatibility

- All new features are additive
- Existing queries continue to work unchanged
- New keywords are reserved but don't break existing tables

### Feature Flags

```yaml
# Enable features incrementally
enhanced_cql:
  enable_cte: true
  enable_window_functions: true
  enable_recursive: false  # Disabled by default
  enable_enhanced_aggregates: true
```

### Migration Steps

1. **Testing Phase**
   ```sql
   -- Test CTEs in development
   SET cql_features.cte = 'enabled';
   ```

2. **Gradual Rollout**
   ```yaml
   # Enable per keyspace
   ALTER KEYSPACE myks WITH cql_features = {
     'cte': 'enabled',
     'window_functions': 'enabled'
   };
   ```

## Testing Strategy

### Unit Tests

- CTE parsing and execution
- Window function calculations
- Recursive query termination
- Aggregate function accuracy
- Memory limit enforcement

### Integration Tests

- Complex multi-CTE queries
- Large window partitions
- Deep recursion handling
- Mixed feature queries
- Cluster-wide query execution

### Performance Tests

- CTE materialization strategies
- Window function scalability
- Recursion performance
- Aggregate memory usage
- Query optimization effectiveness

## Timeline and Milestones

| Milestone | Target Date | Description |
|-----------|------------|-------------|
| Design Review | 2024-12-01 | Complete design review |
| CTE Implementation | 2025-02-01 | Basic CTE support |
| Window Functions | 2025-04-01 | Core window functions |
| Recursive Queries | 2025-06-01 | Limited recursion |
| Enhanced Aggregates | 2025-07-15 | New aggregate functions |
| Beta Release | 2025-09-01 | Feature complete beta |
| GA Release | 2025-12-01 | Production ready |

## Dependencies

- ANTLR4 for CQL parser updates
- Apache Calcite (optional query optimization)
- DataSketches library for approximate algorithms

## Unresolved Questions

- [ ] Should CTEs be allowed to modify data?
- [ ] What's the appropriate default recursion limit?
- [ ] Should window functions work across partitions?
- [ ] How to handle CTE/window functions in prepared statements?
- [ ] Should we support LATERAL joins with CTEs?

## References

- [SQL:2016 Standard](https://www.iso.org/standard/63556.html)
- [PostgreSQL Window Functions](https://www.postgresql.org/docs/current/functions-window.html)
- [SQL Server CTE Documentation](https://docs.microsoft.com/en-us/sql/t-sql/queries/with-common-table-expression-transact-sql)