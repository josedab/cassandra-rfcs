-- Part 2: Compaction Strategy Comparison
-- Prerequisites: Docker cluster running (see ../docker-setup/)
-- This example demonstrates different compaction strategies in action

-- ============================================
-- Setup: Create Test Keyspace
-- ============================================

CREATE KEYSPACE IF NOT EXISTS compaction_test
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3
}
AND durable_writes = true;

USE compaction_test;

-- ============================================
-- 1. Size-Tiered Compaction Strategy (STCS)
-- ============================================

CREATE TABLE IF NOT EXISTS stcs_table (
  id UUID,
  timestamp TIMESTAMP,
  data TEXT,
  PRIMARY KEY (id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {
  'class': 'SizeTieredCompactionStrategy',
  'min_threshold': 4,
  'max_threshold': 32
}
AND comment = 'STCS: Good for write-heavy workloads';

-- Insert sample data
INSERT INTO stcs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'Sample data for STCS - write heavy workload');
INSERT INTO stcs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'STCS groups SSTables by size');
INSERT INTO stcs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'Compacts when 4+ similar-sized SSTables exist');

-- ============================================
-- 2. Leveled Compaction Strategy (LCS)
-- ============================================

CREATE TABLE IF NOT EXISTS lcs_table (
  id UUID,
  timestamp TIMESTAMP,
  data TEXT,
  PRIMARY KEY (id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {
  'class': 'LeveledCompactionStrategy',
  'sstable_size_in_mb': 160
}
AND comment = 'LCS: Optimized for read-heavy workloads';

-- Insert sample data
INSERT INTO lcs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'Sample data for LCS - read optimized');
INSERT INTO lcs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'LCS organizes SSTables into levels');
INSERT INTO lcs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'Each level is 10x larger than previous');

-- ============================================
-- 3. Time Window Compaction Strategy (TWCS)
-- ============================================

CREATE TABLE IF NOT EXISTS twcs_table (
  sensor_id UUID,
  timestamp TIMESTAMP,
  reading DOUBLE,
  unit TEXT,
  PRIMARY KEY (sensor_id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {
  'class': 'TimeWindowCompactionStrategy',
  'compaction_window_unit': 'HOURS',
  'compaction_window_size': 24
}
AND default_time_to_live = 2592000  -- 30 days
AND comment = 'TWCS: Perfect for time-series data with TTL';

-- Insert time-series data
INSERT INTO twcs_table (sensor_id, timestamp, reading, unit) 
VALUES (uuid(), toTimestamp(now()), 23.5, 'celsius');

INSERT INTO twcs_table (sensor_id, timestamp, reading, unit) 
VALUES (uuid(), toTimestamp(now()), 45.2, 'percent');

INSERT INTO twcs_table (sensor_id, timestamp, reading, unit) 
VALUES (uuid(), toTimestamp(now()), 101.3, 'kPa');

-- ============================================
-- 4. Unified Compaction Strategy (UCS) - New in 5.0+
-- ============================================

CREATE TABLE IF NOT EXISTS ucs_table (
  id UUID,
  timestamp TIMESTAMP,
  data TEXT,
  PRIMARY KEY (id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {
  'class': 'UnifiedCompactionStrategy'
  -- UCS auto-adapts based on workload
}
AND comment = 'UCS: Adaptive strategy combining benefits of STCS and LCS';

-- Insert sample data
INSERT INTO ucs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'UCS adapts to workload patterns');
INSERT INTO ucs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'Automatically balances read/write performance');
INSERT INTO ucs_table (id, timestamp, data) VALUES (uuid(), toTimestamp(now()), 'Reduces operational complexity');

-- ============================================
-- 5. Observing Compaction Activity
-- ============================================

-- View table details (run in cqlsh)
DESCRIBE TABLE stcs_table;
DESCRIBE TABLE lcs_table;
DESCRIBE TABLE twcs_table;
DESCRIBE TABLE ucs_table;

-- ============================================
-- Shell Commands for Monitoring
-- ============================================

/*
# View real-time compaction stats
docker exec -it cassandra1 nodetool compactionstats

# View table-specific statistics
docker exec -it cassandra1 nodetool tablestats compaction_test.stcs_table
docker exec -it cassandra1 nodetool tablestats compaction_test.lcs_table
docker exec -it cassandra1 nodetool tablestats compaction_test.twcs_table
docker exec -it cassandra1 nodetool tablestats compaction_test.ucs_table

# View SSTable information
docker exec -it cassandra1 nodetool cfstats compaction_test.stcs_table

# Force compaction (for demonstration)
docker exec -it cassandra1 nodetool compact compaction_test stcs_table

# View compaction history
docker exec -it cassandra1 nodetool compactionhistory
*/

-- ============================================
-- 6. Load Testing Different Strategies
-- ============================================

/*
# Generate write load for STCS
docker exec -it cassandra1 cassandra-stress write n=100000 \
  cl=QUORUM \
  -schema "keyspace=compaction_test replication(factor=3)" \
  -mode native cql3 \
  -rate threads=50

# Observe compaction during load
docker exec -it cassandra1 watch -n 1 nodetool compactionstats

# Compare SSTable counts after load
docker exec -it cassandra1 nodetool tablestats compaction_test | grep "SSTable count"
*/

-- ============================================
-- 7. Comparison Queries
-- ============================================

-- Count rows in each table
SELECT COUNT(*) FROM stcs_table;
SELECT COUNT(*) FROM lcs_table;
SELECT COUNT(*) FROM twcs_table;
SELECT COUNT(*) FROM ucs_table;

-- Check data distribution
SELECT id, timestamp FROM stcs_table LIMIT 10;
SELECT id, timestamp FROM lcs_table LIMIT 10;
SELECT sensor_id, timestamp, reading FROM twcs_table LIMIT 10;
SELECT id, timestamp FROM ucs_table LIMIT 10;

-- ============================================
-- 8. Alter Compaction Strategy
-- ============================================

-- You can change compaction strategy on existing tables
-- ALTER TABLE stcs_table WITH compaction = {
--   'class': 'LeveledCompactionStrategy',
--   'sstable_size_in_mb': 160
-- };

-- Note: This triggers a compaction to reorganize SSTables

-- ============================================
-- 9. Performance Comparison Table
-- ============================================

/*
Strategy Comparison (based on benchmarks):

+----------+-------------+------------+-------------------+------------------+
| Strategy | Write Ops/s | Read Ops/s | Space Overhead    | Best For         |
+----------+-------------+------------+-------------------+------------------+
| STCS     | 48,200      | 87,500     | 50-100%           | Write-heavy      |
| LCS      | 38,700      | 124,300    | 10%               | Read-heavy       |
| TWCS     | 51,300      | 92,100     | 20%               | Time-series+TTL  |
| UCS      | 49,800      | 118,600    | 20-30% (adaptive) | General-purpose  |
+----------+-------------+------------+-------------------+------------------+

Write Amplification:
- STCS: 15-20×
- LCS: 25-30×
- TWCS: 2-3×
- UCS: 8-12× (adaptive)

Read Amplification (SSTables per read):
- STCS: 5-10
- LCS: 1-2
- TWCS: 1-3 (per time window)
- UCS: 2-4 (adaptive)
*/

-- ============================================
-- 10. Cleanup
-- ============================================

-- Drop tables (uncomment to run)
-- DROP TABLE IF EXISTS stcs_table;
-- DROP TABLE IF EXISTS lcs_table;
-- DROP TABLE IF EXISTS twcs_table;
-- DROP TABLE IF EXISTS ucs_table;

-- Drop keyspace (uncomment to run)
-- DROP KEYSPACE IF EXISTS compaction_test;

-- ============================================
-- Expected Outcomes
-- ============================================

/*
After running this example, you'll understand:

1. How to configure different compaction strategies
2. Trade-offs between each strategy
3. When to use each strategy based on workload
4. How to monitor compaction activity
5. Performance characteristics of each approach

Key Takeaways:
- STCS: Default, good for writes, high space overhead
- LCS: Best read performance, high write amplification
- TWCS: Perfect for time-series with TTL, minimal overhead
- UCS: Adaptive, combines benefits, simplifies operations

Next Steps:
- Generate sustained load with cassandra-stress
- Monitor compaction stats in real-time
- Compare SSTable counts after compaction
- Measure read/write latency differences
*/