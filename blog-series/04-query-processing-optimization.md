# Apache Cassandra Query Processing and Performance Optimization

## Introduction

Cassandra's query processing engine is designed for horizontal scalability and predictable performance. Unlike traditional relational databases that optimize for complex joins and aggregations, Cassandra's query engine focuses on fast, partition-oriented access patterns. This post explores CQL query processing, performance optimization techniques, and best practices for achieving optimal throughput and latency.

## CQL: The Query Language

Cassandra Query Language (CQL) provides an SQL-like interface while respecting the underlying distributed architecture.

**Current Version**: CQL 3.4.8 (Cassandra 5.1)

### Fundamental Differences from SQL

**Cassandra Doesn't Support**:
- Cross-partition transactions (outside Accord)
- Distributed joins
- Arbitrary WHERE clauses without indexes
- Subqueries
- Foreign keys

**Cassandra Excels At**:
- Single-partition queries
- Range scans on clustering columns
- Batch operations within a partition
- Time-series queries
- High-throughput writes

## Query Execution Architecture

### Query Coordinator

Every query has a coordinator node (the one receiving the request):

**Coordinator Responsibilities**:
1. Parse and validate CQL statement
2. Determine required replicas using token ring
3. Send read/write requests to replicas
4. Collect and merge responses
5. Perform read repair if needed
6. Return results to client

### Read Path Internals

**Single-Partition Read**:
```sql
SELECT * FROM users.profiles WHERE user_id = 12345;
```

**Execution Steps**:
1. **Route Calculation**: Hash partition key to find replicas
2. **Replica Selection**: Choose based on consistency level and snitch
3. **Local Read** (if coordinator is replica):
   - Check row cache
   - Query memtables (active + flushing)
   - Query SSTables using bloom filters and indexes
   - Merge results by timestamp
4. **Remote Reads**: Send requests to other replicas
5. **Digest Comparison**: Check consistency (for CL > ONE)
6. **Response**: Return merged data to client

**Multi-Partition Read**:
```sql
SELECT * FROM users.profiles WHERE user_id IN (123, 456, 789);
```

**Token-Aware Routing Optimization**:
- Driver knows token ring topology
- Sends subqueries to optimal coordinators
- Reduces network hops
- Parallelizes cross-partition access

### Write Path Internals

**From** [`ColumnFamilyStore.java`](src/java/org/apache/cassandra/db/ColumnFamilyStore.java:1498):
```java
public void apply(PartitionUpdate update, 
                  CassandraWriteContext context, 
                  boolean updateIndexes)
{
    long start = nanoTime();
    OpOrder.Group opGroup = context.getGroup();
    CommitLogPosition commitLogPosition = context.getPosition();
    
    // Write to memtable
    Memtable mt = data.getMemtableFor(opGroup, commitLogPosition);
    UpdateTransaction indexer = newUpdateTransaction(update, context, 
                                                    updateIndexes, mt);
    long timeDelta = mt.put(update, indexer, opGroup);
    
    // Invalidate cached partition
    invalidateCachedPartition(update.partitionKey());
    
    // Update metrics
    metric.writeLatency.addNano(nanoTime() - start);
}
```

**Write Execution**:
1. **Parse and Validate**: Check schema and constraints
2. **Partition Routing**: Calculate replica set
3. **Commit Log Write**: Durable append (if enabled)
4. **Memtable Write**: In-memory buffer update
5. **Secondary Index Update**: Synchronous index maintenance
6. **Hints** (on timeout): Store for failed replicas
7. **Response**: Acknowledge after CL satisfied

## Secondary Indexes

### Legacy Table-Backed Indexes

**Implementation**:
- Hidden table per indexed column
- Partition key = indexed column value
- Clustering key = original partition key

**Limitations**:
- High write amplification (2x)
- Requires ALLOW FILTERING for low-cardinality columns
- Separate SSTables to maintain

### Storage-Attached Indexes (SAI) - 5.0+

**Architecture**:
- Indexes attached to SSTable data
- Efficient intersection queries
- Vector similarity search support

**From** [`NEWS.txt`](NEWS.txt:239):
> Added a new secondary index implementation, Storage-Attached Indexes (SAI)

**Performance Advantages**:
- 50-80% less storage overhead
- 2-5x faster index queries
- Better compaction integration
- Support for multiple index types

**Creating SAI Indexes**:
```sql
CREATE CUSTOM INDEX user_email_idx 
ON users.profiles (email)
USING 'StorageAttachedIndex';

-- With analyzer for text search
CREATE CUSTOM INDEX user_bio_idx
ON users.profiles (bio)
USING 'StorageAttachedIndex'
WITH OPTIONS = {
    'case_sensitive': 'false',
    'normalize': 'true',
    'ascii': 'true'
};

-- Vector similarity search
CREATE CUSTOM INDEX embedding_idx
ON ml.documents (embedding)
USING 'StorageAttachedIndex';

SELECT * FROM ml.documents
ORDER BY embedding ANN OF [0.1, 0.2, ...]
LIMIT 10;
```

## Performance Optimization Techniques

### 1. **Caching Strategies**

#### Row Cache
```yaml
# Cache entire partitions
row_cache_size: 512MiB  # Off-heap
```

**Configuration per table**:
```sql
ALTER TABLE users.profiles 
WITH caching = {'keys': 'ALL', 'rows_per_partition': '100'};
```

**When to Use**:
- Hot partitions accessed frequently
- Partition fits in cache (< cache entry limit)
- Read-heavy workload
- Bounded dataset

#### Key Cache
**Only relevant for BIG format SSTables**:
```yaml
key_cache_size: 100MiB
key_cache_save_period: 4h
```

**With BTI Format**: Key cache unnecessary (index kept in memory)

#### Counter Cache
```yaml
counter_cache_size: 50MiB  # For counter columns only
```

### 2. **Partition Design**

**Optimal Partition Size**:
- Target: 10MB - 100MB per partition
- Warning: > 100MB
- Maximum practical: ~2GB

**Wide Partition Example** (Good):
```sql
CREATE TABLE metrics.time_series (
    sensor_id uuid,
    bucket date,  -- Partition per day
    timestamp timestamp,
    value double,
    PRIMARY KEY ((sensor_id, bucket), timestamp)
);
```

**Avoiding Hot Partitions**:
```sql
-- Bad: Single partition for all users
CREATE TABLE bad.global_counter (
    counter_name text PRIMARY KEY,
    value counter
);

-- Good: Distributed counter
CREATE TABLE good.distributed_counter (
    counter_name text,
    shard int,  -- 0-99
    value counter,
    PRIMARY KEY ((counter_name, shard))
);
```

### 3. **Query Optimization**

#### Use Token-Aware Drivers
```python
# Python driver example
from cassandra.cluster import Cluster
from cassandra.policies import TokenAwarePolicy, DCAwareRoundRobinPolicy

cluster = Cluster(
    contact_points=['node1', 'node2'],
    load_balancing_policy=TokenAwarePolicy(
        DCAwareRoundRobinPolicy(local_dc='DC1')
    )
)
```

#### Prepared Statements
**Always use for repeated queries**:
```java
// Parse once, execute many times
PreparedStatement ps = session.prepare(
    "SELECT * FROM users.profiles WHERE user_id = ?"
);

// Execution is much faster
session.execute(ps.bind(userId));
```

**Benefits**:
- Avoid repeated parsing (cached server-side)
- Reduced network overhead
- Protection against injection attacks

#### Pagination
```sql
-- Automatic paging (driver handles)
SELECT * FROM large.table WHERE partition_key = 'x';  -- Uses paging internally

-- Manual paging
SELECT * FROM large.table 
WHERE partition_key = 'x' 
LIMIT 100;  -- Fetch in batches
```

### 4. **Batch Optimization**

**Logged Batches** (Atomic across partitions):
```sql
BEGIN BATCH
  INSERT INTO users.profiles (user_id, name) VALUES (1, 'Alice');
  INSERT INTO users.activity (user_id, event) VALUES (1, 'signup');
APPLY BATCH;
```

**Warning from** [`cassandra.yaml`](conf/cassandra.yaml:2042):
```yaml
batch_size_warn_threshold: 5KiB
batch_size_fail_threshold: 50KiB  # Prevents oversized batches
```

**Best Practices**:
- Use UNLOGGED batches for same-partition writes
- Limit batch size to avoid coordinator overhead
- Batch for atomicity, not performance
- Multiple partitions = multiple nodes = coordination overhead

### 5. **Clustering Column Ordering**

**Schema Design**:
```sql
CREATE TABLE messages.by_user (
    user_id uuid,
    message_time timestamp,
    message_id uuid,
    content text,
    PRIMARY KEY (user_id, message_time, message_id)
) WITH CLUSTERING ORDER BY (message_time DESC, message_id ASC);
```

**Efficient Queries**:
```sql
-- Range scan on clustering columns (efficient)
SELECT * FROM messages.by_user
WHERE user_id = 'xxx'
  AND message_time > '2024-01-01'
  AND message_time < '2024-02-01';

-- Leverages clustering order for sorting
SELECT * FROM messages.by_user
WHERE user_id = 'xxx'
ORDER BY message_time DESC
LIMIT 10;  -- No sorting needed!
```

## Read/Write Performance Tuning

### Read Timeouts and Thresholds (5.0+)

**From** [`cassandra.yaml`](conf/cassandra.yaml:2222):
```yaml
read_thresholds_enabled: false  # Scheduled for true in 4.2
coordinator_read_size_warn_threshold: 10MiB
coordinator_read_size_fail_threshold: 100MiB
local_read_size_warn_threshold: 5MiB
local_read_size_fail_threshold: 50MiB
```

**Benefits**:
- Prevents runaway queries
- Client warnings before failures
- Protection against OOM
- Operational visibility

### Request Timeouts

**Configuration**:
```yaml
read_request_timeout: 5000ms
range_request_timeout: 10000ms
write_request_timeout: 10000ms
counter_write_request_timeout: 1000ms
```

**Tuning Guidance**:
- Set based on p99 latency + headroom
- Too low: Unnecessary timeouts
- Too high: Slow failure detection
- Monitor actual latency distributions

### Speculation and Hedging

**Speculative Retry**:
```sql
ALTER TABLE latency.critical
WITH speculative_retry = '99PERCENTILE';
```

**How It Works**:
1. Send initial read to replica set
2. If p99 latency exceeded, send retry to another replica
3. Use first successful response
4. Reduces tail latency at cost of increased load

## Advanced Query Features

### 1. **Collections**

```sql
-- Sets, Lists, Maps
CREATE TABLE social.user_data (
    user_id uuid PRIMARY KEY,
    interests set<text>,
    follower_timeline list<uuid>,
    attributes map<text, text>
);

-- Efficient operations
UPDATE social.user_data 
SET interests = interests + {'hiking', 'photography'}
WHERE user_id = 'xxx';
```

### 2. **User-Defined Types (UDTs)**

```sql
CREATE TYPE contact_info (
    email text,
    phone text,
    address frozen<address_type>
);

CREATE TABLE users.detailed_profiles (
    user_id uuid PRIMARY KEY,
    contact frozen<contact_info>
);

-- Query UDT fields
SELECT contact.email FROM users.detailed_profiles 
WHERE user_id = 'xxx';
```

### 3. **Materialized Views** (Experimental)

**From** [`NEWS.txt`](NEWS.txt:946):
> Following a discussion regarding concerns about the design and safety of Materialized Views, the C* development community no longer recommends them for production use.

**Alternative**: Maintain denormalized tables in application code

### 4. **Lightweight Transactions (LWTs)**

```sql
-- Conditional insert
INSERT INTO users.profiles (user_id, name)
VALUES ('xxx', 'Alice')
IF NOT EXISTS;

-- Conditional update
UPDATE users.profiles
SET status = 'active'
WHERE user_id = 'xxx'
IF status = 'pending';
```

**Performance Characteristics**:
- Paxos V1: 4 round-trips, high latency
- Paxos V2 (5.0+): 2 round-trips for uncontended writes
- Use sparingly for critical consistency requirements only

### 5. **Filtering Queries** (Use Carefully)

```sql
-- Requires ALLOW FILTERING
SELECT * FROM users.profiles
WHERE age > 25
ALLOW FILTERING;
```

**Performance Impact**:
- Scans entire partition or table
- No query planner to optimize
- Unbounded resource consumption
- Use secondary indexes instead

**Guardrail** (4.1+):
```yaml
allow_filtering_enabled: true  # Can disable to prevent abuse
```

## Storage-Attached Indexes (SAI) Optimization

### Index Design

**String Indexes**:
```sql
-- Exact match
CREATE CUSTOM INDEX email_idx ON users.profiles (email)
USING 'StorageAttachedIndex';

-- Text search with analyzer
CREATE CUSTOM INDEX bio_idx ON users.profiles (bio)
USING 'StorageAttachedIndex'
WITH OPTIONS = {
    'case_sensitive': 'false',
    'normalize': 'true',
    'ascii': 'true'
};
```

**Numeric Indexes**:
```sql
CREATE CUSTOM INDEX age_idx ON users.profiles (age)
USING 'StorageAttachedIndex';

-- Range queries supported
SELECT * FROM users.profiles WHERE age > 21 AND age < 65;
```

**Vector Indexes** (5.0+):
```sql
CREATE CUSTOM INDEX doc_embedding_idx 
ON ml.documents (embedding)
USING 'StorageAttachedIndex';

-- ANN similarity search
SELECT * FROM ml.documents
ORDER BY embedding ANN OF [0.1, 0.2, 0.3, ...]
LIMIT 10;
```

### Multi-Index Queries

**Intersection Optimization**:
```sql
-- Efficiently uses multiple indexes
SELECT * FROM users.profiles
WHERE country = 'USA' AND age > 25;
```

**From SAI Documentation**:
- Automatically selects most selective index
- Intersects results from multiple indexes
- Replica filtering protection ensures consistency
- Tracks index intersection overhead

### SAI Configuration

**From** [`cassandra.yaml`](conf/cassandra.yaml:1977):
```yaml
sai_options:
  segment_write_buffer_size: 1024MiB  # Index building memory
  prioritize_over_legacy_index: false  # Migration safety
```

## Performance Monitoring

### Key Metrics

**Read Metrics**:
```
org.apache.cassandra.metrics:type=ClientRequest,scope=Read,name=Latency
- Mean, P50, P95, P99, P999
- Critical for understanding user experience
```

**Write Metrics**:
```
org.apache.cassandra.metrics:type=ClientRequest,scope=Write,name=Latency
- Generally more consistent than reads
- Watch for spikes indicating memtable pressure
```

**Table-Level Metrics**:
```bash
nodetool tablestats keyspace.table
```

**Important Fields**:
- SSTable count: Higher = slower reads
- Read latency: P99 most critical
- Bloom filter false positives: Should be < 1%
- Compaction activity: Pending tasks

### Tracing Queries

**Enable query tracing**:
```sql
TRACING ON;
SELECT * FROM keyspace.table WHERE pk = 'xxx';
```

**Output Includes**:
- Coordinator operations
- Read repair decisions
- Replica response times
- Index usage
- Tombstones encountered

**Programmatic Tracing**:
```yaml
# Probabilistic tracing
slow_query_log_timeout: 500ms  # Log slow queries
```

```bash
# Set trace probability
nodetool settraceprobability 0.01  # Trace 1% of queries
```

## Query Optimization Patterns

### Pattern 1: Denormalization

**Cassandra-Idiomatic Design**:
```sql
-- Maintain multiple tables for different query patterns
CREATE TABLE messages.by_user (
    user_id uuid,
    message_time timestamp,
    message_id uuid,
    content text,
    PRIMARY KEY (user_id, message_time)
);

CREATE TABLE messages.by_channel (
    channel_id uuid,
    message_time timestamp,
    user_id uuid,
    content text,
    PRIMARY KEY (channel_id, message_time)
);

-- Update both in a batch (logged for atomicity)
BEGIN BATCH
  INSERT INTO messages.by_user (...);
  INSERT INTO messages.by_channel (...);
APPLY BATCH;
```

### Pattern 2: Bucketing Large Partitions

**Time-Based Bucketing**:
```sql
CREATE TABLE events.user_activity (
    user_id uuid,
    bucket date,  -- Year-month bucket
    event_time timestamp,
    event_type text,
    details text,
    PRIMARY KEY ((user_id, bucket), event_time)
);
```

**Benefits**:
- Bounds partition size
- Parallel query across buckets (in application)
- Efficient compaction and expiration

### Pattern 3: Counter Tables

```sql
CREATE TABLE analytics.page_views (
    page_url text,
    view_date date,
    view_count counter,
    PRIMARY KEY (page_url, view_date)
);

-- Increment atomically
UPDATE analytics.page_views
SET view_count = view_count + 1
WHERE page_url = '/home' AND view_date = '2024-01-15';
```

**Counter Consistency**:
- Eventually consistent
- Over-counting possible during failures (Paxos V1)
- Improved accuracy with Paxos V2

## Guardrails and Protection (4.1+)

**Query Protection**:
```yaml
# Partition key restrictions
partition_keys_in_select_warn_threshold: 10
partition_keys_in_select_fail_threshold: 100

# Collection size limits
collection_size_warn_threshold: 1MiB
collection_size_fail_threshold: 10MiB

# Page size limits
page_size_warn_threshold: 10000
page_size_fail_threshold: 100000
```

**Preventing Abuse**:
```yaml
# Disable dangerous features
allow_filtering_enabled: true  # Can set to false
drop_truncate_table_enabled: true
use_statements_enabled: true
```

## Query Performance Checklist

### ✓ **Schema Design**
- [ ] Partition key chosen for even distribution
- [ ] Clustering columns support query patterns
- [ ] Partition size bounded (< 100MB)
- [ ] Denormalization for multiple access patterns
- [ ] Collections appropriately used (or avoided)

### ✓ **Index Strategy**
- [ ] Primary key queries don't need indexes
- [ ] SAI for secondary access patterns
- [ ] Vector indexes for similarity search
- [ ] Avoid indexes on high-cardinality columns without selective filters

### ✓ **Query Patterns**
- [ ] Minimize use of ALLOW FILTERING
- [ ] Prepared statements for repeated queries
- [ ] Appropriate consistency levels
- [ ] Pagination for large result sets
- [ ] Token-aware routing enabled in driver

### ✓ **Caching**
- [ ] Row cache for hot partitions
- [ ] Key cache disabled if using BTI format
- [ ] Counter cache for high-traffic counters
- [ ] Monitor cache hit rates

### ✓ **Operational**
- [ ] Compaction keeping up with writes
- [ ] No excessive tombstones
- [ ] Regular repairs scheduled
- [ ] Disk space headroom maintained
- [ ] JVM properly tuned

## Troubleshooting Query Performance

### Issue: Slow Partition Queries

**Diagnosis**:
```bash
# Check SSTable count
nodetool tablestats keyspace.table | grep "SSTable count"

# Check for wide partitions
nodetool cfhistograms keyspace table
```

**Solutions**:
- High SSTable count: Force compaction or tune strategy
- Wide partitions: Redesign with bucketing
- No row cache: Enable if partitions frequently accessed

### Issue: Slow Range Scans

**Symptoms**: Queries timing out with `range_request_timeout`

**Analysis**:
```sql
-- Enable tracing to see execution
TRACING ON;
SELECT * FROM keyspace.table 
WHERE pk = 'xxx' 
AND ck > 'start' AND ck < 'end';
```

**Solutions**:
- Reduce LIMIT
- Add more selective clustering restrictions
- Enable speculative retry
- Increase `range_request_timeout` if queries legitimately slow

### Issue: High Read Latency (p99)

**Diagnosis**:
```bash
# Table-level metrics
nodetool tablehistograms keyspace table

# System-level
nodetool proxyhistograms
```

**Common Causes**:
1. **GC Pressure**: Tune JVM, reduce heap usage
2. **Disk I/O**: Check `iostat`, consider SSDs
3. **Compaction**: Balance concurrent_compactors and compaction_throughput
4. **Network**: Cross-DC queries, use LOCAL_QUORUM
5. **Cache Misses**: Enable row cache for hot data

## Advanced Features

### 1. **Function Support**

**Built-in Functions**:
```sql
-- Aggregates
SELECT COUNT(*), MAX(timestamp), MIN(value) 
FROM metrics.data_points
WHERE sensor_id = 'xxx';

-- Scalar functions
SELECT writetime(column), ttl(column) FROM ...;

-- Date/time functions
SELECT toDate(timeuuid_col), now() FROM ...;

-- Math functions (5.0+)
SELECT abs(value), exp(value), log10(value), round(value, 2) FROM ...;
```

**User-Defined Functions (UDFs)**:
```sql
-- Careful: Security implications
CREATE FUNCTION avg_state(state tuple<int,bigint>, val int)
RETURNS tuple<int,bigint>
LANGUAGE java
AS '
  if (val != null) {
    state.setInt(0, state.getInt(0) + 1);
    state.setLong(1, state.getLong(1) + val.intValue());
  }
  return state;
';
```

### 2. **GROUP BY Support** (3.10+)

```sql
SELECT sensor_id, bucket, MAX(value)
FROM metrics.data_points
WHERE sensor_id IN ('s1', 's2', 's3')
GROUP BY sensor_id, bucket;
```

**Limitations**:
- All partition keys must be restricted
- Clustering columns can be grouped
- Performance depends on data distribution

### 3. **Dynamic Data Masking** (5.0+)

```sql
-- Attach masking function to column
ALTER TABLE users.profiles 
ALTER email SET MASKED WITH mask_inner(2, 2, '@');

-- Regular users see masked data
SELECT email FROM users.profiles;  -- Returns 'al***@ex*****.com'

-- Users with UNMASK permission see real data
```

## Benchmarking with cassandra-stress

**Basic Load Test**:
```bash
cassandra-stress write n=1000000 \
  -node node1,node2,node3 \
  -rate threads=50
```

**Custom Profile**:
```yaml
# profile.yaml
keyspace: test
table: users

columnspec:
  - name: user_id
    population: uniform(1..1000000)
  - name: name
    size: uniform(10..30)

queries:
  read:
    cql: SELECT * FROM users WHERE user_id = ?
    fields: samerow
```

```bash
cassandra-stress user profile=profile.yaml \
  ops\(insert=1,read=3\) \
  -node node1,node2,node3
```

## Best Practices Summary

### Query Design
1. **Know your partition**: Every query should specify partition key
2. **Leverage clustering**: Use for range scans and ordering
3. **Minimize cross-partition**: Batch reads in application layer
4. **Use indexes wisely**: SAI for secondary access patterns
5. **Prepare statements**: Always for production applications

### Performance Optimization
1. **Cache appropriately**: Row cache for hot data only
2. **Monitor tombstones**: High tombstone counts kill performance
3. **Tune compaction**: Match strategy to workload
4. **Enable speculation**: For latency-sensitive applications
5. **Use LOCAL_QUORUM**: In multi-DC deployments

### Operational Excellence
1. **Run regular repairs**: Weekly minimum
2. **Monitor compaction**: Ensure it's keeping up
3. **Track metrics**: p99 latency and throughput
4. **Load test**: Before production deployment
5. **Use guardrails**: Prevent resource exhaustion

## Conclusion

Cassandra's query processing is fundamentally different from traditional SQL databases, optimized for the realities of distributed systems. Understanding the execution model—from coordinator routing to replica selection to result merging—is essential for building high-performance applications.

The evolution from basic secondary indexes to SAI, from Paxos V1 to V2, and from manual configuration to automated guardrails demonstrates Cassandra's maturation as a platform. Modern Cassandra (5.0+) offers significantly better query performance while maintaining its core distributed systems strengths.

Key principles:
- **Query-first data modeling**: Design schema for access patterns
- **Embrace denormalization**: Multiple tables for different queries
- **Understand distributed costs**: Cross-partition operations are expensive
- **Use appropriate tools**: SAI for indexes, UCS for compaction, prepared statements for queries
- **Monitor continuously**: Performance degrades gradually without vigilance

---

**Next in Series**: Part 5 explores advanced operational features including repair strategies, hints management, and cluster maintenance.

**References**:
- [CQL Documentation](https://cassandra.apache.org/doc/latest/cassandra/cql/)
- [CEP-7](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-7) - Storage Attached Indexes
- [CASSANDRA-16850](https://issues.apache.org/jira/browse/CASSANDRA-16850) - Read Thresholds