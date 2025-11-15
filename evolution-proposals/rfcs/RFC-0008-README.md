# RFC-0008: Enhanced CQL Features - README

## Quick Start

This README provides a quick overview of the Enhanced CQL Features implementation for Apache Cassandra.

## What's New

RFC-0008 adds four major enhancements to CQL:

1. **Common Table Expressions (CTEs)** - WITH clause for query organization
2. **Window Functions** - OVER clause for analytics
3. **Recursive Queries** - WITH RECURSIVE for hierarchical data
4. **Enhanced Aggregations** - New aggregate functions and FILTER clause

## Installation Status

### ✅ Completed
- Core Java classes for all features
- Configuration settings in cassandra.yaml
- Grammar extensions (keywords added to Lexer.g)
- Statement builders and selectors
- Basic unit test framework
- Comprehensive documentation

### ⏳ In Progress / Pending
- Full ANTLR Parser.g integration
- Query processor integration
- Complete test suite execution
- Performance benchmarking

## File Structure

```
cassandra-rfcs/
├── src/java/org/apache/cassandra/cql3/
│   ├── cte/                          # CTE implementation
│   │   ├── CTEDefinition.java
│   │   ├── CTEContext.java
│   │   ├── CTEProcessor.java
│   │   ├── CTEDependencyGraph.java
│   │   └── MaterializedResultSet.java
│   │
│   ├── window/                       # Window functions
│   │   ├── WindowDefinition.java
│   │   ├── WindowFrame.java
│   │   └── WindowFunctionProcessor.java
│   │
│   ├── recursive/                    # Recursive queries
│   │   ├── RecursiveQueryProcessor.java
│   │   ├── TerminationCondition.java
│   │   └── HierarchicalQueryOptimizer.java
│   │
│   ├── functions/aggregate/          # Enhanced aggregates
│   │   ├── EnhancedAggregates.java
│   │   └── FilteredAggregation.java
│   │
│   ├── statements/
│   │   └── SelectStatementWithCTE.java
│   │
│   ├── selection/
│   │   └── WindowFunctionSelector.java
│   │
│   └── EnhancedCQLFeatures.java      # Configuration manager
│
├── src/antlr/
│   ├── Lexer.g                       # ✅ Keywords added
│   └── Parser.g                      # ⏳ Rules need integration
│
├── test/unit/org/apache/cassandra/cql3/validation/
│   ├── cte/CTEBasicTest.java
│   ├── window/RankingFunctionTest.java
│   ├── recursive/RecursiveQueryTest.java
│   └── aggregates/EnhancedAggregateTest.java
│
├── conf/
│   └── cassandra.yaml                # ✅ Configuration added
│
└── evolution-proposals/rfcs/
    ├── RFC-0008-enhanced-cql-features.md
    ├── RFC-0008-IMPLEMENTATION.md
    ├── RFC-0008-GRAMMAR-EXTENSIONS.md
    ├── RFC-0008-EXAMPLES.md
    └── RFC-0008-README.md
```

## Quick Examples

### CTE Example
```sql
WITH recent_orders AS (
    SELECT user_id, SUM(amount) as total
    FROM orders
    WHERE order_date >= '2024-11-01'
    GROUP BY user_id
)
SELECT * FROM recent_orders WHERE total > 1000;
```

### Window Function Example
```sql
SELECT
    product_id,
    sales,
    ROW_NUMBER() OVER (ORDER BY sales DESC) as rank,
    AVG(sales) OVER (
        ORDER BY sale_date
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ) as moving_avg_7day
FROM product_sales;
```

### Recursive Query Example
```sql
WITH RECURSIVE org_tree AS (
    SELECT employee_id, name, manager_id, 0 as level
    FROM employees WHERE manager_id IS NULL

    UNION ALL

    SELECT e.employee_id, e.name, e.manager_id, o.level + 1
    FROM employees e
    JOIN org_tree o ON e.manager_id = o.employee_id
    WHERE o.level < 10
)
SELECT * FROM org_tree;
```

### Enhanced Aggregates Example
```sql
SELECT
    category,
    COUNT(*) FILTER (WHERE in_stock = true) as in_stock_count,
    STDDEV(price) as price_stddev,
    MEDIAN(price) as median_price,
    GROUP_CONCAT(brand, ', ') as brands
FROM products
GROUP BY category;
```

## Configuration

Edit `conf/cassandra.yaml`:

```yaml
enhanced_cql:
  # Feature toggles
  enable_cte: true
  enable_window_functions: true
  enable_recursive: false          # Disabled by default
  enable_enhanced_aggregates: true

  # Resource limits
  cte_max_memory_mb: 256
  window_max_partition_size: 100000
  max_recursion_depth: 100
  recursion_timeout_ms: 30000
  aggregate_max_memory_mb: 128
```

## Feature Status

| Feature | Implementation | Tests | Docs | Status |
|---------|---------------|-------|------|--------|
| CTEs | ✅ Complete | ⚠️ Basic | ✅ Complete | Ready for integration |
| Window Functions | ✅ Complete | ⚠️ Basic | ✅ Complete | Ready for integration |
| Recursive Queries | ✅ Complete | ⚠️ Basic | ✅ Complete | Ready for integration |
| Enhanced Aggregates | ✅ Complete | ⚠️ Basic | ✅ Complete | Ready for integration |
| Grammar (Lexer) | ✅ Complete | N/A | ✅ Complete | Keywords added |
| Grammar (Parser) | ⏳ Documented | N/A | ✅ Complete | Needs integration |
| Query Processor | ⏳ Pending | ⏳ Pending | ✅ Complete | Needs integration |

## Testing

### Running Unit Tests

```bash
# Run all enhanced CQL tests
ant test -Dtest.name=org.apache.cassandra.cql3.validation.cte.*
ant test -Dtest.name=org.apache.cassandra.cql3.validation.window.*
ant test -Dtest.name=org.apache.cassandra.cql3.validation.recursive.*
ant test -Dtest.name=org.apache.cassandra.cql3.validation.aggregates.*
```

### Test Coverage

Current test files (placeholder implementation):
- `CTEBasicTest.java` - Basic CTE functionality
- `RankingFunctionTest.java` - Window ranking functions
- `RecursiveQueryTest.java` - Recursive query patterns
- `EnhancedAggregateTest.java` - New aggregate functions

## Next Steps for Full Integration

### 1. Parser Integration
Add grammar rules to `src/antlr/Parser.g`:
- WITH clause parsing
- OVER clause parsing
- FILTER clause parsing
- Window frame specifications

See `RFC-0008-GRAMMAR-EXTENSIONS.md` for details.

### 2. Query Processor Integration
Update `QueryProcessor.java` to route enhanced queries to appropriate handlers.

### 3. SelectStatement Extension
Integrate CTE and window function processing into SelectStatement execution.

### 4. Complete Testing
- Implement actual query execution in tests
- Add integration tests
- Performance benchmarks

### 5. Documentation
- Update user documentation
- Add to CQL specification
- Create migration guide

## Performance Considerations

### Memory Management
- **CTEs**: Materialize in memory with configurable limits (default: 256MB)
- **Window Functions**: Process partitions independently (default max: 100K rows)
- **Recursive Queries**: Depth-limited (default: 100 levels) with timeout (default: 30s)

### Best Practices
1. Use CTEs for query organization, not just performance
2. Filter early in CTEs to reduce materialized data
3. Set appropriate recursion depth limits
4. Use approximate aggregates for very large datasets
5. Partition window functions appropriately

See `RFC-0008-EXAMPLES.md` for detailed performance tips.

## Known Limitations

1. **Cross-Partition Window Functions**: Not supported in initial implementation
2. **Recursive Queries**: Limited to single partition for performance
3. **CTE Modification**: CTEs are read-only (no INSERT/UPDATE/DELETE)
4. **Memory Limits**: Large CTEs may spill to disk if configured

## Contributing

### Code Style
- Follow existing Cassandra code conventions
- Add appropriate logging
- Include comprehensive javadoc
- Write unit tests for new features

### Testing Requirements
- Unit tests for core functionality
- Integration tests for query execution
- Performance benchmarks for large datasets
- Edge case coverage

## Documentation

- **Design**: `RFC-0008-enhanced-cql-features.md`
- **Implementation**: `RFC-0008-IMPLEMENTATION.md`
- **Grammar**: `RFC-0008-GRAMMAR-EXTENSIONS.md`
- **Examples**: `RFC-0008-EXAMPLES.md`
- **This File**: `RFC-0008-README.md`

## Support

For questions or issues:
1. Check the RFC documents in `evolution-proposals/rfcs/`
2. Review examples in `RFC-0008-EXAMPLES.md`
3. Check test files for usage patterns
4. Refer to Cassandra documentation

## Version History

- **v1.0** (2024-11-15): Initial implementation
  - Core classes for all features
  - Grammar extensions
  - Configuration support
  - Basic tests
  - Comprehensive documentation

## License

Apache License 2.0 - See LICENSE file for details

## Authors

Cassandra Development Team
Implementation based on RFC-0008

---

**Status**: Implementation complete, pending parser and query processor integration
**Last Updated**: 2024-11-15
