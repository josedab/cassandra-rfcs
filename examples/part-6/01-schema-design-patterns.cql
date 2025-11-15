-- Part 6: Schema Design Patterns and Best Practices
-- Prerequisites: Docker cluster running (see ../docker-setup/)
-- This example demonstrates common schema design patterns

-- ============================================
-- Setup: Create Test Keyspace
-- ============================================

CREATE KEYSPACE IF NOT EXISTS schema_patterns
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3
}
AND durable_writes = true;

USE schema_patterns;

-- ============================================
-- Pattern 1: Time Series Data
-- ============================================

-- Anti-pattern: Single partition for all sensor data
-- DON'T DO THIS - Creates hot partition
/*
CREATE TABLE sensor_readings_bad (
  sensor_id UUID,
  timestamp TIMESTAMP,
  reading DOUBLE,
  PRIMARY KEY (sensor_id, timestamp)  -- All data for one sensor in one partition!
);
*/

-- Good pattern: Bucket by time window
CREATE TABLE sensor_readings (
  sensor_id UUID,
  bucket DATE,          -- Partition by day
  timestamp TIMESTAMP,  -- Clustering key for ordering
  reading DOUBLE,
  unit TEXT,
  PRIMARY KEY ((sensor_id, bucket), timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {
  'class': 'TimeWindowCompactionStrategy',
  'compaction_window_unit': 'DAYS',
  'compaction_window_size': 1
}
AND default_time_to_live = 2592000;  -- 30 days

-- Insert time-series data
INSERT INTO sensor_readings (sensor_id, bucket, timestamp, reading, unit)
VALUES (uuid(), toDate(now()), toTimestamp(now()), 23.5, 'celsius');

-- Query recent data from a sensor
-- SELECT * FROM sensor_readings 
-- WHERE sensor_id = ? AND bucket = '2024-01-15'
-- AND timestamp >= '2024-01-15 00:00:00'
-- AND timestamp <= '2024-01-15 23:59:59';

/*
Time Series Best Practices:
✓ Bucket by time (hour/day/month based on query patterns)
✓ Use TWCS compaction
✓ Set TTL for automatic cleanup
✓ Keep partition size <100MB
✓ Use DESC clustering for recent-first queries
*/

-- ============================================
-- Pattern 2: User Activity Tracking
-- ============================================

-- Track user events with efficient queries
CREATE TABLE user_activity (
  user_id UUID,
  activity_date DATE,
  activity_time TIMESTAMP,
  activity_type TEXT,
  details MAP<TEXT, TEXT>,
  PRIMARY KEY ((user_id, activity_date), activity_time)
) WITH CLUSTERING ORDER BY (activity_time DESC);

-- Insert activity
INSERT INTO user_activity (user_id, activity_date, activity_time, activity_type, details)
VALUES (
  uuid(),
  toDate(now()),
  toTimestamp(now()),
  'login',
  {'ip': '192.168.1.1', 'device': 'mobile'}
);

-- Query today's activities for a user
-- SELECT * FROM user_activity
-- WHERE user_id = ? AND activity_date = toDate(now());

/*
Activity Tracking Best Practices:
✓ Composite partition key (user_id + date)
✓ Prevents unbounded partition growth
✓ Enables efficient time-range queries
✓ Use MAP for flexible metadata
*/

-- ============================================
-- Pattern 3: Denormalization for Multiple Access Patterns
-- ============================================

-- Table 1: Users by ID (primary access)
CREATE TABLE users_by_id (
  user_id UUID PRIMARY KEY,
  email TEXT,
  username TEXT,
  full_name TEXT,
  created_at TIMESTAMP,
  status TEXT
);

-- Table 2: Users by Email (for login)
CREATE TABLE users_by_email (
  email TEXT PRIMARY KEY,
  user_id UUID,
  username TEXT,
  full_name TEXT,
  created_at TIMESTAMP,
  status TEXT
);

-- Table 3: Users by Username (for profile lookup)
CREATE TABLE users_by_username (
  username TEXT PRIMARY KEY,
  user_id UUID,
  email TEXT,
  full_name TEXT,
  created_at TIMESTAMP,
  status TEXT
);

-- Insert must update all tables (use application-level batch or MV)
BEGIN BATCH
  INSERT INTO users_by_id (user_id, email, username, full_name, created_at, status)
  VALUES (uuid(), 'user@example.com', 'johndoe', 'John Doe', toTimestamp(now()), 'active');
  
  INSERT INTO users_by_email (email, user_id, username, full_name, created_at, status)
  VALUES ('user@example.com', uuid(), 'johndoe', 'John Doe', toTimestamp(now()), 'active');
  
  INSERT INTO users_by_username (username, user_id, email, full_name, created_at, status)
  VALUES ('johndoe', uuid(), 'user@example.com', 'John Doe', toTimestamp(now()), 'active');
APPLY BATCH;

/*
Denormalization Best Practices:
✓ Create separate table for each access pattern
✓ Duplicate data across tables
✓ Keep tables in sync (batch writes or MV)
✓ Disk is cheap, queries are expensive
⚠️ Consistency managed at application level
*/

-- ============================================
-- Pattern 4: Wide Partitions (Carefully!)
-- ============================================

-- Product reviews - many reviews per product
CREATE TABLE product_reviews (
  product_id UUID,
  review_time TIMESTAMP,
  review_id UUID,
  user_id UUID,
  rating INT,
  review_text TEXT,
  helpful_count COUNTER,
  PRIMARY KEY (product_id, review_time, review_id)
) WITH CLUSTERING ORDER BY (review_time DESC);

-- Partition size guidelines:
-- Max 100,000 rows per partition
-- Max 100MB per partition
-- Use LIMIT in queries

-- Query with LIMIT
-- SELECT * FROM product_reviews
-- WHERE product_id = ?
-- LIMIT 10;  -- Always use LIMIT!

/*
Wide Partition Best Practices:
✓ Keep partition size <100MB
✓ Limit clustering columns <100K per partition
✓ Always use LIMIT in queries
✓ Monitor partition sizes
⚠️ Consider bucketing if partitions grow too large
*/

-- ============================================
-- Pattern 5: Materialized Views
-- ============================================

-- Base table
CREATE TABLE blog_posts (
  post_id UUID,
  author_id UUID,
  category TEXT,
  title TEXT,
  content TEXT,
  published_at TIMESTAMP,
  view_count BIGINT,
  PRIMARY KEY (post_id)
);

-- Materialized view for posts by category
CREATE MATERIALIZED VIEW posts_by_category AS
  SELECT post_id, category, published_at, title, author_id
  FROM blog_posts
  WHERE category IS NOT NULL
    AND published_at IS NOT NULL
    AND post_id IS NOT NULL
  PRIMARY KEY (category, published_at, post_id)
  WITH CLUSTERING ORDER BY (published_at DESC);

-- Materialized view for posts by author
CREATE MATERIALIZED VIEW posts_by_author AS
  SELECT post_id, author_id, published_at, title, category
  FROM blog_posts
  WHERE author_id IS NOT NULL
    AND published_at IS NOT NULL
    AND post_id IS NOT NULL
  PRIMARY KEY (author_id, published_at, post_id)
  WITH CLUSTERING ORDER BY (published_at DESC);

-- Insert into base table (MVs update automatically)
INSERT INTO blog_posts (post_id, author_id, category, title, content, published_at, view_count)
VALUES (
  uuid(),
  uuid(),
  'technology',
  'Cassandra Schema Design',
  'Content here...',
  toTimestamp(now()),
  0
);

-- Query materialized views
-- SELECT * FROM posts_by_category WHERE category = 'technology' LIMIT 10;
-- SELECT * FROM posts_by_author WHERE author_id = ? LIMIT 10;

/*
Materialized View Best Practices:
✓ Automatic consistency maintenance
✓ Different clustering order than base table
✓ All PK columns must be in SELECT
⚠️ Write amplification (updates all MVs)
⚠️ Can impact write performance
*/

-- ============================================
-- Pattern 6: Secondary Indexes (SAI in 5.0+)
-- ============================================

-- Table with secondary index
CREATE TABLE products (
  product_id UUID PRIMARY KEY,
  name TEXT,
  category TEXT,
  price DECIMAL,
  stock_quantity INT,
  status TEXT
);

-- Create SAI index (Storage Attached Index - 5.0+)
CREATE INDEX ON products (category);
CREATE INDEX ON products (status);

-- Insert products
INSERT INTO products (product_id, name, category, price, stock_quantity, status)
VALUES (uuid(), 'Laptop', 'electronics', 999.99, 10, 'active');

INSERT INTO products (product_id, name, category, price, stock_quantity, status)
VALUES (uuid(), 'Mouse', 'electronics', 29.99, 50, 'active');

-- Query using secondary index
SELECT * FROM products WHERE category = 'electronics' AND status = 'active';

/*
Secondary Index Best Practices:
✓ Use for low-to-medium cardinality columns
✓ SAI (5.0+) better than legacy SASI
✓ Good for filtering on non-key columns
⚠️ Avoid on high-cardinality columns (user_id, email)
⚠️ Less efficient than primary key queries
⚠️ Consider denormalization instead for heavy use
*/

-- ============================================
-- Pattern 7: Counter Tables
-- ============================================

-- Page view counters
CREATE TABLE page_stats (
  page_id TEXT,
  stat_type TEXT,  -- 'views', 'likes', 'shares'
  count COUNTER,
  PRIMARY KEY (page_id, stat_type)
);

-- Increment counters
UPDATE page_stats SET count = count + 1 
WHERE page_id = 'homepage' AND stat_type = 'views';

UPDATE page_stats SET count = count + 1
WHERE page_id = 'homepage' AND stat_type = 'likes';

-- Read counters
SELECT * FROM page_stats WHERE page_id = 'homepage';

/*
Counter Table Best Practices:
✓ Dedicated tables for counters only
✓ Cannot mix counters with regular columns
✓ Eventually consistent (may see temporary decreases)
✓ Use for counts, not critical values
⚠️ Cannot set to specific value (only increment/decrement)
⚠️ Requires read-before-write (performance impact)
*/

-- ============================================
-- Pattern 8: Shopping Cart (Session Data)
-- ============================================

-- Shopping cart with TTL
CREATE TABLE shopping_carts (
  user_id UUID,
  session_id UUID,
  product_id UUID,
  added_at TIMESTAMP,
  quantity INT,
  price DECIMAL,
  PRIMARY KEY ((user_id, session_id), product_id)
);

-- Insert with TTL (cart expires after 24 hours)
INSERT INTO shopping_carts (user_id, session_id, product_id, added_at, quantity, price)
VALUES (uuid(), uuid(), uuid(), toTimestamp(now()), 2, 49.99)
USING TTL 86400;  -- 24 hours

/*
Session Data Best Practices:
✓ Use TTL for automatic cleanup
✓ Composite partition key (user + session)
✓ Low consistency level acceptable (LOCAL_ONE)
✓ Prevents storage bloat
*/

-- ============================================
-- Pattern 9: Messaging/Inbox
-- ============================================

-- User inbox with efficient queries
CREATE TABLE user_messages (
  user_id UUID,
  message_date DATE,
  message_time TIMESTAMP,
  message_id UUID,
  sender_id UUID,
  subject TEXT,
  body TEXT,
  is_read BOOLEAN,
  PRIMARY KEY ((user_id, message_date), message_time, message_id)
) WITH CLUSTERING ORDER BY (message_time DESC);

-- Insert message
INSERT INTO user_messages (user_id, message_date, message_time, message_id, sender_id, subject, body, is_read)
VALUES (
  uuid(),
  toDate(now()),
  toTimestamp(now()),
  uuid(),
  uuid(),
  'Hello!',
  'Message content here',
  false
);

-- Query today's messages
-- SELECT * FROM user_messages
-- WHERE user_id = ? AND message_date = toDate(now())
-- LIMIT 50;

/*
Inbox Pattern Best Practices:
✓ Bucket by date to prevent unbounded growth
✓ DESC ordering for recent messages first
✓ Use LIMIT for pagination
✓ Consider separate table for unread count
*/

-- ============================================
-- Pattern 10: Multi-Tenancy
-- ============================================

-- Tenant-scoped data
CREATE TABLE tenant_data (
  tenant_id UUID,
  record_type TEXT,
  record_id UUID,
  data MAP<TEXT, TEXT>,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  PRIMARY KEY ((tenant_id, record_type), record_id)
);

-- Insert tenant data
INSERT INTO tenant_data (tenant_id, record_type, record_id, data, created_at, updated_at)
VALUES (
  uuid(),
  'customer',
  uuid(),
  {'name': 'John Doe', 'email': 'john@example.com'},
  toTimestamp(now()),
  toTimestamp(now())
);

/*
Multi-Tenancy Best Practices:
✓ Include tenant_id in partition key
✓ Ensures tenant isolation
✓ Prevents cross-tenant queries
✓ Easy to shard by tenant
✓ Consider separate keyspace per tenant for large tenants
*/

-- ============================================
-- Anti-Patterns to Avoid
-- ============================================

/*
❌ DON'T: Create unbounded partitions
Bad: PRIMARY KEY (user_id, activity_time)
Good: PRIMARY KEY ((user_id, date), activity_time)

❌ DON'T: Use high-cardinality secondary indexes
Bad: CREATE INDEX ON users (email)  -- millions of unique values
Good: Denormalize into separate table

❌ DON'T: Use ALLOW FILTERING in production
Bad: SELECT * FROM users WHERE status = 'active' ALLOW FILTERING;
Good: Create index or denormalized table

❌ DON'T: Store large blobs >10MB
Bad: Storing video files in Cassandra
Good: Store in object storage, reference in Cassandra

❌ DON'T: Use collections for unbounded data
Bad: SET<TEXT> with thousands of elements
Good: Create separate table with proper partitioning

❌ DON'T: Mix counter and regular columns
Bad: CREATE TABLE (id UUID PRIMARY KEY, count COUNTER, name TEXT)
Good: Separate table for counters

❌ DON'T: Query without partition key
Bad: SELECT * FROM users WHERE email = ?
Good: users_by_email table with email as partition key

❌ DON'T: Use NULL in partition key
Bad: INSERT INTO table (pk, ck, value) VALUES (null, ...)
Good: Always provide non-null partition keys
*/

-- ============================================
-- Design Decision Flowchart
-- ============================================

/*
1. Identify query patterns first (not entities!)
2. For each query, design a table
3. Choose partition key:
   - Distributes data evenly
   - Enables efficient queries
   - Prevents hot partitions
   - Keeps partition size <100MB
4. Choose clustering columns:
   - For sorting within partition
   - For range queries
   - For uniqueness (with PK)
5. Choose compaction strategy:
   - TWCS for time-series with TTL
   - LCS for read-heavy
   - STCS for write-heavy
   - UCS for mixed/unknown
6. Set TTL if data expires
7. Add secondary indexes sparingly
8. Denormalize as needed
9. Test with realistic data volume
10. Monitor partition sizes

See: blog-series/diagrams/data-modeling-decision-tree.mmd
*/

-- ============================================
-- Cleanup
-- ============================================

-- Drop tables and views (uncomment to run)
-- DROP MATERIALIZED VIEW IF EXISTS posts_by_author;
-- DROP MATERIALIZED VIEW IF EXISTS posts_by_category;
-- DROP TABLE IF EXISTS blog_posts;
-- DROP TABLE IF EXISTS product_reviews;
-- DROP TABLE IF EXISTS user_messages;
-- DROP TABLE IF EXISTS shopping_carts;
-- DROP TABLE IF EXISTS page_stats;
-- DROP TABLE IF EXISTS products;
-- DROP TABLE IF EXISTS users_by_username;
-- DROP TABLE IF EXISTS users_by_email;
-- DROP TABLE IF EXISTS users_by_id;
-- DROP TABLE IF EXISTS user_activity;
-- DROP TABLE IF EXISTS sensor_readings;
-- DROP TABLE IF EXISTS tenant_data;

-- Drop keyspace (uncomment to run)
-- DROP KEYSPACE IF EXISTS schema_patterns;

-- ============================================
-- Expected Outcomes
-- ============================================

/*
After running this example, you'll understand:

1. Common schema design patterns for various use cases
2. How to design tables based on query patterns
3. When to denormalize vs use secondary indexes
4. How to prevent common anti-patterns
5. Best practices for partition sizing

Key Takeaways:
- Query patterns drive schema design
- Denormalization is normal and expected
- Keep partitions <100MB
- Use appropriate compaction strategy
- Avoid ALLOW FILTERING
- Test with production-like data volumes

Next Steps:
- Design schemas for your use cases
- Test partition sizes with realistic data
- Benchmark query performance
- Monitor in production
*/