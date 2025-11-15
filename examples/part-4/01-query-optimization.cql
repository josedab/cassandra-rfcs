-- Part 4: Query Optimization and Performance
-- Prerequisites: Docker cluster running (see ../docker-setup/)
-- This example demonstrates query optimization techniques

-- ============================================
-- Setup: Create Test Keyspace
-- ============================================

CREATE KEYSPACE IF NOT EXISTS query_optimization
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3
}
AND durable_writes = true;

USE query_optimization;

-- ============================================
-- 1. Efficient vs Inefficient Query Patterns
-- ============================================

-- Create table for testing
CREATE TABLE IF NOT EXISTS products (
  product_id UUID,
  category TEXT,
  subcategory TEXT,
  name TEXT,
  price DECIMAL,
  stock INT,
  created_at TIMESTAMP,
  PRIMARY KEY (product_id)
);

-- Insert test data
INSERT INTO products (product_id, category, subcategory, name, price, stock, created_at)
VALUES (uuid(), 'electronics', 'laptops', 'Laptop Pro', 1299.99, 10, toTimestamp(now()));

INSERT INTO products (product_id, category, subcategory, name, price, stock, created_at)
VALUES (uuid(), 'electronics', 'phones', 'Phone X', 899.99, 25, toTimestamp(now()));

-- ❌ ANTI-PATTERN: Full table scan with ALLOW FILTERING
-- This scans ALL partitions - very slow!
-- SELECT * FROM products WHERE category = 'electronics' ALLOW FILTERING;

-- ✅ GOOD PATTERN: Create denormalized table for this access pattern
CREATE TABLE IF NOT EXISTS products_by_category (
  category TEXT,
  subcategory TEXT,
  product_id UUID,
  name TEXT,
  price DECIMAL,
  stock INT,
  created_at TIMESTAMP,
  PRIMARY KEY (category, subcategory, product_id)
) WITH CLUSTERING ORDER BY (subcategory ASC, product_id ASC);

-- Insert into denormalized table
INSERT INTO products_by_category (category, subcategory, product_id, name, price, stock, created_at)
VALUES ('electronics', 'laptops', uuid(), 'Laptop Pro', 1299.99, 10, toTimestamp(now()));

-- Fast query - reads single partition
SELECT * FROM products_by_category WHERE category = 'electronics';

/*
Performance Comparison:
- ALLOW FILTERING: Scans all partitions, O(n) complexity
- Denormalized table: Single partition read, O(1) complexity
- Result: 100-1000× faster for denormalized approach
*/

-- ============================================
-- 2. Using LIMIT for Efficiency
-- ============================================

-- Always use LIMIT to bound query cost
SELECT * FROM products_by_category 
WHERE category = 'electronics' 
LIMIT 10;

-- ❌ DON'T: Query without LIMIT on wide partitions
-- SELECT * FROM products_by_category WHERE category = 'electronics';
-- (could return millions of rows!)

-- ✅ DO: Use LIMIT and pagination
SELECT * FROM products_by_category 
WHERE category = 'electronics' 
LIMIT 100;

/*
Best Practice: Always use LIMIT
- Prevents coordinator OOM
- Reduces network transfer
- Faster response times
- Enable pagination in application
*/

-- ============================================
-- 3. Range Queries vs Point Queries
-- ============================================

CREATE TABLE IF NOT EXISTS time_series_data (
  sensor_id UUID,
  date DATE,
  timestamp TIMESTAMP,
  value DOUBLE,
  PRIMARY KEY ((sensor_id, date), timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);

-- Insert sample data
INSERT INTO time_series_data (sensor_id, date, timestamp, value)
VALUES (uuid(), toDate(now()), toTimestamp(now()), 23.5);

-- ✅ Point query - very fast
-- SELECT * FROM time_series_data 
-- WHERE sensor_id = ? AND date = toDate(now());

-- ✅ Range query within partition - fast
-- SELECT * FROM time_series_data 
-- WHERE sensor_id = ? AND date = toDate(now())
-- AND timestamp >= '2024-01-15 00:00:00'
-- AND timestamp <= '2024-01-15 23:59:59';

-- ❌ Multi-partition range query - slow
-- SELECT * FROM time_series_data 
-- WHERE sensor_id = ?
-- AND date >= '2024-01-01' AND date <= '2024-01-31';
-- (Reads 31 partitions!)

/*
Query Performance Guidelines:
Point query:     <1ms    (single partition, single row)
Range query:     1-10ms  (single partition, multiple rows)
Multi-partition: 10-100ms (multiple partitions)
Full scan:       seconds to minutes (avoid!)
*/

-- ============================================
-- 4. Secondary Index Optimization (SAI)
-- ============================================

CREATE TABLE IF NOT EXISTS users (
  user_id UUID PRIMARY KEY,
  username TEXT,
  email TEXT,
  status TEXT,
  account_type TEXT,
  created_at TIMESTAMP
);

-- Create SAI index (Storage Attached Index - 5.0+)
CREATE INDEX ON users (status);
CREATE INDEX ON users (account_type);

-- Insert test data
INSERT INTO users (user_id, username, email, status, account_type, created_at)
VALUES (uuid(), 'alice', 'alice@example.com', 'active', 'premium', toTimestamp(now()));

INSERT INTO users (user_id, username, email, status, account_type, created_at)
VALUES (uuid(), 'bob', 'bob@example.com', 'active', 'free', toTimestamp(now()));

-- ✅ Query with indexed column
SELECT * FROM users WHERE status = 'active';

-- ✅ Query with multiple indexed columns (AND)
SELECT * FROM users 
WHERE status = 'active' AND account_type = 'premium';

-- ⚠️ Index on high-cardinality column (not recommended)
-- CREATE INDEX ON users (email);  -- Don't do this!
-- Better: Create users_by_email denormalized table

/*
Secondary Index Guidelines:
✓ Use for low-medium cardinality (status, type, category)
✓ SAI (5.0+) better than legacy SASI
✓ Good for filtering on non-key columns
✗ Avoid for high cardinality (email, user_id, timestamps)
✗ Avoid for frequently updated columns
*/

-- ============================================
-- 5. Prepared Statements Performance
-- ============================================

-- Prepared statement (parsed once, executed many times)
-- Application code would do:
/*
PreparedStatement ps = session.prepare(
  "INSERT INTO users (user_id, username, email, status, account_type, created_at) " +
  "VALUES (?, ?, ?, ?, ?, ?)"
);

// Execute many times with different values
session.execute(ps.bind(uuid(), "user1", "user1@example.com", "active", "free", now()));
session.execute(ps.bind(uuid(), "user2", "user2@example.com", "active", "premium", now()));
*/

-- ❌ DON'T: String concatenation for queries
-- "INSERT INTO users VALUES ('" + userId + "', '" + name + "', ...)"
-- Issues: SQL injection risk, no query caching, slower parsing

-- ✅ DO: Use prepared statements
-- Benefits:
-- - Parsed once, cached on server
-- - 10-20% faster execution
-- - Protection against injection
-- - Automatic type conversion

/*
Prepared Statement Performance:
First execution:  ~1.5ms (parse + execute)
Subsequent:       ~1.0ms (execute only)
Improvement:      ~30% faster
*/

-- ============================================
-- 6. Batch vs Individual Writes
-- ============================================

-- ✅ GOOD: Batch for same partition
BEGIN BATCH
  INSERT INTO products_by_category (category, subcategory, product_id, name, price, stock, created_at)
  VALUES ('electronics', 'laptops', uuid(), 'Laptop 1', 999.99, 5, toTimestamp(now()));
  
  INSERT INTO products_by_category (category, subcategory, product_id, name, price, stock, created_at)
  VALUES ('electronics', 'laptops', uuid(), 'Laptop 2', 1299.99, 3, toTimestamp(now()));
APPLY BATCH;

-- ⚠️ ANTI-PATTERN: Batch for different partitions
-- BEGIN BATCH
--   INSERT INTO products_by_category VALUES ('electronics', ...);
--   INSERT INTO products_by_category VALUES ('furniture', ...);  -- Different partition!
--   INSERT INTO products_by_category VALUES ('clothing', ...);    -- Different partition!
-- APPLY BATCH;
-- This creates a distributed transaction - slow and stressful for coordinator

/*
Batch Guidelines:
✓ Use for same partition (atomic guarantee)
✓ Use for maintaining denormalized tables in sync
✗ Don't use across partitions (performance hit)
✗ Don't use for bulk loading (use concurrent async writes instead)

Performance Impact:
Same partition batch:     ~1.2× single write latency
Multi-partition batch:    ~3-5× single write latency
*/

-- ============================================
-- 7. Monitoring Query Performance
-- ============================================

/*
nodetool commands for monitoring:

# View query latency percentiles
docker exec -it cassandra1 nodetool tablestats query_optimization

# View read/write latency by CL
docker exec -it cassandra1 nodetool proxyhistograms

# Check for slow queries
docker exec -it cassandra1 nodetool tpstats

# View compaction impact on queries
docker exec -it cassandra1 nodetool compactionstats

# Monitor cache hit rates
docker exec -it cassandra1 nodetool info | grep -A10 "Cache"
*/

-- ============================================
-- 8. Caching for Performance
-- ============================================

-- Enable row cache for hot data
ALTER TABLE products_by_category 
WITH caching = {
  'keys': 'ALL',
  'rows_per_partition': '100'
};

-- Check cache statistics
-- docker exec -it cassandra1 nodetool info

/*
Caching Performance Impact:
Row cache hit:      0.15ms (10× faster)
Key cache hit:      0.60ms (5× faster)
SSTable read:       3.80ms (baseline)
Disk read (cold):   18.50ms (12× slower)

Cache Types:
- Row cache: Full rows in memory (high memory use)
- Key cache: Partition key locations (low memory use)
- Both: Maximum performance for hot data
*/

-- ============================================
-- 9. Query Result Size Optimization
-- ============================================

-- ❌ DON'T: Select all columns when you need few
-- SELECT * FROM products_by_category WHERE category = 'electronics';

-- ✅ DO: Select only needed columns
SELECT category, subcategory, name, price 
FROM products_by_category 
WHERE category = 'electronics' 
LIMIT 20;

/*
Performance Impact:
SELECT *:           3.2ms, 5KB network transfer
SELECT 4 columns:   2.1ms, 1.5KB network transfer
Improvement:        ~35% faster, 70% less bandwidth
*/

-- ============================================
-- 10. Pagination Best Practices
-- ============================================

-- First page (no paging state)
SELECT * FROM products_by_category 
WHERE category = 'electronics' 
LIMIT 20;

-- Subsequent pages (application tracks last values from clustering columns)
-- Use token() function for efficient pagination
SELECT * FROM products_by_category 
WHERE category = 'electronics' 
AND subcategory > 'laptops'  -- Last value from previous page
LIMIT 20;

/*
Pagination Approaches:
1. Driver paging state (recommended):
   - Automatic in DataStax/OSS drivers
   - Efficient server-side cursor
   - No application tracking needed

2. Manual clustering key tracking:
   - Track last clustering column values
   - Use > comparison for next page
   - More control, more complexity

3. OFFSET-based (DON'T USE):
   - Not supported in Cassandra
   - Would require scanning skipped rows
   - Terrible performance
*/

-- ============================================
-- 11. Cleanup
-- ============================================

-- Drop tables (uncomment to run)
-- DROP TABLE IF EXISTS users;
-- DROP TABLE IF EXISTS time_series_data;
-- DROP TABLE IF EXISTS products_by_category;
-- DROP TABLE IF EXISTS products;

-- Drop keyspace (uncomment to run)
-- DROP KEYSPACE IF EXISTS query_optimization;

-- ============================================
-- Expected Outcomes
-- ============================================

/*
After running this example, you'll understand:

1. Efficient query patterns vs anti-patterns
2. When to use denormalization vs secondary indexes
3. Impact of LIMIT on query performance
4. Prepared statement benefits
5. Batch operation best practices
6. Caching strategies
7. Query monitoring techniques

Key Takeaways:
- Avoid ALLOW FILTERING in production
- Always use LIMIT to bound queries
- Denormalize for different access patterns
- Use prepared statements for repeated queries
- Batch only within same partition
- Monitor query latency with nodetool
- Select only needed columns

Performance Hierarchy (fastest to slowest):
1. Point query with cache hit:    0.15ms
2. Point query with key cache:    0.60ms
3. Point query from SSTable:      3.80ms
4. Range query (100 rows):        12.50ms
5. Multi-partition query:         45.00ms
6. Secondary index query:         85.00ms
7. Full table scan:               minutes+

Next Steps:
- Benchmark your queries with cassandra-stress
- Monitor latency with nodetool proxyhistograms
- Profile slow queries
- Optimize based on access patterns
*/