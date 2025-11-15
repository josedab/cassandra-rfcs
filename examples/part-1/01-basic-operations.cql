-- Part 1: Basic Cassandra Operations
-- Prerequisites: Docker cluster running (see ../docker-setup/)
-- Run with: docker exec -it cassandra1 cqlsh < examples/part-1/01-basic-operations.cql

-- ============================================
-- 1. Create a Keyspace
-- ============================================

-- Create keyspace with NetworkTopologyStrategy
CREATE KEYSPACE IF NOT EXISTS example
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3  -- 3 replicas in datacenter DC1
}
AND durable_writes = true;

-- Verify keyspace created
DESCRIBE KEYSPACES;

-- View keyspace details
DESCRIBE KEYSPACE example;

-- ============================================
-- 2. Create a Simple Table
-- ============================================

USE example;

-- Create a users table with simple partition key
CREATE TABLE IF NOT EXISTS users (
  user_id UUID PRIMARY KEY,
  username TEXT,
  email TEXT,
  created_at TIMESTAMP,
  last_login TIMESTAMP,
  account_status TEXT
);

-- View table structure
DESCRIBE TABLE users;

-- ============================================
-- 3. Basic INSERT Operations
-- ============================================

-- Insert individual users
INSERT INTO users (user_id, username, email, created_at, account_status)
VALUES (uuid(), 'alice', 'alice@example.com', toTimestamp(now()), 'active');

INSERT INTO users (user_id, username, email, created_at, account_status)
VALUES (uuid(), 'bob', 'bob@example.com', toTimestamp(now()), 'active');

INSERT INTO users (user_id, username, email, created_at, account_status)
VALUES (uuid(), 'charlie', 'charlie@example.com', toTimestamp(now()), 'inactive');

-- Insert with TTL (expires after 1 hour)
INSERT INTO users (user_id, username, email, created_at, account_status)
VALUES (uuid(), 'temp_user', 'temp@example.com', toTimestamp(now()), 'trial')
USING TTL 3600;

-- Insert with timestamp (backdated)
INSERT INTO users (user_id, username, email, created_at, account_status)
VALUES (uuid(), 'historical', 'old@example.com', '2023-01-01 00:00:00', 'active')
USING TIMESTAMP 1672531200000000;

-- ============================================
-- 4. Basic SELECT Operations
-- ============================================

-- Select all users (avoid in production!)
SELECT * FROM users;

-- Select specific columns
SELECT username, email, account_status FROM users;

-- Select with LIMIT
SELECT * FROM users LIMIT 3;

-- Note: WHERE clause requires partition key or ALLOW FILTERING
-- This will fail:
-- SELECT * FROM users WHERE username = 'alice';

-- This requires ALLOW FILTERING (slow, avoid in production)
SELECT * FROM users WHERE username = 'alice' ALLOW FILTERING;

-- ============================================
-- 5. UPDATE Operations
-- ============================================

-- Note: Need to capture a user_id first
-- In practice, you'd get this from a previous query
-- For demo purposes, we'll use a fixed UUID

-- Update requires partition key
-- UPDATE users SET last_login = toTimestamp(now()) WHERE user_id = ?;

-- Update with conditional
-- UPDATE users SET account_status = 'suspended' WHERE user_id = ? IF account_status = 'active';

-- ============================================
-- 6. DELETE Operations
-- ============================================

-- Delete a specific user (requires partition key)
-- DELETE FROM users WHERE user_id = ?;

-- Delete specific columns
-- DELETE email FROM users WHERE user_id = ?;

-- Delete all users with a specific username (requires ALLOW FILTERING)
-- DELETE FROM users WHERE username = 'temp_user' IF EXISTS;

-- ============================================
-- 7. Create Table with Clustering Columns
-- ============================================

-- Create a table to track user activity
CREATE TABLE IF NOT EXISTS user_activity (
  user_id UUID,
  activity_time TIMESTAMP,
  activity_type TEXT,
  details TEXT,
  PRIMARY KEY (user_id, activity_time)
) WITH CLUSTERING ORDER BY (activity_time DESC);

-- Insert some activity records
INSERT INTO user_activity (user_id, activity_time, activity_type, details)
VALUES (uuid(), toTimestamp(now()), 'login', 'Web browser login');

INSERT INTO user_activity (user_id, activity_time, activity_type, details)
VALUES (uuid(), toTimestamp(now()), 'purchase', 'Bought product X');

-- ============================================
-- 8. Batch Operations
-- ============================================

-- Logged batch (atomic within a partition)
BEGIN BATCH
  INSERT INTO users (user_id, username, email, created_at, account_status)
  VALUES (uuid(), 'batch_user1', 'batch1@example.com', toTimestamp(now()), 'active');
  
  INSERT INTO users (user_id, username, email, created_at, account_status)
  VALUES (uuid(), 'batch_user2', 'batch2@example.com', toTimestamp(now()), 'active');
APPLY BATCH;

-- Unlogged batch (better performance, no atomicity guarantee)
BEGIN UNLOGGED BATCH
  INSERT INTO users (user_id, username, email, created_at, account_status)
  VALUES (uuid(), 'unlogged1', 'unlogged1@example.com', toTimestamp(now()), 'active');
  
  INSERT INTO users (user_id, username, email, created_at, account_status)
  VALUES (uuid(), 'unlogged2', 'unlogged2@example.com', toTimestamp(now()), 'active');
APPLY BATCH;

-- ============================================
-- 9. Collections
-- ============================================

-- Create table with collection types
CREATE TABLE IF NOT EXISTS user_profiles (
  user_id UUID PRIMARY KEY,
  username TEXT,
  tags SET<TEXT>,
  preferences MAP<TEXT, TEXT>,
  login_history LIST<TIMESTAMP>
);

-- Insert with collections
INSERT INTO user_profiles (user_id, username, tags, preferences)
VALUES (
  uuid(),
  'collection_user',
  {'power-user', 'early-adopter'},
  {'theme': 'dark', 'language': 'en'}
);

-- Update collections - add to set
UPDATE user_profiles 
SET tags = tags + {'verified'} 
WHERE user_id = uuid(); -- Use actual UUID

-- Update collections - add to map
UPDATE user_profiles 
SET preferences['notifications'] = 'email' 
WHERE user_id = uuid(); -- Use actual UUID

-- ============================================
-- 10. Counter Tables
-- ============================================

-- Create counter table
CREATE TABLE IF NOT EXISTS page_views (
  page_id TEXT PRIMARY KEY,
  view_count COUNTER
);

-- Increment counter
UPDATE page_views SET view_count = view_count + 1 WHERE page_id = 'home';
UPDATE page_views SET view_count = view_count + 1 WHERE page_id = 'home';
UPDATE page_views SET view_count = view_count + 5 WHERE page_id = 'home';

-- Read counter
SELECT * FROM page_views WHERE page_id = 'home';

-- ============================================
-- 11. Inspect Table Statistics
-- ============================================

-- Note: These commands work in nodetool, not CQL
-- Run from shell:
-- docker exec -it cassandra1 nodetool tablestats example
-- docker exec -it cassandra1 nodetool cfstats example.users

-- ============================================
-- 12. Cleanup
-- ============================================

-- Drop tables (uncomment to run)
-- DROP TABLE IF EXISTS page_views;
-- DROP TABLE IF EXISTS user_profiles;
-- DROP TABLE IF EXISTS user_activity;
-- DROP TABLE IF EXISTS users;

-- Drop keyspace (uncomment to run)
-- DROP KEYSPACE IF EXISTS example;

-- ============================================
-- Expected Outcomes
-- ============================================

/*
After running this script, you should have:
1. A keyspace 'example' with RF=3
2. Several tables demonstrating different patterns
3. Sample data in each table
4. Understanding of basic CRUD operations

To verify:
1. Check cluster status:
   docker exec -it cassandra1 nodetool status

2. View keyspace info:
   docker exec -it cassandra1 cqlsh -e "DESCRIBE KEYSPACE example;"

3. Count rows:
   docker exec -it cassandra1 cqlsh -e "SELECT COUNT(*) FROM example.users;"

4. View table statistics:
   docker exec -it cassandra1 nodetool tablestats example.users
*/