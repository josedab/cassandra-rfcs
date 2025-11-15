-- Part 3: Consistency Levels and Replication Testing
-- Prerequisites: Docker cluster running with 3 nodes (see ../docker-setup/)
-- This example demonstrates different consistency levels in action

-- ============================================
-- Setup: Create Test Keyspace
-- ============================================

CREATE KEYSPACE IF NOT EXISTS consistency_test
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3  -- Replication factor of 3
}
AND durable_writes = true;

USE consistency_test;

-- ============================================
-- 1. Create Test Table
-- ============================================

CREATE TABLE IF NOT EXISTS user_data (
  user_id UUID PRIMARY KEY,
  username TEXT,
  email TEXT,
  data TEXT,
  written_at TIMESTAMP,
  written_by TEXT
);

-- ============================================
-- 2. Testing Consistency Level: ONE
-- ============================================

-- Set consistency to ONE (fastest, eventual consistency)
CONSISTENCY ONE;

-- Insert with CL=ONE (writes to 1 replica)
INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
VALUES (uuid(), 'cl_one_user', 'one@example.com', 'Written with CL=ONE', toTimestamp(now()), 'coordinator_node_1');

-- Read with CL=ONE (reads from 1 replica)
SELECT * FROM user_data WHERE user_id = uuid();

/*
CL=ONE Characteristics:
✓ Lowest latency (~0.9ms write, ~0.6ms read)
✓ Highest availability (tolerates 2 node failures for reads)
⚠️ Eventual consistency (may read stale data)
⚠️ Lower durability (data loss if node fails before replication)
*/

-- ============================================
-- 3. Testing Consistency Level: QUORUM
-- ============================================

-- Set consistency to QUORUM (most common production setting)
CONSISTENCY QUORUM;

-- Insert with CL=QUORUM (writes to 2 of 3 replicas)
INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
VALUES (uuid(), 'cl_quorum_user', 'quorum@example.com', 'Written with CL=QUORUM', toTimestamp(now()), 'coordinator_node_1');

-- Read with CL=QUORUM (reads from 2 replicas)
SELECT * FROM user_data LIMIT 5;

/*
CL=QUORUM Characteristics:
✓ Strong consistency (R+W > RF: 2+2 > 3)
✓ Good availability (tolerates 1 node failure)
✓ Balanced latency (~1.8ms write, ~1.0ms read)
✓ Production standard (80% of deployments)

Formula: QUORUM = ⌈RF/2⌉ = ⌈3/2⌉ = 2 nodes
*/

-- ============================================
-- 4. Testing Consistency Level: ALL
-- ============================================

-- Set consistency to ALL (highest consistency)
CONSISTENCY ALL;

-- Insert with CL=ALL (writes to all 3 replicas)
INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
VALUES (uuid(), 'cl_all_user', 'all@example.com', 'Written with CL=ALL', toTimestamp(now()), 'coordinator_node_1');

-- Read with CL=ALL (reads from all replicas)
SELECT * FROM user_data LIMIT 3;

/*
CL=ALL Characteristics:
✓ Guaranteed consistency
✓ Highest durability
❌ Lowest availability (any node down = failure)
❌ Highest latency (~2.6ms write, ~1.3ms read)
⚠️ Use sparingly (critical financial data only)
*/

-- ============================================
-- 5. Testing Consistency Level: TWO
-- ============================================

-- Set consistency to TWO
CONSISTENCY TWO;

-- Insert with CL=TWO (writes to exactly 2 replicas)
INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
VALUES (uuid(), 'cl_two_user', 'two@example.com', 'Written with CL=TWO', toTimestamp(now()), 'coordinator_node_1');

/*
CL=TWO Characteristics:
✓ Medium consistency
✓ Medium availability
⚠️ Less common than QUORUM
⚠️ Doesn't guarantee R+W > RF
*/

-- ============================================
-- 6. Testing Consistency Level: ANY (Writes Only)
-- ============================================

-- Set consistency to ANY (lowest durability)
CONSISTENCY ANY;

-- Insert with CL=ANY (can write to hint)
INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
VALUES (uuid(), 'cl_any_user', 'any@example.com', 'Written with CL=ANY', toTimestamp(now()), 'coordinator_node_1');

/*
CL=ANY Characteristics (WRITES ONLY):
✓ Fastest writes (~0.8ms)
✓ Highest write availability
❌ Lowest durability (may only write hint)
⚠️ Use only for non-critical data (logging, metrics)
❌ Not available for reads
*/

-- ============================================
-- 7. Demonstrating R + W > RF = Strong Consistency
-- ============================================

-- Strong consistency combinations for RF=3:
/*
Write CL  | Read CL   | W + R | Strong Consistency
----------|-----------|-------|-------------------
QUORUM    | QUORUM    | 2 + 2 | ✓ (4 > 3)
ONE       | ALL       | 1 + 3 | ✓ (4 > 3)
ALL       | ONE       | 3 + 1 | ✓ (4 > 3)
TWO       | TWO       | 2 + 2 | ✓ (4 > 3)
ONE       | ONE       | 1 + 1 | ✗ (2 < 3) Eventual
TWO       | ONE       | 2 + 1 | ✗ (3 = 3) Not guaranteed
*/

-- ============================================
-- 8. Performance Comparison Experiment
-- ============================================

-- Reset to QUORUM for testing
CONSISTENCY QUORUM;

-- Create a batch insert for performance testing
BEGIN BATCH
  INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
  VALUES (uuid(), 'batch1', 'batch1@example.com', 'Batch test', toTimestamp(now()), 'batch');
  
  INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
  VALUES (uuid(), 'batch2', 'batch2@example.com', 'Batch test', toTimestamp(now()), 'batch');
  
  INSERT INTO user_data (user_id, username, email, data, written_at, written_by)
  VALUES (uuid(), 'batch3', 'batch3@example.com', 'Batch test', toTimestamp(now()), 'batch');
APPLY BATCH;

-- ============================================
-- 9. Simulating Node Failure
-- ============================================

/*
To test consistency behavior during node failure:

# Stop one node
docker stop cassandra3

# With QUORUM (2 of 3 nodes):
# Writes: ✓ Still work (can reach 2 nodes)
# Reads: ✓ Still work (can reach 2 nodes)

CONSISTENCY QUORUM;
INSERT INTO user_data (user_id, username, email, data, written_at)
VALUES (uuid(), 'after_failure', 'fail@example.com', 'Node 3 down', toTimestamp(now()));

# With ALL (3 of 3 nodes):
# Writes: ✗ FAIL (cannot reach all nodes)
# Reads: ✗ FAIL (cannot reach all nodes)

CONSISTENCY ALL;
-- This will timeout:
-- INSERT INTO user_data (user_id, username, email, data)
-- VALUES (uuid(), 'will_fail', 'fail@example.com', 'This fails');

# Stop second node
docker stop cassandra2

# With QUORUM (need 2 of 3):
# Writes: ✗ FAIL (only 1 node available)
# Reads: ✗ FAIL (only 1 node available)

# With ONE (need 1 of 3):
# Writes: ✓ Still work
# Reads: ✓ Still work

CONSISTENCY ONE;
INSERT INTO user_data (user_id, username, email, data)
VALUES (uuid(), 'minority', 'minority@example.com', 'Only 1 node up');

# Restart nodes
docker start cassandra2 cassandra3
*/

-- ============================================
-- 10. Multi-DC Consistency Levels (Conceptual)
-- ============================================

-- For multi-datacenter deployments:
/*
LOCAL_ONE: 1 replica in local DC
LOCAL_QUORUM: Quorum in local DC (most common for multi-DC)
EACH_QUORUM: Quorum in each DC (highest consistency)

Example with 2 DCs (RF=3 per DC):

CONSISTENCY LOCAL_QUORUM;
-- Writes to 2 of 3 nodes in local DC
-- Async replication to remote DC
-- Lowest cross-DC latency

CONSISTENCY EACH_QUORUM;
-- Writes to 2 of 3 nodes in EACH DC
-- Synchronous cross-DC replication
-- Highest cross-DC latency (+60-150ms)
*/

-- ============================================
-- 11. Consistency Level Recommendations
-- ============================================

/*
Use Case                      | Write CL       | Read CL        | Rationale
------------------------------|----------------|----------------|--------------------
User sessions                 | LOCAL_ONE      | LOCAL_ONE      | Speed > consistency
Social media posts            | LOCAL_QUORUM   | LOCAL_ONE      | Eventual is OK
E-commerce inventory          | QUORUM         | QUORUM         | Strong consistency
Financial transactions        | ALL            | QUORUM         | Maximum durability
Analytics/Logging             | ONE/ANY        | ONE            | Speed, loss OK
Multi-DC critical data        | EACH_QUORUM    | LOCAL_QUORUM   | Global consistency
Time-series (recent)          | LOCAL_QUORUM   | LOCAL_ONE      | Recent data critical
Time-series (historical)      | ONE            | ONE            | Speed over accuracy
*/

-- ============================================
-- 12. Monitoring Consistency
-- ============================================

/*
# View read/write latency by CL
docker exec -it cassandra1 nodetool proxyhistograms

# Check for hints (indicates nodes were down)
docker exec -it cassandra1 nodetool statusgossip

# View pending operations
docker exec -it cassandra1 nodetool tpstats | grep -A5 "Mutation"

# Monitor read repair activity
docker exec -it cassandra1 nodetool netstats
*/

-- ============================================
-- 13. View Data Distribution
-- ============================================

-- Count total rows
SELECT COUNT(*) FROM user_data;

-- View sample data with all consistency levels represented
SELECT username, data, written_by FROM user_data LIMIT 10;

-- ============================================
-- 14. Performance Benchmarks
-- ============================================

/*
From blog-series/PERFORMANCE-BENCHMARKS.md:

Consistency Level | Write Ops/s | Read Ops/s | Latency p95
------------------|-------------|------------|-------------
ANY               | 78,500      | N/A        | 2.1ms
ONE               | 72,300      | 142,500    | 2.4ms
TWO               | 58,400      | 98,300     | 4.2ms
QUORUM            | 52,800      | 87,600     | 5.2ms
ALL               | 41,200      | 73,400     | 8.7ms

Trade-off: Consistency ↑ = Latency ↑, Throughput ↓
*/

-- ============================================
-- 15. Cleanup
-- ============================================

-- Drop table (uncomment to run)
-- DROP TABLE IF EXISTS user_data;

-- Drop keyspace (uncomment to run)
-- DROP KEYSPACE IF EXISTS consistency_test;

-- ============================================
-- Expected Outcomes
-- ============================================

/*
After running this example, you'll understand:

1. How to set and use different consistency levels
2. Trade-offs between consistency, availability, and latency
3. The R + W > RF formula for strong consistency
4. How consistency levels behave during node failures
5. When to use each consistency level

Key Takeaways:
- QUORUM is the production standard (80% of use cases)
- R + W > RF guarantees strong consistency
- Higher CL = higher latency but stronger guarantees
- Choose CL based on application requirements
- Multi-DC deployments should use LOCAL_QUORUM

Next Steps:
- Experiment with node failures using docker stop
- Run cassandra-stress with different CLs
- Monitor latency differences in nodetool proxyhistograms
- Test read repair behavior
*/