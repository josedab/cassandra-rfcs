# Apache Cassandra Schema Design Patterns and Best Practices

## Introduction

Data modeling in Apache Cassandra requires a fundamental shift in thinking from traditional relational databases. Instead of normalizing data and relying on joins, Cassandra demands query-first design with denormalization and careful partition key selection. This post explores proven schema design patterns, anti-patterns to avoid, and best practices for building scalable, performant applications on Cassandra.

## Core Principles

### 1. **Query-First Design**

**Unlike RDBMS**: Don't start with entities and relationships
**Cassandra Approach**: Start with queries, then design schema

**Process**:
1. List all application queries
2. Identify query patterns and access frequencies
3. Design tables to support specific queries
4. Accept data duplication for query efficiency

### 2. **Partition Key Selection**

**The Most Critical Decision**:
```sql
-- Good: Even distribution, bounded partitions
CREATE TABLE sensor_data (
    sensor_id uuid,
    bucket date,          -- Partition per sensor per day
    timestamp timestamp,
    value double,
    PRIMARY KEY ((sensor_id, bucket), timestamp)
);

-- Bad: Single partition (unbounded growth)
CREATE TABLE all_sensor_data (
    data_type text,       -- Always 'sensor'
    timestamp timestamp,
    sensor_id uuid,
    value double,
    PRIMARY KEY (data_type, timestamp)
);
```

**Partition Key Goals**:
- Even data distribution across cluster
- Bounded partition size (< 100MB)
- Aligns with most frequent queries
- Avoids hot spots

### 3. **Clustering Columns for Sorting**

```sql
CREATE TABLE user_messages (
    user_id uuid,
    message_time timestamp,
    message_id uuid,
    sender uuid,
    content text,
    PRIMARY KEY (user_id, message_time, message_id)
) WITH CLUSTERING ORDER BY (message_time DESC, message_id ASC);
```

**Benefits**:
- Data stored in sorted order on disk
- Range queries are efficient
- No additional sorting required
- Supports efficient time-series queries

## Common Data Modeling Patterns

### Pattern 1: Time-Series Data

**Bucketed Time-Series**:
```sql
CREATE TABLE metrics.measurements (
    sensor_id uuid,
    year_month int,       -- 202401, 202402, etc.
    measurement_time timestamp,
    temperature double,
    humidity double,
    PRIMARY KEY ((sensor_id, year_month), measurement_time)
) WITH CLUSTERING ORDER BY (measurement_time DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_unit': 'DAYS',
                    'compaction_window_size': 1}
  AND default_time_to_live = 7776000;  -- 90 days
```

**Query Patterns Supported**:
```sql
-- Latest readings for sensor
SELECT * FROM metrics.measurements
WHERE sensor_id = ? AND year_month = 202401
ORDER BY measurement_time DESC
LIMIT 100;

-- Range query within bucket
SELECT * FROM metrics.measurements
WHERE sensor_id = ?
  AND year_month = 202401
  AND measurement_time > ?
  AND measurement_time < ?;
```

### Pattern 2: Event Logging

**Immutable Event Store**:
```sql
CREATE TABLE events.user_activity (
    user_id uuid,
    activity_date date,
    event_time timeuuid,  -- Use timeuuid for ordering
    event_type text,
    details map<text, text>,
    PRIMARY KEY ((user_id, activity_date), event_time)
) WITH CLUSTERING ORDER BY (event_time DESC);
```

**Advantages**:
- Append-only writes (no updates)
- Time-ordered retrieval
- Efficient TTL-based expiration
- Compatible with TWCS compaction

### Pattern 3: Inverted Index

**Manual Index Implementation**:
```sql
-- Main table
CREATE TABLE documents.content (
    doc_id uuid PRIMARY KEY,
    title text,
    body text,
    tags set<text>
);

-- Inverted index for tag search
CREATE TABLE documents.by_tag (
    tag text,
    doc_id uuid,
    title text,
    PRIMARY KEY (tag, doc_id)
);

-- Application maintains both tables
BEGIN BATCH
  INSERT INTO documents.content (doc_id, title, tags) 
    VALUES (?, ?, {'cassandra', 'database'});
  INSERT INTO documents.by_tag (tag, doc_id, title) 
    VALUES ('cassandra', ?, ?);
  INSERT INTO documents.by_tag (tag, doc_id, title) 
    VALUES ('database', ?, ?);
APPLY BATCH;
```

**Or Use SAI** (5.0+):
```sql
CREATE TABLE documents.content (
    doc_id uuid PRIMARY KEY,
    title text,
    body text,
    tags set<text>
);

CREATE CUSTOM INDEX ON documents.content (values(tags))
USING 'StorageAttachedIndex';

-- Direct query on collection elements
SELECT * FROM documents.content
WHERE tags CONTAINS 'cassandra';
```

### Pattern 4: Counting

**Distributed Counter**:
```sql
CREATE TABLE analytics.page_views (
    page_url text,
    view_date date,
    shard int,            -- 0-9 for distribution
    view_count counter,
    PRIMARY KEY ((page_url, shard), view_date)
);

-- Increment (randomly choose shard)
UPDATE analytics.page_views
SET view_count = view_count + 1
WHERE page_url = '/home'
  AND shard = <random(0, 9)>
  AND view_date = '2024-01-15';

-- Read (sum across shards in application)
SELECT page_url, view_date, shard, view_count
FROM analytics.page_views
WHERE page_url = '/home'
  AND shard IN (0,1,2,3,4,5,6,7,8,9)
  AND view_date = '2024-01-15';
```

### Pattern 5: Hierarchical Data

**Denormalized Hierarchy**:
```sql
-- User -> Orders -> Items
CREATE TABLE orders.user_orders (
    user_id uuid,
    order_time timestamp,
    order_id uuid,
    status text,
    total_amount decimal,
    items list<frozen<order_item>>,  -- Embed items
    PRIMARY KEY (user_id, order_time, order_id)
) WITH CLUSTERING ORDER BY (order_time DESC);

-- UDT for nested data
CREATE TYPE order_item (
    product_id uuid,
    quantity int,
    price decimal
);
```

**Trade-offs**:
- Fast single-partition retrieval
- Cannot query by nested fields without index
- Updates require read-modify-write
- Perfect for read-heavy, write-once patterns

### Pattern 6: One-to-Many Relationships

**Collection Storage** (for small, bounded relationships):
```sql
CREATE TABLE users.profiles (
    user_id uuid PRIMARY KEY,
    name text,
    follower_ids set<uuid>,  -- Bounded to reasonable size
    recent_activities list<frozen<activity>>
);
```

**Separate Table** (for large or unbounded relationships):
```sql
CREATE TABLE social.followers (
    user_id uuid,
    follower_id uuid,
    followed_at timestamp,
    PRIMARY KEY (user_id, follower_id)
);

-- Query followers for user
SELECT * FROM social.followers WHERE user_id = ?;
```

### Pattern 7: Lookup Tables

**Multiple Access Patterns**:
```sql
-- Lookup by ID
CREATE TABLE products.by_id (
    product_id uuid PRIMARY KEY,
    sku text,
    name text,
    price decimal,
    category text
);

-- Lookup by SKU
CREATE TABLE products.by_sku (
    sku text PRIMARY KEY,
    product_id uuid,
    name text,
    price decimal,
    category text
);

-- Lookup by category (with SAI or bucketing)
CREATE TABLE products.by_category (
    category text,
    product_id uuid,
    sku text,
    name text,
    price decimal,
    PRIMARY KEY (category, product_id)
);
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Unbounded Partitions

**Bad Example**:
```sql
-- NEVER DO THIS
CREATE TABLE global_events (
    event_type text,  -- Only a few values
    event_time timestamp,
    details text,
    PRIMARY KEY (event_type, event_time)
);
-- Results in massive partitions, poor performance
```

**Fix**: Add bucketing
```sql
CREATE TABLE global_events (
    event_type text,
    bucket int,  -- Shard events across multiple partitions
    event_time timestamp,
    details text,
    PRIMARY KEY ((event_type, bucket), event_time)
);
```

### Anti-Pattern 2: Excessive Collections

**Bad Example**:
```sql
CREATE TABLE users (
    user_id uuid PRIMARY KEY,
    friend_ids set<uuid>  -- Could grow to millions
);
```

**Limits** from [`cassandra.yaml`](conf/cassandra.yaml:2456):
```yaml
items_per_collection_warn_threshold: 1000
items_per_collection_fail_threshold: 10000
```

**Fix**: Use separate table

### Anti-Pattern 3: Premature Normalization

**Bad (RDBMS thinking)**:
```sql
-- Requires joins (not supported)
CREATE TABLE users (user_id uuid PRIMARY KEY, name text);
CREATE TABLE orders (order_id uuid PRIMARY KEY, user_id uuid, amount decimal);
```

**Good (Cassandra thinking)**:
```sql
-- Denormalize for query efficiency
CREATE TABLE orders.by_user (
    user_id uuid,
    order_time timestamp,
    order_id uuid,
    user_name text,      -- Denormalized
    amount decimal,
    PRIMARY KEY (user_id, order_time)
);

CREATE TABLE orders.by_id (
    order_id uuid PRIMARY KEY,
    user_id uuid,
    user_name text,
    amount decimal,
    order_time timestamp
);
```

### Anti-Pattern 4: SELECT * FROM large_table

**Never scan entire tables in production**:
```sql
-- Bad: Full table scan
SELECT * FROM large_table;

-- Good: Partition-specific
SELECT * FROM large_table WHERE partition_key = ?;
```

## Advanced Schema Patterns

### Pattern 1: Composite Keys for Multi-Tenant

```sql
CREATE TABLE saas.customer_data (
    tenant_id uuid,
    user_id uuid,
    data_key text,
    data_value text,
    PRIMARY KEY ((tenant_id, user_id), data_key)
);
```

**Benefits**:
- Automatic data isolation per tenant
- Even distribution if tenants balanced
- Efficient tenant-specific queries

### Pattern 2: Expiring Data with TTL

```sql
-- Automatic expiration
INSERT INTO sessions.active (
    session_id, user_id, created_at, data
) VALUES (?, ?, ?, ?)
USING TTL 3600;  -- Expire after 1 hour

-- Table-level default TTL
CREATE TABLE cache.entries (
    cache_key text PRIMARY KEY,
    cache_value blob
) WITH default_time_to_live = 86400;  -- 24 hours
```

**Best Practice**: Combine with TWCS for efficient TTL expiration

### Pattern 3: Static Columns

```sql
CREATE TABLE products.inventory (
    product_id uuid,
    warehouse_id uuid,
    product_name text STATIC,  -- Shared across all warehouse entries
    product_category text STATIC,
    quantity int,
    PRIMARY KEY (product_id, warehouse_id)
);

-- Update static column (affects all rows in partition)
UPDATE products.inventory
SET product_name = 'New Name'
WHERE product_id = ?;

-- Query static column only
SELECT DISTINCT product_id, product_name 
FROM products.inventory;
```

### Pattern 4: Counter Tables

```sql
CREATE TABLE analytics.metrics (
    metric_name text,
    dimension text,
    time_bucket timestamp,
    counter_value counter,
    PRIMARY KEY ((metric_name, dimension), time_bucket)
);

-- Atomic increment
UPDATE analytics.metrics
SET counter_value = counter_value + 1
WHERE metric_name = 'page_views'
  AND dimension = 'homepage'
  AND time_bucket = '2024-01-15 14:00:00';
```

**Cautions**:
- Eventually consistent
- Cannot mix with regular columns
- Requires special read-before-write
- Use Paxos V2 for better accuracy

## Schema Evolution

### Adding Columns

```sql
-- Safe operation (instant)
ALTER TABLE users.profiles ADD phone text;

-- With default
ALTER TABLE users.profiles ADD country text DEFAULT 'US';
```

**Note**: Existing rows return null for new column until updated

### Removing Columns

```sql
-- Marks column as dropped (doesn't reclaim space immediately)
ALTER TABLE users.profiles DROP phone;

-- Space reclaimed during compaction
-- Old SSTables still contain dropped column data
```

**Important**: Cannot re-add column with same name but different type

### Changing Types

**Very Limited Support**:
```sql
-- Allowed: Expanding compatible types
ALTER TABLE users.profiles ALTER created_at TYPE timestamp;

-- Not allowed: Incompatible types
-- ALTER TABLE users.profiles ALTER user_id TYPE text; -- FAILS
```

**Recommendation**: Use casting in queries rather than altering schema

## Data Modeling Checklist

### ✓ **Partition Design**
- [ ] Partition key ensures even distribution
- [ ] Partition size bounded (target: 10-100MB, max: ~2GB)
- [ ] Partition key matches most frequent query pattern
- [ ] Composite partition key if needed for distribution
- [ ] Bucketing strategy for time-series or unbounded growth

### ✓ **Clustering Design**
- [ ] Clustering columns support range queries
- [ ] Clustering order matches common query ORDER BY
- [ ] Clustering columns used for filtering (WHERE clauses)
- [ ] No more than 2-3 clustering columns (complexity management)

### ✓ **Column Design**
- [ ] Regular columns for frequently updated data
- [ ] Static columns for partition-wide data
- [ ] Collections bounded in size (< 1000 items)
- [ ] UDTs for complex nested structures
- [ ] Appropriate data types (timestamp vs bigint, uuid vs text)

### ✓ **Table Configuration**
- [ ] Compaction strategy matches workload
- [ ] Appropriate compression (LZ4 default)
- [ ] TTL if data should expire
- [ ] Caching strategy (row cache for hot data)
- [ ] Guardrails configured appropriately

## Real-World Examples

### Example 1: Social Media Feed

**Requirements**:
- User posts messages
- Followers see messages in timeline
- Recent messages shown first

**Schema**:
```sql
-- User's own posts
CREATE TABLE social.user_posts (
    user_id uuid,
    post_time timestamp,
    post_id uuid,
    content text,
    media_urls list<text>,
    PRIMARY KEY (user_id, post_time, post_id)
) WITH CLUSTERING ORDER BY (post_time DESC);

-- Follower timelines (denormalized)
CREATE TABLE social.user_timeline (
    user_id uuid,          -- Follower's ID
    post_time timestamp,
    post_id uuid,
    author_id uuid,        -- Original poster
    author_name text,      -- Denormalized
    content text,
    PRIMARY KEY (user_id, post_time, post_id)
) WITH CLUSTERING ORDER BY (post_time DESC)
  AND default_time_to_live = 2592000;  -- 30 days

-- Application logic:
-- When user posts -> write to user_posts
-- Asynchronously -> write to all followers' timelines
```

### Example 2: E-Commerce Orders

**Requirements**:
- Users view their orders
- Look up specific order by ID
- Search orders by status
- Generate sales reports

**Schema**:
```sql
-- Orders by user (primary access pattern)
CREATE TABLE orders.by_user (
    user_id uuid,
    order_time timestamp,
    order_id uuid,
    status text,
    total decimal,
    items list<frozen<order_item>>,
    PRIMARY KEY (user_id, order_time)
) WITH CLUSTERING ORDER BY (order_time DESC);

-- Order lookup by ID
CREATE TABLE orders.by_id (
    order_id uuid PRIMARY KEY,
    user_id uuid,
    user_name text,  -- Denormalized
    order_time timestamp,
    status text,
    total decimal,
    items list<frozen<order_item>>
);

-- Orders by status (for operations)
CREATE TABLE orders.by_status (
    status text,
    order_time timestamp,
    order_id uuid,
    user_id uuid,
    total decimal,
    PRIMARY KEY (status, order_time)
) WITH CLUSTERING ORDER BY (order_time DESC);
```

### Example 3: IoT Sensor Network

**Requirements**:
- Millions of sensors
- High write throughput
- Recent data queries
- Automatic expiration

**Schema**:
```sql
CREATE TABLE iot.sensor_readings (
    sensor_id uuid,
    reading_date date,     -- Partition per sensor per day
    reading_time timestamp,
    temperature double,
    pressure double,
    humidity double,
    battery_level int,
    PRIMARY KEY ((sensor_id, reading_date), reading_time)
) WITH CLUSTERING ORDER BY (reading_time DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_unit': 'HOURS',
                    'compaction_window_size': 1}
  AND default_time_to_live = 604800  -- 7 days
  AND compression = {'class': 'LZ4Compressor', 'chunk_length_in_kb': 16};
```

**Query Patterns**:
```sql
-- Latest readings for sensor
SELECT * FROM iot.sensor_readings
WHERE sensor_id = ?
  AND reading_date = current_date
ORDER BY reading_time DESC
LIMIT 100;

-- Specific time range
SELECT * FROM iot.sensor_readings
WHERE sensor_id = ?
  AND reading_date = '2024-01-15'
  AND reading_time > '2024-01-15 14:00:00'
  AND reading_time < '2024-01-15 15:00:00';
```

### Example 4: User Sessions

**Schema**:
```sql
CREATE TABLE sessions.active (
    session_id uuid PRIMARY KEY,
    user_id uuid,
    created_at timestamp,
    last_activity timestamp,
    user_agent text,
    ip_address inet,
    data map<text, text>
) WITH default_time_to_live = 3600  -- 1 hour
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_unit': 'MINUTES',
                    'compaction_window_size': 10};

-- Index for user lookups
CREATE CUSTOM INDEX sessions_user_idx 
ON sessions.active (user_id)
USING 'StorageAttachedIndex';
```

## Migration Strategies

### Migrating from RDBMS

**Step 1: Analyze Queries**
```
List all SQL queries
Identify access patterns
Determine read vs write ratios
Measure query frequencies
```

**Step 2: Denormalize**
```
Create one table per query pattern
Duplicate data across tables
Use collections for embedded relationships
Plan for eventual consistency
```

**Step 3: Identify Partition Keys**
```
Choose keys for even distribution
Bucket time-series data
Shard high-volume entities
Avoid hot spots
```

**Step 4: Implement Write Path**
```
Use BATCH for atomicity across tables
Consider async updates for less critical tables
Implement application-level consistency checks
```

### Refactoring Existing Schema

**Adding Bucket to Unbounded Partition**:
```sql
-- Old (unbounded)
CREATE TABLE logs (
    log_level text,
    log_time timestamp,
    message text,
    PRIMARY KEY (log_level, log_time)
);

-- New (bucketed)
CREATE TABLE logs_v2 (
    log_level text,
    bucket date,  -- Add bucketing
    log_time timestamp,
    message text,
    PRIMARY KEY ((log_level, bucket), log_time)
);

-- Migration:
-- 1. Create new table
-- 2. Dual-write to both tables
-- 3. Backfill old data (if needed)
-- 4. Switch reads to new table
-- 5. Drop old table
```

## Schema Optimization Techniques

### 1. **Compression**

**LZ4 (Default)**:
```sql
WITH compression = {'class': 'LZ4Compressor', 
                    'chunk_length_in_kb': 16};
```

**ZSTD with Dictionary** (5.1+):
```sql
WITH compression = {'class': 'ZstdCompressor',
                    'chunk_length_in_kb': 16,
                    'compression_level': 3};

-- Dictionary auto-trained or manually provided
nodetool compressiondictionary export keyspace table
```

### 2. **Bloom Filter Tuning**

**Adjust False Positive Rate**:
```sql
-- Lower = more memory, fewer false positives
ALTER TABLE keyspace.table 
WITH bloom_filter_fp_chance = 0.01;  -- Default: 0.1 for STCS, 0.1 for LCS
```

### 3. **Index Summary Interval**

**Only relevant for BIG format**:
```sql
ALTER TABLE keyspace.table
WITH min_index_interval = 128
 AND max_index_interval = 2048;
```

### 4. **Caching Configuration**

```sql
-- Enable row cache for hot partitions
ALTER TABLE users.profiles
WITH caching = {'keys': 'ALL', 'rows_per_partition': '100'};

-- Disable all caching
ALTER TABLE large.table
WITH caching = {'keys': 'NONE', 'rows_per_partition': 'NONE'};
```

## Testing Schema Design

### Using cassandra-stress

**Custom Profile**:
```yaml
# stress-profile.yaml
keyspace: test

table: users

columnspec:
  - name: user_id
    population: uniform(1..1000000)
  - name: user_name
    size: uniform(10..30)
  - name: email
    size: uniform(20..50)
  - name: created_at
    population: uniform(1..1000000000)

insert:
  partitions: fixed(1)
  batchtype: UNLOGGED

queries:
  by_user:
    cql: SELECT * FROM users WHERE user_id = ?
    fields: samerow
  
  by_email:
    cql: SELECT * FROM users WHERE email = ? ALLOW FILTERING
    fields: samerow
```

```bash
cassandra-stress user profile=stress-profile.yaml \
  ops\(insert=1,by_user=5,by_email=1\) \
  n=1000000 \
  -rate threads=100 \
  -node node1,node2,node3
```

### Validation Queries

**Check Partition Sizes**:
```bash
nodetool cfhistograms keyspace table
```

**Monitor Compaction**:
```bash
watch -n 1 nodetool compactionstats
```

**Validate Distribution**:
```bash
nodetool ring keyspace | sort -k 7 -n
# Check ownership percentages are balanced
```

## Best Practices Summary

### 1. **Design Phase**
- Document all query patterns before schema design
- Choose partition keys for distribution and query efficiency
- Use time bucketing for unbounded time-series
- Plan for denormalization (storage is cheap, joins are expensive)
- Consider access frequencies (optimize for common case)

### 2. **Implementation Phase**
- Use prepared statements exclusively
- Implement proper error handling
- Monitor partition sizes during development
- Test with realistic data volumes
- Validate distribution across cluster

### 3. **Production Phase**
- Monitor key metrics (latency, throughput, partition sizes)
- Run regular repairs (or enable auto repair)
- Keep compaction current
- Review slow query logs
- Plan for schema evolution

### 4. **Migration Phase**
- Dual-write during transition
- Backfill data carefully (throttled)
- Switch reads gradually
- Validate consistency
- Drop old tables only after verification

## Conclusion

Successful Cassandra deployments require embracing its distributed nature through query-first data modeling. The patterns described here—from time-series bucketing to denormalized lookups—represent battle-tested approaches to common problems.

Key principles:
- **Query-first always**: Schema follows queries, not entities
- **Partition wisely**: Even distribution and bounded size
- **Denormalize without fear**: Duplicate data for performance
- **Use appropriate features**: Collections, UDTs, static columns, TTLs
- **Test thoroughly**: Validate schema under load before production

Modern Cassandra (5.0+) features like SAI, UCS, and advanced guardrails make good schema design more forgiving, but fundamental principles remain: know your queries, design your partitions, and embrace the distributed model.

---

**Series Conclusion**: This six-part series has explored Apache Cassandra from architectural foundations through operational best practices. Together, these posts provide a comprehensive understanding of building, optimizing, and operating Cassandra at scale.

**References**:
- [CQL Data Modeling](https://cassandra.apache.org/doc/latest/cassandra/data-modeling/)
- [Common Data Modeling Mistakes](https://cassandra.apache.org/doc/latest/cassandra/data-modeling/data-modeling-mistakes.html)
- [Schema Design Examples](https://cassandra.apache.org/doc/latest/cassandra/data-modeling/data-modeling-schema.html)