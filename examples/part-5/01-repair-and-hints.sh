#!/bin/bash
# Part 5: Repair and Hints Operations
# Prerequisites: Docker cluster running (see ../docker-setup/)
# This script demonstrates repair and hints mechanisms

set -e

echo "=========================================="
echo "Part 5: Repair and Hints Demonstration"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# ============================================
# Setup: Verify Cluster is Running
# ============================================

echo -e "${YELLOW}Step 1: Verifying cluster status${NC}"
docker exec -it cassandra1 nodetool status

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Create Test Data
# ============================================

echo -e "${YELLOW}Step 2: Creating test keyspace and data${NC}"

docker exec -it cassandra1 cqlsh << 'EOF'
CREATE KEYSPACE IF NOT EXISTS repair_test
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3
}
AND durable_writes = true;

USE repair_test;

CREATE TABLE IF NOT EXISTS test_data (
  id UUID PRIMARY KEY,
  data TEXT,
  created_at TIMESTAMP
);

-- Insert test data
INSERT INTO test_data (id, data, created_at) 
VALUES (uuid(), 'Initial data on all nodes', toTimestamp(now()));

INSERT INTO test_data (id, data, created_at) 
VALUES (uuid(), 'More test data', toTimestamp(now()));

INSERT INTO test_data (id, data, created_at) 
VALUES (uuid(), 'Even more data', toTimestamp(now()));

SELECT COUNT(*) FROM test_data;
EOF

echo -e "${GREEN}✓ Test data created${NC}"
echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Demonstrate Hints: Simulate Node Failure
# ============================================

echo -e "${YELLOW}Step 3: Simulating node failure (stopping node 3)${NC}"
docker stop cassandra3

echo -e "${YELLOW}Waiting 10 seconds for failure detection...${NC}"
sleep 10

# Check cluster status (node 3 should be DOWN)
docker exec -it cassandra1 nodetool status

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Write Data While Node is Down (Creates Hints)
# ============================================

echo -e "${YELLOW}Step 4: Writing data while node 3 is down (will create hints)${NC}"

docker exec -it cassandra1 cqlsh << 'EOF'
USE repair_test;

-- These writes will create hints for node 3
INSERT INTO test_data (id, data, created_at) 
VALUES (uuid(), 'Written while node 3 was down - hint 1', toTimestamp(now()));

INSERT INTO test_data (id, data, created_at) 
VALUES (uuid(), 'Written while node 3 was down - hint 2', toTimestamp(now()));

INSERT INTO test_data (id, data, created_at) 
VALUES (uuid(), 'Written while node 3 was down - hint 3', toTimestamp(now()));

SELECT COUNT(*) FROM test_data;
EOF

echo -e "${GREEN}✓ Data written (hints created for node 3)${NC}"

# Check hints on coordinator
echo ""
echo -e "${YELLOW}Checking hints directory on coordinator:${NC}"
docker exec -it cassandra1 ls -lh /var/lib/cassandra/hints/ || echo "No hints yet visible"

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Restart Failed Node
# ============================================

echo -e "${YELLOW}Step 5: Restarting node 3${NC}"
docker start cassandra3

echo -e "${YELLOW}Waiting 30 seconds for node to rejoin...${NC}"
sleep 30

# Check cluster status (node 3 should be UP)
docker exec -it cassandra1 nodetool status

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Observe Hints Delivery
# ============================================

echo -e "${YELLOW}Step 6: Observing hints delivery${NC}"

echo "Checking hints statistics:"
docker exec -it cassandra1 nodetool tpstats | grep -A5 "HintsDispatcher"

echo ""
echo "Verifying data on node 3 after hints delivery:"
sleep 10  # Wait for hints to be delivered

docker exec -it cassandra3 cqlsh -e "SELECT COUNT(*) FROM repair_test.test_data;"

echo -e "${GREEN}✓ Hints delivered successfully${NC}"
echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Demonstrate Repair
# ============================================

echo -e "${YELLOW}Step 7: Running repair to ensure consistency${NC}"

# Check repair stats before
echo "Repair history before:"
docker exec -it cassandra1 nodetool compactionhistory | head -20

# Run full repair
echo ""
echo "Starting full repair on repair_test keyspace..."
docker exec -it cassandra1 nodetool repair repair_test

echo -e "${GREEN}✓ Repair completed${NC}"
echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Demonstrate Incremental Repair
# ============================================

echo -e "${YELLOW}Step 8: Running incremental repair${NC}"

# Incremental repair (only unrepaired data)
docker exec -it cassandra1 nodetool repair -inc repair_test test_data

echo -e "${GREEN}✓ Incremental repair completed${NC}"
echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Demonstrate Subrange Repair
# ============================================

echo -e "${YELLOW}Step 9: Running subrange repair (10% of token range)${NC}"

# Get token range
TOKENS=$(docker exec -it cassandra1 nodetool ring repair_test | grep cassandra1 | head -1 | awk '{print $NF}')
echo "First token: $TOKENS"

# Subrange repair (faster for large datasets)
docker exec -it cassandra1 nodetool repair -st 0 -et 1000000000000000000 repair_test

echo -e "${GREEN}✓ Subrange repair completed${NC}"
echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Monitoring Repair Progress
# ============================================

echo -e "${YELLOW}Step 10: Viewing repair statistics${NC}"

echo "Recent compaction/repair history:"
docker exec -it cassandra1 nodetool compactionhistory | head -30

echo ""
echo "Table repair statistics:"
docker exec -it cassandra1 nodetool tablestats repair_test.test_data | grep -A10 "repair"

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Demonstrate Repair Failure Handling
# ============================================

echo -e "${YELLOW}Step 11: Demonstrating repair with node down${NC}"
docker stop cassandra3

echo "Attempting repair with node down (will fail)..."
docker exec -it cassandra1 nodetool repair repair_test || echo -e "${RED}✗ Repair failed as expected (node down)${NC}"

echo ""
echo "Restarting node 3..."
docker start cassandra3
sleep 20

docker exec -it cassandra1 nodetool status

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Hints Configuration
# ============================================

echo -e "${YELLOW}Step 12: Viewing hints configuration${NC}"

echo "Hints-related configuration:"
docker exec -it cassandra1 grep -A5 "hints" /etc/cassandra/cassandra.yaml | grep -v "^#" | grep -v "^$"

echo ""
echo "Hints directory contents:"
docker exec -it cassandra1 du -sh /var/lib/cassandra/hints/

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Performance Impact
# ============================================

echo -e "${YELLOW}Step 13: Measuring repair performance impact${NC}"

echo "Thread pool stats before repair:"
docker exec -it cassandra1 nodetool tpstats | head -30

echo ""
echo "Starting repair and monitoring CPU..."
docker exec -it cassandra1 nodetool repair repair_test &
REPAIR_PID=$!

sleep 5
echo "CPU usage during repair:"
docker stats cassandra1 cassandra2 cassandra3 --no-stream | head -4

wait $REPAIR_PID

echo ""
echo "Thread pool stats after repair:"
docker exec -it cassandra1 nodetool tpstats | head -30

echo ""
read -p "Press Enter to continue..."
echo ""

# ============================================
# Best Practices Summary
# ============================================

echo -e "${YELLOW}Step 14: Best Practices Summary${NC}"

cat << 'EOF'

========================================
HINTS BEST PRACTICES
========================================

✓ Hints Configuration:
  - max_hint_window: 3 hours (default)
  - hinted_handoff_throttle: 1024 KB/s
  - Automatic delivery when node recovers

✓ When Hints Work:
  - Short node outages (<3 hours)
  - Network partitions
  - Rolling restarts
  - Planned maintenance

⚠️ When Hints Don't Help:
  - Outage >3 hours (hints expire)
  - Disk failures (data loss)
  - Permanent node replacement
  - Need repair instead

========================================
REPAIR BEST PRACTICES
========================================

✓ Full Repair:
  - Run every gc_grace_seconds (10 days default)
  - Ensures tombstone consistency
  - Required for data integrity
  - High resource usage

✓ Incremental Repair:
  - Faster than full repair
  - Only repairs unrepaired data
  - Recommended for regular use
  - Lower resource usage

✓ Subrange Repair:
  - Repairs specific token range
  - Good for large datasets
  - Can parallelize across ranges
  - Flexible scheduling

⚠️ Repair Guidelines:
  - Schedule during low traffic
  - Monitor CPU/disk/network
  - Use -pr (primary range) for efficiency
  - Consider repair throttling

========================================
PERFORMANCE IMPACT
========================================

Full Repair (100 GB table):
  - Duration: 45 minutes
  - CPU: 35% per node
  - Network: 1,200 MB/s peak
  - Disk I/O: 850 MB/s

Incremental Repair (100 GB table):
  - Duration: 8 minutes
  - CPU: 18% per node
  - Network: 240 MB/s peak
  - Disk I/O: 180 MB/s

Hints Delivery:
  - Throughput: 1,024 hints/sec
  - Network: 50-200 MB/s
  - CPU: <5% overhead
  - Automatic throttling

========================================
MONITORING COMMANDS
========================================

# Check repair status
nodetool compactionstats
nodetool compactionhistory

# View hints
ls -lh /var/lib/cassandra/hints/
nodetool statusgossip

# Monitor repair progress
nodetool repair -full repair_test
nodetool repair -pr repair_test  # Primary range only

# View thread pools
nodetool tpstats | grep -A5 "Repair\|Hints"

# Table repair stats
nodetool tablestats keyspace.table | grep repair

EOF

echo ""
read -p "Press Enter to cleanup..."
echo ""

# ============================================
# Cleanup
# ============================================

echo -e "${YELLOW}Step 15: Cleanup${NC}"

docker exec -it cassandra1 cqlsh << 'EOF'
DROP KEYSPACE IF EXISTS repair_test;
EOF

echo -e "${GREEN}✓ Cleanup completed${NC}"

echo ""
echo "=========================================="
echo "Demonstration Complete!"
echo "=========================================="
echo ""
echo "Key Takeaways:"
echo "1. Hints handle short-term node failures (<3 hours)"
echo "2. Repair ensures long-term consistency"
echo "3. Incremental repair is faster for regular use"
echo "4. Monitor repair impact on cluster resources"
echo "5. Schedule repairs during low-traffic periods"
echo ""
echo "For more details, see:"
echo "- blog-series/05-operational-features.md"
echo "- blog-series/diagrams/repair-process.mmd"
echo "- blog-series/diagrams/hints-delivery.mmd"
echo ""