# Cassandra Docker Setup

This Docker Compose setup provides a 3-node Apache Cassandra 5.1 cluster for learning, testing, and exploring the concepts discussed in the blog series.

## Prerequisites

- Docker Desktop or Docker Engine (20.10+)
- Docker Compose (2.0+)
- At least 8GB RAM available for Docker
- At least 10GB free disk space

## Quick Start

### 1. Start the Cluster

```bash
cd examples/docker-setup
docker-compose up -d
```

This will:
- Pull the Cassandra 5.1 image (if not already downloaded)
- Start three Cassandra nodes sequentially
- Wait for each node to be healthy before starting the next
- Set up a network for inter-node communication

**Expected startup time:** 3-5 minutes for all nodes to be ready

### 2. Verify Cluster Status

```bash
# Check that all containers are running
docker-compose ps

# Check cluster status from node 1
docker exec -it cassandra1 nodetool status
```

Expected output:
```
Datacenter: DC1
===============
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address     Load       Tokens  Owns (effective)  Host ID                               Rack
UN  172.18.0.2  75.91 KiB  16      100.0%            a1b2c3d4-...                          RAC1
UN  172.18.0.3  75.91 KiB  16      100.0%            e5f6g7h8-...                          RAC2
UN  172.18.0.4  75.91 KiB  16      100.0%            i9j0k1l2-...                          RAC1
```

### 3. Connect with CQL Shell

```bash
# Connect to node 1
docker exec -it cassandra1 cqlsh

# Or connect to node 2
docker exec -it cassandra2 cqlsh

# Or connect to node 3
docker exec -it cassandra3 cqlsh
```

### 4. Stop the Cluster

```bash
# Stop all nodes
docker-compose down

# Stop and remove data volumes (CAUTION: This deletes all data)
docker-compose down -v
```

## Cluster Configuration

### Node Details

| Node | Container | CQL Port | JMX Port | Data Center | Rack |
|------|-----------|----------|----------|-------------|------|
| 1 | cassandra1 | 9042 | 7199 | DC1 | RAC1 |
| 2 | cassandra2 | 9043 | - | DC1 | RAC2 |
| 3 | cassandra3 | 9044 | - | DC1 | RAC1 |

### Key Settings

- **Cluster Name:** BlogExampleCluster
- **Num Tokens:** 16 (virtual nodes per physical node)
- **Snitch:** GossipingPropertyFileSnitch
- **Heap Size:** 2GB max, 400MB new generation
- **Replication:** Single datacenter (DC1) with two racks

### Network Architecture

```
┌─────────────────────────────────────────┐
│        cassandra-network (bridge)       │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  │  Node 1  │  │  Node 2  │  │  Node 3  │
│  │ (Seed)   │  │          │  │          │
│  │ RAC1     │  │ RAC2     │  │ RAC1     │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘
│       │             │             │      │
│       └─────────────┴─────────────┘      │
│              Gossip Protocol             │
└─────────────────────────────────────────┘
```

## Common Operations

### Checking Cluster Health

```bash
# View cluster ring
docker exec -it cassandra1 nodetool ring

# Check node gossip info
docker exec -it cassandra1 nodetool gossipinfo

# View cluster status with token distribution
docker exec -it cassandra1 nodetool status

# Check individual node info
docker exec -it cassandra1 nodetool info
```

### Monitoring and Metrics

```bash
# View thread pool stats
docker exec -it cassandra1 nodetool tpstats

# Check compaction stats
docker exec -it cassandra1 nodetool compactionstats

# Monitor GC activity
docker exec -it cassandra1 nodetool gcstats

# View table statistics for a keyspace
docker exec -it cassandra1 nodetool tablestats your_keyspace
```

### Log Access

```bash
# View logs from node 1
docker logs cassandra1 -f

# View last 100 lines
docker logs cassandra1 --tail 100

# View logs from all nodes
docker-compose logs -f
```

### Data Operations

```bash
# Flush memtables to disk
docker exec -it cassandra1 nodetool flush

# Run repair on a keyspace
docker exec -it cassandra1 nodetool repair your_keyspace

# Take a snapshot
docker exec -it cassandra1 nodetool snapshot your_keyspace

# Clear snapshots
docker exec -it cassandra1 nodetool clearsnapshot
```

## Example Workloads

### Creating a Test Keyspace

```sql
-- Connect via cqlsh first
docker exec -it cassandra1 cqlsh

-- Create keyspace with RF=3
CREATE KEYSPACE example
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'DC1': 3
};

USE example;

-- Create a simple table
CREATE TABLE users (
  user_id UUID PRIMARY KEY,
  username TEXT,
  email TEXT,
  created_at TIMESTAMP
);

-- Insert test data
INSERT INTO users (user_id, username, email, created_at)
VALUES (uuid(), 'alice', 'alice@example.com', toTimestamp(now()));

-- Query the data
SELECT * FROM users;
```

### Testing Replication

```sql
-- Insert data on node 1
docker exec -it cassandra1 cqlsh -e "
  USE example;
  INSERT INTO users (user_id, username, email, created_at)
  VALUES (uuid(), 'bob', 'bob@example.com', toTimestamp(now()));
"

-- Query from node 2 (data should be replicated)
docker exec -it cassandra2 cqlsh -e "
  USE example;
  SELECT * FROM users WHERE username = 'bob' ALLOW FILTERING;
"

-- Query from node 3
docker exec -it cassandra3 cqlsh -e "
  USE example;
  SELECT COUNT(*) FROM users;
"
```

### Testing Consistency Levels

```sql
-- Write with QUORUM (requires 2 of 3 nodes)
CONSISTENCY QUORUM;
INSERT INTO users (user_id, username, email, created_at)
VALUES (uuid(), 'charlie', 'charlie@example.com', toTimestamp(now()));

-- Read with ONE (any single replica)
CONSISTENCY ONE;
SELECT * FROM users;

-- Read with ALL (requires all 3 replicas)
CONSISTENCY ALL;
SELECT * FROM users;

-- Read with LOCAL_QUORUM
CONSISTENCY LOCAL_QUORUM;
SELECT * FROM users;
```

## Advanced Scenarios

### Simulating Node Failure

```bash
# Stop node 3 to simulate failure
docker stop cassandra3

# Check cluster status (node 3 should be DOWN)
docker exec -it cassandra1 nodetool status

# Verify reads/writes still work with QUORUM
docker exec -it cassandra1 cqlsh -e "
  CONSISTENCY QUORUM;
  USE example;
  SELECT COUNT(*) FROM users;
"

# Restart the failed node
docker start cassandra3

# Wait for it to rejoin and run repair
sleep 60
docker exec -it cassandra3 nodetool repair example
```

### Testing Compaction Strategies

```sql
-- Create tables with different compaction strategies
CREATE TABLE stcs_example (
  id UUID PRIMARY KEY,
  data TEXT
) WITH compaction = {
  'class': 'SizeTieredCompactionStrategy',
  'min_threshold': 4,
  'max_threshold': 32
};

CREATE TABLE lcs_example (
  id UUID PRIMARY KEY,
  data TEXT
) WITH compaction = {
  'class': 'LeveledCompactionStrategy',
  'sstable_size_in_mb': 160
};

CREATE TABLE twcs_example (
  id UUID PRIMARY KEY,
  timestamp TIMESTAMP,
  data TEXT
) WITH compaction = {
  'class': 'TimeWindowCompactionStrategy',
  'compaction_window_unit': 'DAYS',
  'compaction_window_size': 1
};
```

### Load Testing with cassandra-stress

```bash
# Write 1 million rows
docker exec -it cassandra1 cassandra-stress write \
  n=1000000 \
  -rate threads=50 \
  -node cassandra1,cassandra2,cassandra3

# Read with mixed workload
docker exec -it cassandra1 cassandra-stress mixed \
  ratio\(write=1,read=3\) \
  n=1000000 \
  -rate threads=50 \
  -node cassandra1,cassandra2,cassandra3

# User-defined workload from profile
docker exec -it cassandra1 cassandra-stress user \
  profile=/path/to/profile.yaml \
  n=100000 \
  -node cassandra1
```

## Troubleshooting

### Cluster Won't Start

**Symptom:** Containers exit immediately or restart loop

**Check:**
```bash
# View container logs
docker logs cassandra1

# Common issues:
# 1. Insufficient memory (increase Docker memory limit)
# 2. Port conflicts (check if 9042 is already in use)
# 3. Corrupted data volumes
```

**Solution:**
```bash
# Remove all data and restart fresh
docker-compose down -v
docker-compose up -d
```

### Nodes Not Joining Cluster

**Symptom:** `nodetool status` shows only one node

**Check:**
```bash
# Verify network connectivity
docker network inspect docker-setup_cassandra-network

# Check if seed node is healthy
docker exec -it cassandra1 nodetool status
```

**Solution:**
```bash
# Restart dependent nodes in order
docker restart cassandra2
sleep 60
docker restart cassandra3
```

### Connection Refused Errors

**Symptom:** `cqlsh` cannot connect

**Check:**
```bash
# Verify CQL port is listening
docker exec -it cassandra1 netstat -tlnp | grep 9042

# Check if node is ready
docker exec -it cassandra1 nodetool statusbinary
```

**Solution:**
```bash
# Wait for node to fully initialize (check logs)
docker logs cassandra1 -f | grep "Listening for thrift clients"

# Or enable binary protocol manually
docker exec -it cassandra1 nodetool enablebinary
```

### Out of Memory Errors

**Symptom:** Nodes crash with OOM errors in logs

**Solution:**
```yaml
# Edit docker-compose.yml and reduce heap size
environment:
  - MAX_HEAP_SIZE=1G
  - HEAP_NEWSIZE=200M
```

## Performance Tips

### Optimal Docker Resources

- **CPU:** Allocate at least 2 cores per node (6 total recommended)
- **Memory:** Allocate at least 3GB per node (10GB total recommended)
- **Storage:** Use volumes (not bind mounts) for better I/O performance

### Monitoring JMX Metrics

```bash
# Install JMX tools
docker exec -it cassandra1 apt-get update && apt-get install -y openjdk-11-jdk

# Connect via JConsole
# From your host machine, connect to localhost:7199
```

## Cleanup

### Remove All Data

```bash
# Stop and remove containers, networks, and volumes
docker-compose down -v

# Remove downloaded images (optional)
docker rmi cassandra:5.1
```

### Preserve Data Between Restarts

```bash
# Stop cluster (keeps volumes)
docker-compose stop

# Start cluster (reuses existing data)
docker-compose start
```

## Next Steps

- Explore the blog series examples in `../part-{1-6}/`
- Try the performance benchmarks in `../../blog-series/PERFORMANCE-BENCHMARKS.md`
- Experiment with different consistency levels and replication factors
- Test failure scenarios and observe repair mechanisms
- Load your own data and run custom queries

## References

- [Official Cassandra Docker Image](https://hub.docker.com/_/cassandra)
- [Cassandra Configuration](https://cassandra.apache.org/doc/latest/configuration/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [nodetool Reference](https://cassandra.apache.org/doc/latest/tools/nodetool/)