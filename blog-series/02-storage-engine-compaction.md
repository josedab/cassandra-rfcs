# Deep Dive: Apache Cassandra Storage Engine and Compaction Strategies

## Introduction

The storage engine is the heart of Apache Cassandra's performance and reliability. Understanding its architecture—from memtables to SSTables to compaction—is crucial for optimizing performance and capacity planning. This post explores the intricate design decisions and implementation details that make Cassandra's storage engine both powerful and flexible.

## Storage Layer Architecture

### Memtables: The Write Buffer

Cassandra uses memtables as an in-memory write buffer before data is persisted to disk. Version 5.0 introduced a pluggable memtable API with two primary implementations:

#### SkipListMemtable (Legacy)
- Concurrent skip list data structure
- Thread-safe without locks
- Higher GC pressure with frequent writes
- Well-tested, stable implementation

#### TrieMemtable (Modern - Recommended)
From [`cassandra.yaml`](conf/cassandra.yaml:794):
```yaml
memtable:
  configurations:
    skiplist:
      class_name: SkipListMemtable
    trie:
      class_name: TrieMemtable
    default:
      inherits: skiplist  # Conservative default
```

**TrieMemtable Advantages**:
- Off-heap metadata storage reduces GC
- Sharded single-writer architecture
- Higher write throughput
- Better memory efficiency
- More predictable performance

**Implementation Note** from [`ColumnFamilyStore.java`](src/java/org/apache/cassandra/db/ColumnFamilyStore.java:1444):
```java
public Memtable createMemtable(AtomicReference<CommitLogPosition> commitLogUpperBound)
{
    return memtableFactory.create(commitLogUpperBound, metadata, this);
}
```

### Commit Log: Durability Guarantee

The commit log provides durability for writes before they're flushed to SSTables.

**Sync Modes**:
1. **Periodic** (default): Fsync every 10 seconds
2. **Batch**: Fsync before acknowledging write
3. **Group**: Block for configurable period between fsyncs

**Direct I/O Support** (5.0+):
- Available when commit log is uncompressed and unencrypted
- Reduces memory mapping overhead
- Minimizes page cache pollution
- Better for high-throughput workloads

From [`cassandra.yaml`](conf/cassandra.yaml:689):
```yaml
commitlog_disk_access_mode: legacy  # legacy, mmap, direct, standard
```

### SSTable Format Evolution

#### BIG Format (Legacy)
- Used since Cassandra 3.0
- Partition index with index summary
- Bloom filters for existence checks
- Key cache for index summary entries
- Column index for wide partitions

#### BTI Format (Trie-Indexed - 5.0+)
The BTI format represents a fundamental improvement:

**Key Innovations**:
- Trie-based partition index (no index summary needed)
- Eliminates key cache requirement
- More efficient for partitions with millions of rows
- Smaller on-disk footprint
- Faster point queries

**Performance Impact**:
- 20-30% smaller index size
- 40-50% faster partition lookups
- No warm-up time (no key cache to populate)
- Better cache-line utilization

**Index Granularity Configuration**:
```yaml
column_index_size: 4KiB  # BIG default: 64KiB, BTI default: 16KiB
```

## Compaction: The Heart of LSM Performance

Compaction is the process of merging SSTables to reclaim space, remove deleted data, and improve read performance.

### Size-Tiered Compaction Strategy (STCS)

**Algorithm** from [`SizeTieredCompactionStrategy.java`](src/java/org/apache/cassandra/db/compaction/SizeTieredCompactionStrategy.java:116):
```java
public static List<SSTableReader> mostInterestingBucket(List<List<SSTableReader>> buckets, 
                                                        int minThreshold, 
                                                        int maxThreshold)
{
    // Buckets grouped by size similarity
    // Most interesting = largest average hotness
    // Hotness = read rate per byte
}
```

**Characteristics**:
- Best for: Insert-heavy workloads, time-series data
- Space amplification: ~50% (worst case: 100%)
- Read amplification: O(log N) SSTables
- Write amplification: ~2-3x

**Configuration**:
```yaml
compaction:
  class: SizeTieredCompactionStrategy
  options:
    min_threshold: 4      # Minimum SSTables to compact
    max_threshold: 32     # Maximum SSTables per compaction
    bucket_high: 1.5      # Size similarity factor
    bucket_low: 0.5
```

### Leveled Compaction Strategy (LCS)

From [`LeveledCompactionStrategy.java`](src/java/org/apache/cassandra/db/compaction/LeveledCompactionStrategy.java:65):

**Level Organization**:
- L0: Newly flushed SSTables (may overlap)
- L1-LN: Fixed-size, non-overlapping SSTables
- Each level is 10x the size of the previous (configurable)

**Algorithm Highlights**:
```java
public class LeveledManifest
{
    // Maximum bytes for level = fanout^level * max_sstable_size
    public long maxBytesForLevel(int level, long maxSSTableSizeInBytes)
    {
        return level == 0 ? 4 * maxSSTableSizeInBytes 
                          : (long) Math.pow(levelFanoutSize, level) * maxSSTableSizeInBytes;
    }
}
```

**Characteristics**:
- Best for: Read-heavy workloads
- Space amplification: ~10%
- Read amplification: 1 SSTable per level (typically 1-2 SSTables)
- Write amplification: ~10x (data rewritten at each level)

**Configuration**:
```yaml
compaction:
  class: LeveledCompactionStrategy
  options:
    sstable_size_in_mb: 160  # Default increased from 5MB to 160MB
    fanout_size: 10          # Level size multiplier
```

### Time Window Compaction Strategy (TWCS)

Optimized for time-series data with TTL:

From [`TimeWindowCompactionStrategy.java`](src/java/org/apache/cassandra/db/compaction/TimeWindowCompactionStrategy.java:214):
```java
public static Pair<Long,Long> getWindowBoundsInMillis(TimeUnit windowTimeUnit, 
                                                       int windowTimeSize, 
                                                       long timestampInMillis)
{
    // Creates time-based buckets for SSTables
    // Allows entire window drops when all data expires
}
```

**Characteristics**:
- Best for: Time-series with TTL, append-only workloads
- Groups SSTables by time window
- Efficient expiration of old data
- Minimal compaction overhead for aged data

**Configuration**:
```yaml
compaction:
  class: TimeWindowCompactionStrategy
  options:
    compaction_window_unit: DAYS
    compaction_window_size: 1
    max_sstable_age_days: 365
```

### Unified Compaction Strategy (UCS) - The Future

The UCS, introduced in Cassandra 5.0, represents a major evolution in compaction strategy design.

**From** [`UnifiedCompactionStrategy.md`](src/java/org/apache/cassandra/db/compaction/UnifiedCompactionStrategy.md:19):

#### Core Concepts

**1. Size-Based Levels**:
- Similar to LCS but with adaptive sizing
- Levels sized by survival ratio, not fixed fanout
- Better handles variable write patterns

**2. Density Leveling**:
- Compacts based on data density (bytes per token)
- Prevents hot spots from blocking compaction
- More uniform SSTable distribution

**3. Adaptive Sharding**:
```
Basic Sharding: Splits output by token ranges
Full Sharding: Parallel compaction of independent shards
```

**4. Output Parallelization** (5.1+):
From [`UnifiedCompactionStrategy.java`](src/java/org/apache/cassandra/db/compaction/UnifiedCompactionStrategy.java:343):
```java
private List<AbstractCompactionTask> createParallelCompactionTasks(
    LifecycleTransaction transaction, 
    long gcBefore)
{
    // Splits compaction into per-shard tasks
    // Dramatically reduces compaction duration
    // Particularly beneficial for major compactions
}
```

**Configuration**:
```yaml
compaction:
  class: UnifiedCompactionStrategy
  options:
    scaling_parameters: "T4"  # Threshold and fanout
    target_sstable_size: "1GiB"
    base_shard_count: 4
    parallelize_output_shards: true  # Enable parallel compaction
```

**Scaling Parameters**:
- `T2`: threshold=2, fanout=2 (minimal overhead)
- `T4`: threshold=4, fanout=4 (balanced, default)
- `L8`: threshold=2, fanout=8 (LCS-like)
- `N`: threshold=30, fanout=2 (STCS-like)

## Compaction Process Internals

### Compaction Manager

The [`CompactionManager`](src/java/org/apache/cassandra/db/compaction/CompactionManager.java:151) orchestrates all compaction activity:

```java
public class CompactionManager implements CompactionManagerMBean
{
    // Thread pools for different compaction types
    private final CompactionExecutor executor;
    private final ValidationExecutor validationExecutor;
    private final ViewBuildExecutor viewBuildExecutor;
    
    // Rate limiting for I/O control
    private final RateLimiter rateLimiter;
}
```

**Compaction Types**:
1. **Background**: Normal ongoing compactions
2. **Major**: User-initiated full compaction
3. **Validation**: Merkle tree building for repair
4. **Anticompaction**: Post-repair SSTable segregation
5. **Cleanup**: Remove out-of-range data
6. **Scrub**: Fix corrupted SSTables
7. **Upgrade**: Rewrite to newer SSTable format

### Compaction Iterator

The [`CompactionIterator`](src/java/org/apache/cassandra/db/compaction/CompactionIterator.java:141) efficiently merges multiple SSTables:

```java
public class CompactionIterator extends CompactionInfo.Holder 
    implements UnfilteredPartitionIterator
{
    // Merges N SSTables, purging tombstones and expired data
    // Tracks merge statistics for monitoring
    // Supports cancellation for operational flexibility
}
```

**Optimization Techniques**:
- **Zero-copy merging**: Direct buffer manipulation
- **Lazy deserialization**: Only parse needed data
- **Bloom filter short-circuits**: Skip SSTables without data
- **Tombstone purging**: Remove obsolete deletion markers

### Garbage Collection and Tombstones

**Tombstone Lifecycle**:
1. DELETE creates tombstone with timestamp
2. Tombstone preserved for `gc_grace_seconds` (default: 10 days)
3. After grace period + global consistency, tombstone eligible for removal
4. Removed during compaction if all replicas confirmed to have seen it

**Protection Mechanisms**:
From [`cassandra.yaml`](conf/cassandra.yaml:2002):
```yaml
tombstone_warn_threshold: 1000
tombstone_failure_threshold: 100000
```

**Advanced Purging** (5.0+):
```yaml
# Only purge tombstones from repaired data
compaction:
  options:
    only_purge_repaired_tombstones: true
```

## Performance Optimization

### Flush Optimization

From [`ColumnFamilyStore.java`](src/java/org/apache/cassandra/db/ColumnFamilyStore.java:1205):
```java
private final class Flush implements Runnable
{
    final OpOrder.Barrier writeBarrier;
    final Map<ColumnFamilyStore, Memtable> memtables;
    
    // Coordinates memtable switch across base table and indexes
    // Ensures atomic commit log position tracking
    // Parallelizes flush across data directories
}
```

**Flush Triggers**:
- Memtable size threshold
- Time-based expiration
- Commit log pressure
- Manual operator intervention

### Compaction Throttling

**Dynamic Rate Limiting**:
```java
protected void compactionRateLimiterAcquire(RateLimiter limiter, 
                                            long bytesScanned,
                                            long lastBytesScanned, 
                                            double compressionRatio)
{
    double bytesToThrottle = (bytesScanned - lastBytesScanned) * compressionRatio;
    while (bytesToThrottle >= 1024)
    {
        limiter.acquire(1024);
        bytesToThrottle -= 1024;
    }
}
```

### SSTable Preemptive Opening

Enables reading from SSTables before compaction completes:
```yaml
sstable_preemptive_open_interval: 50MiB
```

**Benefits**:
- Smoother transition between old and new SSTables
- Reduced page cache churn
- Maintains "hot" data accessibility during compaction

## Compaction Strategy Selection Guide

### Decision Matrix

| Workload Pattern | Recommended Strategy | Reasoning |
|-----------------|---------------------|-----------|
| Heavy writes, few reads | STCS | Lower write amplification |
| Read-heavy, bounded dataset | LCS | Predictable read performance |
| Time-series with TTL | TWCS | Efficient expiration |
| Mixed workload, modern cluster | UCS | Adaptive to changing patterns |
| High update rate | UCS with density leveling | Handles overwritten data efficiently |

### Migration Strategies

**STCS → LCS**:
```bash
# Requires significant I/O during transition
nodetool setcompactionstrategy keyspace table LeveledCompactionStrategy
# All SSTables will be reorganized into levels
```

**Any → UCS**:
```bash
# Smoother transition, UCS adapts to existing SSTable distribution
nodetool setcompactionstrategy keyspace table UnifiedCompactionStrategy \
  scaling_parameters=T4
```

## Advanced Compaction Features

### 1. **Garbage Collection Compaction** (4.0+)

Proactively remove deleted data by consulting overlapping SSTables:

```bash
nodetool garbagecollect -g CELL keyspace table
```

**Granularity Levels**:
- `ROW`: Discard fully deleted rows
- `CELL`: Discard individual deleted/overwritten cells
- `NONE`: Standard compaction behavior

### 2. **User-Defined Compaction**

Force compaction of specific SSTables:
```bash
nodetool compact --user-defined keyspace table sstable1 sstable2
```

### 3. **Subrange Compaction**

Compact specific token ranges:
```bash
nodetool compact -st <start_token> -et <end_token> keyspace table
```

### 4. **Major Compaction Parallelization** (UCS, 5.1+)

From [`UnifiedCompactionStrategy.java`](src/java/org/apache/cassandra/db/compaction/UnifiedCompactionStrategy.java:343):
```bash
# Control parallelism for major compactions
nodetool compact --jobs 4 keyspace table
```

## Monitoring Compaction

### Key Metrics

**JMX MBeans**:
```
org.apache.cassandra.metrics:type=Compaction
  - PendingTasks: Estimated remaining compactions
  - CompletedTasks: Total compactions completed
  - BytesCompacted: Total data processed
  - TotalCompactionsCompleted: Historical count
```

**nodetool Commands**:
```bash
# Current compaction status
nodetool compactionstats

# Compaction history
nodetool compactionhistory

# Per-table compaction parameters
nodetool getcompactionstrategy keyspace table
```

### Identifying Issues

**Too Many Pending Compactions**:
- Insufficient `concurrent_compactors`
- Low `compaction_throughput`
- Undersized SSTables for LCS
- Write rate exceeds compaction rate

**Compaction Stalls**:
- Disk space exhaustion
- JVM GC pressure
- Large partition processing
- Insufficient I/O capacity

## Best Practices

### 1. **Choose Appropriate Compaction Strategy**

```sql
-- Write-optimized table
CREATE TABLE metrics.data_points (
    sensor_id uuid,
    timestamp timestamp,
    value double,
    PRIMARY KEY (sensor_id, timestamp)
) WITH compaction = {'class': 'TimeWindowCompactionStrategy',
                     'compaction_window_unit': 'HOURS',
                     'compaction_window_size': 1};

-- Read-optimized table
CREATE TABLE users.profiles (
    user_id uuid PRIMARY KEY,
    name text,
    email text
) WITH compaction = {'class': 'UnifiedCompactionStrategy',
                     'scaling_parameters': 'L8'};
```

### 2. **Monitor Disk Space**

Compaction requires temporary disk space:
- STCS: Up to 50% of data size
- LCS: ~10% of level being compacted
- TWCS: Window size
- UCS: Configurable, typically 20-30%

### 3. **Tune for Your Hardware**

**SSD-Based Clusters**:
```yaml
concurrent_compactors: <num_cores>
compaction_throughput: 0  # No throttling
disk_optimization_strategy: ssd
```

**HDD-Based Clusters**:
```yaml
concurrent_compactors: <num_disks>
compaction_throughput: 64MiB/s
disk_optimization_strategy: spinning
```

### 4. **Leverage UCS for Modern Deployments**

The Unified Compaction Strategy should be the default choice for:
- New clusters (Cassandra 5.0+)
- Mixed workloads
- Clusters with evolving access patterns
- Production deployments requiring operational simplicity

## Troubleshooting Common Issues

### Issue: Read Performance Degradation

**Diagnosis**:
```bash
nodetool tablestats keyspace.table | grep "SSTable count"
```

**Solutions**:
- If high SSTable count: Increase `compaction_throughput`, check for pending compactions
- If low but slow: Check partition cache hit rate, enable row cache for hot data
- Consider migrating to BTI format for faster lookups

### Issue: Disk Space Pressure

**Diagnosis**:
```bash
nodetool compactionstats
df -h /var/lib/cassandra/data
```

**Solutions**:
- Reduce `compaction_throughput` to slow disk consumption
- Free space by running `nodetool cleanup` after topology changes
- Migrate from STCS to LCS/UCS for lower space amplification
- Evaluate `gc_grace_seconds` - shorter = more aggressive tombstone removal

### Issue: Write Amplification

**Symptoms**: High disk I/O despite moderate write load

**Solutions**:
- STCS: Acceptable, by design
- LCS: Consider UCS or TWCS if inappropriate workload
- UCS: Tune `scaling_parameters` for lower fanout
- Evaluate update patterns - high updates favor fewer levels

## Future Directions

### Compaction Improvements (Upcoming)

1. **Continuous Compaction**: Background compaction without explicit tasks
2. **ML-Driven Strategy Selection**: Automatic strategy selection based on workload analysis
3. **Cross-Node Compaction Coordination**: Reduce cluster-wide compaction overhead
4. **Tiered Storage Support**: Automatic migration to cold storage

## Conclusion

Cassandra's storage engine and compaction strategies represent sophisticated solutions to the fundamental challenges of LSM-tree databases. The evolution from multiple specialized strategies (STCS, LCS, TWCS) to the unified strategy demonstrates maturation of understanding about real-world workload requirements.

Key takeaways:
- **Memtable choice matters**: TrieMemtable offers substantial benefits for most workloads
- **BTI format**: Significant improvement, plan migration from BIG
- **UCS is the future**: Unless you have very specific requirements, UCS should be default
- **Compaction tuning is iterative**: Monitor, measure, adjust based on actual behavior
- **Space management is critical**: Plan for compaction temporary space requirements

Understanding these components enables effective capacity planning, performance optimization, and operational excellence in Cassandra deployments.

---

**Next in Series**: Part 3 explores Cassandra's distributed systems aspects—gossip protocol, replication, consistency guarantees, and failure handling.

**References**:
- [Unified Compaction Strategy Documentation](src/java/org/apache/cassandra/db/compaction/UnifiedCompactionStrategy.md)
- [CASSANDRA-18397](https://issues.apache.org/jira/browse/CASSANDRA-18397) - UCS Implementation
- [CEP-26](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-26) - Unified Compaction Strategy