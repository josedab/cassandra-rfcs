# RFC-0004: Advanced Query Planning and Optimization

- **Status:** Draft
- **Type:** Feature
- **Priority:** P2-High
- **Start Date:** 2024-11-15
- **Author(s):** Cassandra Development Team
- **Ticket:** CASSANDRA-XXXXX
- **Discussion:** [Dev mailing list thread]

## Summary

This RFC proposes adding intelligent query planning and optimization capabilities to Apache Cassandra, including query cost estimation, multi-index optimization, enhanced partition pruning, and adaptive query execution. These improvements will significantly enhance query performance while maintaining Cassandra's distributed architecture strengths, bringing query processing capabilities closer to modern analytical databases without sacrificing scalability.

## Motivation

Cassandra's current query processing is intentionally simplistic to maintain predictable performance at scale. However, as workloads become more complex and users expect more sophisticated query capabilities, there's a growing need for intelligent query optimization that can improve performance without compromising Cassandra's core strengths.

### Current State

The current query processing in Cassandra:
- Executes queries with minimal optimization
- Lacks cost-based decision making
- Has limited ability to leverage multiple indexes efficiently
- Requires ALLOW FILTERING for many legitimate queries
- Provides minimal feedback on query efficiency
- Uses simple partition and range scanning

### Problem Statement

Current limitations impact both developers and operators:

1. **Performance Unpredictability**: Queries can have vastly different performance with minor changes
2. **Index Underutilization**: Multiple indexes cannot be efficiently combined
3. **Excessive Data Scanning**: Limited partition pruning leads to unnecessary data reads
4. **Poor User Experience**: No warnings or suggestions for expensive queries before execution
5. **Manual Optimization Required**: Developers must manually optimize queries without tooling support

## Detailed Design

### API Changes

#### New CQL Extensions

```sql
-- Query cost estimation
EXPLAIN PLAN FOR
SELECT * FROM users 
WHERE country = 'US' AND age > 25 AND status = 'active';

-- Query hints
SELECT /*+ USE_INDEX(age_idx) */ * FROM users
WHERE age > 25 AND status = 'active';

-- Query statistics
SELECT * FROM users 
WHERE country = 'US'
WITH STATISTICS = true;

-- Adaptive execution control
SELECT * FROM large_table
WITH adaptive_execution = true,
     max_partitions = 1000,
     timeout_per_partition = '100ms';
```

#### New Virtual Tables

```sql
-- Query plan cache
CREATE VIRTUAL TABLE system_views.query_plans (
    query_id uuid,
    query_text text,
    plan_id uuid,
    plan_json text,
    estimated_cost bigint,
    actual_cost bigint,
    execution_count int,
    cache_time timestamp,
    last_used timestamp,
    PRIMARY KEY (query_id)
);

-- Query execution statistics
CREATE VIRTUAL TABLE system_views.query_stats (
    query_hash text,
    query_template text,
    execution_count bigint,
    total_time_ms bigint,
    avg_time_ms double,
    min_time_ms bigint,
    max_time_ms bigint,
    p50_time_ms bigint,
    p95_time_ms bigint,
    p99_time_ms bigint,
    rows_returned_total bigint,
    rows_scanned_total bigint,
    partitions_scanned_total bigint,
    last_execution timestamp,
    PRIMARY KEY (query_hash)
);

-- Index usage statistics
CREATE VIRTUAL TABLE system_views.index_usage (
    keyspace_name text,
    table_name text,
    index_name text,
    usage_count bigint,
    last_used timestamp,
    selectivity double,
    false_positive_rate double,
    avg_lookup_time_ms double,
    space_used_bytes bigint,
    PRIMARY KEY ((keyspace_name, table_name), index_name)
);

-- Query recommendations
CREATE VIRTUAL TABLE system_views.query_recommendations (
    recommendation_id uuid,
    query_hash text,
    recommendation_type text, -- 'add_index', 'modify_query', 'change_schema'
    priority text, -- 'high', 'medium', 'low'
    description text,
    suggested_action text,
    estimated_improvement double,
    confidence_score double,
    PRIMARY KEY (recommendation_id)
);
```

#### New nodetool Commands

```bash
# Analyze query performance
nodetool query analyze --query "SELECT * FROM ks.table WHERE ..."
  Output: Query plan, cost estimation, recommendations

# View query statistics
nodetool query stats [--top N] [--keyspace ks]
  Output: Most expensive queries, execution statistics

# Clear query plan cache
nodetool query clear-cache [--keyspace ks] [--table table]

# Enable query optimization features
nodetool query optimize --enable [--feature <feature>]
  Features: cost_based, adaptive_execution, index_intersection
```

### Implementation Details

#### 1. Query Cost Estimation

```java
public class QueryCostEstimator {
    private final TableMetadata table;
    private final IndexRegistry indexRegistry;
    private final StatisticsCollector statistics;
    
    public QueryCost estimate(SelectStatement select) {
        QueryCost cost = new QueryCost();
        
        // Estimate partitions to read
        PartitionEstimate partitions = estimatePartitions(select);
        cost.setPartitionsToScan(partitions.getCount());
        
        // Estimate rows to scan
        RowEstimate rows = estimateRows(select, partitions);
        cost.setRowsToScan(rows.getCount());
        
        // Estimate data size
        DataSizeEstimate dataSize = estimateDataSize(rows);
        cost.setBytesToRead(dataSize.getBytes());
        
        // Calculate network cost
        NetworkCost network = estimateNetworkCost(select, dataSize);
        cost.setNetworkCost(network);
        
        // Calculate total cost
        cost.setTotalCost(calculateTotalCost(cost));
        
        return cost;
    }
    
    private PartitionEstimate estimatePartitions(SelectStatement select) {
        if (select.hasPartitionKeyRestrictions()) {
            // Exact partition key match
            if (select.isExactPartitionMatch()) {
                return new PartitionEstimate(1);
            }
            
            // IN clause on partition key
            if (select.hasPartitionKeyInClause()) {
                return new PartitionEstimate(select.getPartitionKeyInValues().size());
            }
            
            // Token range query
            if (select.hasTokenRangeRestriction()) {
                return estimateTokenRangePartitions(select.getTokenRange());
            }
        }
        
        // Full table scan
        return new PartitionEstimate(statistics.getEstimatedPartitionCount(table));
    }
    
    private RowEstimate estimateRows(SelectStatement select, PartitionEstimate partitions) {
        // Get average rows per partition
        double avgRowsPerPartition = statistics.getAverageRowsPerPartition(table);
        
        // Apply clustering key selectivity
        double selectivity = 1.0;
        if (select.hasClusteringRestrictions()) {
            selectivity = estimateClusteringSelectivity(select);
        }
        
        // Apply regular column filters
        if (select.hasRegularColumnRestrictions()) {
            selectivity *= estimateFilterSelectivity(select);
        }
        
        long estimatedRows = (long)(partitions.getCount() * avgRowsPerPartition * selectivity);
        return new RowEstimate(estimatedRows);
    }
    
    public class CostBasedQueryPlanner {
        public QueryPlan createPlan(SelectStatement select, QueryCost cost) {
            QueryPlan plan = new QueryPlan();
            
            // Determine if indexes should be used
            IndexPlan indexPlan = selectBestIndexes(select, cost);
            plan.setIndexPlan(indexPlan);
            
            // Determine partition scan strategy
            PartitionScanStrategy scanStrategy = selectScanStrategy(select, cost);
            plan.setScanStrategy(scanStrategy);
            
            // Set execution parameters
            ExecutionParams params = new ExecutionParams();
            params.setPageSize(calculateOptimalPageSize(cost));
            params.setTimeout(calculateTimeout(cost));
            params.setParallelism(calculateParallelism(cost));
            plan.setExecutionParams(params);
            
            return plan;
        }
    }
}
```

#### 2. Multi-Index Query Optimization

```java
public class MultiIndexOptimizer {
    private final IndexRegistry indexRegistry;
    private final CostEstimator costEstimator;
    
    public IndexExecutionPlan optimizeIndexUsage(SelectStatement select) {
        List<SecondaryIndex> availableIndexes = findApplicableIndexes(select);
        
        if (availableIndexes.isEmpty()) {
            return IndexExecutionPlan.noIndex();
        }
        
        // Generate all possible index combinations
        List<IndexCombination> combinations = generateIndexCombinations(availableIndexes);
        
        // Evaluate each combination
        IndexCombination bestCombination = null;
        double bestCost = Double.MAX_VALUE;
        
        for (IndexCombination combination : combinations) {
            double cost = evaluateCombination(combination, select);
            if (cost < bestCost) {
                bestCost = cost;
                bestCombination = combination;
            }
        }
        
        return createExecutionPlan(bestCombination, select);
    }
    
    private double evaluateCombination(IndexCombination combination, SelectStatement select) {
        if (combination.isIntersection()) {
            return evaluateIntersection(combination, select);
        } else if (combination.isUnion()) {
            return evaluateUnion(combination, select);
        } else {
            return evaluateSingleIndex(combination.getSingleIndex(), select);
        }
    }
    
    private double evaluateIntersection(IndexCombination combination, SelectStatement select) {
        double totalCost = 0;
        double resultSize = Double.MAX_VALUE;
        
        for (SecondaryIndex index : combination.getIndexes()) {
            IndexStats stats = indexRegistry.getStats(index);
            double indexCost = stats.getAverageLookupCost();
            double selectivity = estimateSelectivity(index, select);
            
            totalCost += indexCost;
            resultSize = Math.min(resultSize, selectivity * stats.getEstimatedRows());
        }
        
        // Add merge cost for intersection
        totalCost += calculateIntersectionMergeCost(resultSize);
        
        return totalCost;
    }
    
    public class IndexIntersectionExecutor {
        public RowIterator executeIntersection(List<SecondaryIndex> indexes, 
                                              DataRange range,
                                              ColumnFilter filter) {
            // Get iterators from each index
            List<RowIterator> indexIterators = new ArrayList<>();
            for (SecondaryIndex index : indexes) {
                RowIterator iter = index.search(range, filter);
                indexIterators.add(iter);
            }
            
            // Create intersection iterator
            return new IntersectionIterator(indexIterators) {
                @Override
                protected boolean shouldInclude(Row row) {
                    // Check if row matches all index conditions
                    for (SecondaryIndex index : indexes) {
                        if (!index.matches(row)) {
                            return false;
                        }
                    }
                    return true;
                }
            };
        }
    }
}
```

#### 3. Partition Pruning Enhancement

```java
public class EnhancedPartitionPruner {
    private final TokenMetadata tokenMetadata;
    private final BloomFilterCache bloomFilterCache;
    private final PartitionStatistics statistics;
    
    public PartitionRanges prune(SelectStatement select, TableMetadata table) {
        PartitionRanges ranges = new PartitionRanges();
        
        // Apply token-based pruning
        if (select.hasTokenRestriction()) {
            ranges = pruneByToken(select.getTokenRestriction());
        }
        
        // Apply partition key pruning
        if (select.hasPartitionKeyRestrictions()) {
            ranges = pruneByPartitionKey(select, ranges);
        }
        
        // Apply bloom filter pruning
        if (canUseBloomFilter(select)) {
            ranges = pruneByBloomFilter(select, ranges);
        }
        
        // Apply statistical pruning
        if (hasStatistics(table)) {
            ranges = pruneByStatistics(select, ranges);
        }
        
        return ranges;
    }
    
    private PartitionRanges pruneByBloomFilter(SelectStatement select, PartitionRanges ranges) {
        PartitionRanges prunedRanges = new PartitionRanges();
        
        for (PartitionRange range : ranges) {
            List<SSTableReader> sstables = getSSTablesForRange(range);
            
            for (SSTableReader sstable : sstables) {
                BloomFilter filter = bloomFilterCache.get(sstable);
                
                // Check if partition might exist in SSTable
                if (mightContainPartition(filter, select)) {
                    prunedRanges.add(range);
                }
            }
        }
        
        return prunedRanges;
    }
    
    private PartitionRanges pruneByStatistics(SelectStatement select, PartitionRanges ranges) {
        PartitionRanges prunedRanges = new PartitionRanges();
        
        for (PartitionRange range : ranges) {
            // Get statistics for range
            RangeStatistics stats = statistics.getStatistics(range);
            
            // Check min/max values
            if (select.hasClusteringRestrictions()) {
                ClusteringBound min = stats.getMinClustering();
                ClusteringBound max = stats.getMaxClustering();
                
                if (overlaps(select.getClusteringBounds(), min, max)) {
                    prunedRanges.add(range);
                }
            } else {
                prunedRanges.add(range);
            }
        }
        
        return prunedRanges;
    }
    
    public class MetadataBasedPruner {
        public Set<SSTableReader> pruneSSTablesForQuery(SelectStatement select,
                                                        Set<SSTableReader> sstables) {
            Set<SSTableReader> pruned = new HashSet<>();
            
            for (SSTableReader sstable : sstables) {
                StatsMetadata stats = sstable.getSSTableMetadata();
                
                // Check time bounds for time-series data
                if (select.hasTimeRestriction()) {
                    if (overlapsTimeRange(select.getTimeRange(), 
                                        stats.minTimestamp, 
                                        stats.maxTimestamp)) {
                        pruned.add(sstable);
                    }
                }
                // Check clustering bounds
                else if (select.hasClusteringRestrictions()) {
                    if (overlapsClusteringRange(select.getClusteringBounds(),
                                              stats.minClusteringValues,
                                              stats.maxClusteringValues)) {
                        pruned.add(sstable);
                    }
                } else {
                    pruned.add(sstable);
                }
            }
            
            return pruned;
        }
    }
}
```

#### 4. Adaptive Query Execution

```java
public class AdaptiveQueryExecutor {
    private final ExecutionMonitor monitor;
    private final PlanAdjuster planAdjuster;
    private final ResourceManager resourceManager;
    
    public ResultSet executeAdaptive(SelectStatement select, QueryPlan initialPlan) {
        AdaptiveExecution execution = new AdaptiveExecution(select, initialPlan);
        
        try {
            // Start execution with initial plan
            execution.start();
            
            // Monitor and adjust during execution
            while (!execution.isComplete()) {
                ExecutionSnapshot snapshot = monitor.getSnapshot(execution);
                
                if (shouldAdjustPlan(snapshot)) {
                    QueryPlan adjustedPlan = planAdjuster.adjust(snapshot, execution.getCurrentPlan());
                    execution.updatePlan(adjustedPlan);
                }
                
                // Process next batch
                execution.processNextBatch();
            }
            
            return execution.getResults();
            
        } finally {
            execution.cleanup();
        }
    }
    
    public class AdaptiveExecution {
        private volatile QueryPlan currentPlan;
        private final ProgressTracker progressTracker;
        private final ResultCollector resultCollector;
        
        public void processNextBatch() {
            BatchSize batchSize = calculateAdaptiveBatchSize();
            
            try {
                List<Row> rows = fetchNextBatch(batchSize);
                
                // Track progress
                progressTracker.update(rows.size());
                
                // Collect results
                resultCollector.add(rows);
                
                // Update statistics
                updateExecutionStatistics(rows);
                
            } catch (TimeoutException e) {
                handleTimeout();
            }
        }
        
        private BatchSize calculateAdaptiveBatchSize() {
            // Start with default
            int size = currentPlan.getInitialBatchSize();
            
            // Adjust based on response time
            double avgResponseTime = progressTracker.getAverageResponseTime();
            if (avgResponseTime < TARGET_RESPONSE_TIME * 0.5) {
                size = (int)(size * 1.5); // Increase batch size
            } else if (avgResponseTime > TARGET_RESPONSE_TIME) {
                size = (int)(size * 0.75); // Decrease batch size
            }
            
            // Adjust based on memory pressure
            double memoryUsage = resourceManager.getMemoryUsage();
            if (memoryUsage > 0.8) {
                size = Math.min(size, MAX_BATCH_SIZE_UNDER_PRESSURE);
            }
            
            return new BatchSize(size);
        }
        
        private void handleTimeout() {
            if (progressTracker.getProcessedRows() > 0) {
                // Return partial results with warning
                resultCollector.setPartialResult(true);
                resultCollector.addWarning("Query timed out, returning partial results");
            } else {
                throw new QueryTimeoutException("Query exceeded timeout with no results");
            }
        }
    }
    
    public class PlanAdjuster {
        public QueryPlan adjust(ExecutionSnapshot snapshot, QueryPlan currentPlan) {
            QueryPlan adjusted = currentPlan.copy();
            
            // Adjust parallelism
            if (snapshot.getQueueDepth() > HIGH_QUEUE_THRESHOLD) {
                adjusted.decreaseParallelism();
            } else if (snapshot.getIdleTime() > IDLE_THRESHOLD) {
                adjusted.increaseParallelism();
            }
            
            // Adjust timeout
            double estimatedCompletion = estimateTimeToCompletion(snapshot);
            if (estimatedCompletion > currentPlan.getTimeout()) {
                adjusted.setTimeout(estimatedCompletion * 1.2);
            }
            
            // Switch index strategy if needed
            if (snapshot.getFalsePositiveRate() > FALSE_POSITIVE_THRESHOLD) {
                adjusted.disableIndexUsage();
            }
            
            return adjusted;
        }
    }
}
```

#### 5. Query Recommendation Engine

```java
public class QueryRecommendationEngine {
    private final SchemaAnalyzer schemaAnalyzer;
    private final QueryAnalyzer queryAnalyzer;
    private final StatisticsCollector statistics;
    
    public List<Recommendation> analyze(SelectStatement select, QueryExecutionStats stats) {
        List<Recommendation> recommendations = new ArrayList<>();
        
        // Analyze query pattern
        QueryPattern pattern = queryAnalyzer.analyze(select);
        
        // Check for missing indexes
        recommendations.addAll(suggestIndexes(select, pattern, stats));
        
        // Check for query improvements
        recommendations.addAll(suggestQueryImprovements(select, stats));
        
        // Check for schema improvements
        recommendations.addAll(suggestSchemaChanges(pattern, stats));
        
        return recommendations;
    }
    
    private List<Recommendation> suggestIndexes(SelectStatement select, 
                                               QueryPattern pattern,
                                               QueryExecutionStats stats) {
        List<Recommendation> indexRecommendations = new ArrayList<>();
        
        // Check if query would benefit from index
        if (stats.getRowsScanned() / stats.getRowsReturned() > 10) {
            for (ColumnRestriction restriction : select.getRestrictions()) {
                if (!hasIndex(restriction.getColumn())) {
                    double estimatedImprovement = estimateIndexImprovement(restriction, stats);
                    
                    if (estimatedImprovement > 0.3) { // 30% improvement threshold
                        indexRecommendations.add(new IndexRecommendation(
                            restriction.getColumn(),
                            estimatedImprovement,
                            calculateIndexCost(restriction.getColumn())
                        ));
                    }
                }
            }
        }
        
        return indexRecommendations;
    }
    
    private List<Recommendation> suggestQueryImprovements(SelectStatement select,
                                                         QueryExecutionStats stats) {
        List<Recommendation> improvements = new ArrayList<>();
        
        // Check for partition key usage
        if (!select.hasPartitionKeyRestrictions()) {
            improvements.add(new QueryRecommendation(
                "Add partition key restriction",
                "Query performs full table scan. Consider adding partition key filter.",
                Priority.HIGH
            ));
        }
        
        // Check for IN clause optimization
        if (select.hasLargeInClause()) {
            improvements.add(new QueryRecommendation(
                "Split large IN clause",
                "Large IN clause detected. Consider splitting into multiple queries.",
                Priority.MEDIUM
            ));
        }
        
        // Check for ALLOW FILTERING optimization
        if (select.requiresAllowFiltering()) {
            String alternative = suggestAlternativeQuery(select);
            if (alternative != null) {
                improvements.add(new QueryRecommendation(
                    "Avoid ALLOW FILTERING",
                    "Query can be rewritten to avoid ALLOW FILTERING: " + alternative,
                    Priority.HIGH
                ));
            }
        }
        
        return improvements;
    }
}
```

### Performance Considerations

1. **Query Plan Caching**: Cache plans for frequently executed queries
2. **Statistics Collection**: Asynchronous, sampling-based statistics collection
3. **Adaptive Thresholds**: Dynamic adjustment of optimization thresholds
4. **Resource Limits**: Configurable limits on planning time and memory
5. **Fallback Mechanism**: Automatic fallback to simple execution on planning failure

### Security Considerations

1. **Query Plan Access**: Restrict EXPLAIN PLAN to authorized users
2. **Statistics Privacy**: Ensure statistics don't leak sensitive data
3. **Resource Protection**: Prevent resource exhaustion through complex queries
4. **Audit Logging**: Log all query plan changes and optimization decisions

## Alternatives Considered

### Alternative 1: External Query Optimizer

**Description**: Separate query optimization service outside Cassandra.

**Why not chosen**:
- Increases deployment complexity
- Network overhead for plan exchange
- Loses tight integration with storage engine

### Alternative 2: Client-Side Optimization

**Description**: Push query optimization logic to client drivers.

**Why not chosen**:
- Duplicates logic across multiple drivers
- Limited access to server-side statistics
- Increases client complexity

### Alternative 3: Simple Rule-Based Optimization

**Description**: Basic rule-based optimizations without cost estimation.

**Why not chosen**:
- Insufficient for complex queries
- Cannot adapt to data distribution changes
- Limited improvement potential

## Migration Path

### Backward Compatibility

- All optimizations are opt-in by default
- Existing queries continue to work unchanged
- New features activated via configuration or hints

### Migration Steps

1. **Phase 1: Statistics Collection**
   ```yaml
   # Enable statistics collection
   query_optimization:
     collect_statistics: true
     statistics_sample_rate: 0.01
   ```

2. **Phase 2: Query Analysis**
   ```bash
   # Analyze existing queries
   nodetool query analyze --keyspace prod
   ```

3. **Phase 3: Gradual Enablement**
   ```yaml
   # Enable specific optimizations
   query_optimization:
     enable_cost_estimation: true
     enable_index_intersection: false
     enable_adaptive_execution: false
   ```

4. **Phase 4: Full Optimization**
   ```yaml
   # Enable all optimizations
   query_optimization:
     enabled: true
     optimization_level: full
   ```

### Rollback Plan

```yaml
# Disable all optimizations
query_optimization:
  enabled: false
  
# Or selectively disable features
query_optimization:
  enable_cost_estimation: false
```

## Testing Strategy

### Unit Tests

- Cost estimation accuracy for various query patterns
- Index selection algorithm correctness
- Partition pruning effectiveness
- Plan adjustment logic
- Statistics collection accuracy

### Integration Tests

- End-to-end query optimization flow
- Multi-index query execution
- Adaptive execution behavior
- Query plan caching
- Statistics updates

### Performance Tests

- Query latency improvement measurement
- Planning overhead assessment
- Memory usage with plan caching
- Large-scale query optimization
- Concurrent query handling

### Benchmarks

```
# Baseline (No Optimization)
Query: SELECT * FROM users WHERE country = 'US' AND age > 25
Execution Time: 1250ms
Rows Scanned: 1,000,000
Rows Returned: 5,000

# With Query Optimization
Query: SELECT * FROM users WHERE country = 'US' AND age > 25
Execution Time: 85ms (-93%)
Rows Scanned: 12,000 (-98.8%)
Rows Returned: 5,000
Plan: Index intersection (country_idx ∩ age_idx)
```

## Timeline and Milestones

| Milestone | Target Date | Description |
|-----------|------------|-------------|
| Design Review | 2025-02-01 | Complete design review |
| Statistics Engine | 2025-03-15 | Statistics collection implementation |
| Cost Estimation | 2025-04-30 | Cost-based planning complete |
| Index Optimization | 2025-06-15 | Multi-index optimization ready |
| Adaptive Execution | 2025-07-31 | Adaptive query execution complete |
| Beta Release | 2025-08-31 | Beta testing begins |
| GA Release | 2025-10-01 | Production ready in 5.3 |

## Dependencies

- Statistics collection framework
- Enhanced index infrastructure
- Query execution monitoring
- Virtual table support

## Unresolved Questions

- [ ] Should we support query hints for all optimization decisions?
- [ ] What should be the default statistics collection rate?
- [ ] How to handle statistics for rapidly changing data?
- [ ] Should we support cross-partition joins in limited cases?
- [ ] How to integrate with existing driver-side retries?

## References

- [Query Optimization in Distributed Databases](https://research.papers/distributed-query-opt)
- [Cost-Based Optimization in NoSQL Systems](https://research.papers/nosql-cbo)
- [Adaptive Query Processing Survey](https://research.papers/adaptive-query)

## Appendix

### A. Example Query Plans

```json
{
  "query": "SELECT * FROM users WHERE country = 'US' AND age > 25",
  "plan": {
    "type": "IndexIntersection",
    "indexes": ["country_idx", "age_idx"],
    "estimated_cost": 1250,
    "estimated_rows": 5000,
    "steps": [
      {
        "step": 1,
        "operation": "IndexScan",
        "index": "country_idx",
        "predicate": "country = 'US'",
        "estimated_rows": 50000
      },
      {
        "step": 2,
        "operation": "IndexScan", 
        "index": "age_idx",
        "predicate": "age > 25",
        "estimated_rows": 100000
      },
      {
        "step": 3,
        "operation": "Intersect",
        "inputs": [1, 2],
        "estimated_rows": 5000
      },
      {
        "step": 4,
        "operation": "Fetch",
        "source": "base_table",
        "rows": 5000
      }
    ]
  }
}
```

### B. Configuration Reference

```yaml
query_optimization:
  # Master switch
  enabled: true
  
  # Statistics collection
  statistics:
    enabled: true
    sample_rate: 0.01
    update_interval: 1h
    
  # Cost estimation
  cost_estimation:
    enabled: true
    cache_plans: true
    cache_size: 1000
    
  # Index optimization
  index_optimization:
    enable_intersection: true
    enable_union: true
    max_indexes_per_query: 3
    
  # Adaptive execution
  adaptive_execution:
    enabled: true
    initial_batch_size: 100
    target_response_time: 100ms
    memory_limit: 128MB
    
  # Recommendations
  recommendations:
    enabled: true
    min_query_frequency: 100
    analysis_interval: 24h