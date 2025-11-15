# Part 1 Examples: Cassandra Architecture Overview

This directory contains hands-on examples demonstrating the core concepts from Part 1 of the blog series.

## Prerequisites

- Docker cluster running (see `../docker-setup/`)
- Basic understanding of CQL syntax

## Quick Start

```bash
# Start the Docker cluster
cd ../docker-setup
docker-compose up -d

# Wait for cluster to be ready
docker exec -it cassandra1 nodetool status

# Run the basic operations example
docker exec -i cassandra1 cqlsh < ../part-1/01-basic-operations.cql
```

## Examples

### 01-basic-operations.cql

Covers fundamental Cassandra operations:
- Creating keyspaces with NetworkTopologyStrategy
- Creating tables with various primary key patterns
- Basic CRUD operations (INSERT, SELECT, UPDATE, DELETE)
- Working with clustering columns
- Batch operations (logged and unlogged)
- Collection types (SET, LIST, MAP)
- Counter tables
- TTL and timestamps

**Learning Objectives:**
- Understand keyspace and replication factor concepts
- Learn primary key design (partition key vs clustering columns)
- Master basic CQL operations
- Explore Cassandra-specific features (counters, TTL, collections)

**Run Time:** ~2 minutes

**Expected Output:**
```
Keyspace created
Table created
5 rows inserted
Counter incremented to 7
```

## Interactive Exploration

### Inspecting Cluster State

```bash
# View cluster status
docker exec -it cassandra1 nodetool status

# Check keyspace info
docker exec -it cassandra1 cqlsh -e "DESCRIBE KEYSPACE example;"

# View table statistics
docker exec -it cassandra1 nodetool tablestats example.users

# Check data distribution
docker exec -it cassandra1 nodetool getendpoints example users <user_id>
```

### Monitoring Operations

```bash
# Watch thread pool stats while running operations
docker exec -it cassandra1 nodetool tpstats

# Monitor compaction
docker exec -it cassandra1 nodetool compactionstats

# View cache statistics
docker exec -it cassandra1 nodetool info
```

## Experiments to Try

### 1. Token Ring Visualization

```bash
# View the token ring
docker exec -it cassandra1 nodetool ring example

# See how data is distributed
docker exec -it cassandra1 cqlsh -e "SELECT token(user_id), user_id, username FROM example.users;"
```

### 2. Replication Verification

```bash
# Insert data on node 1
docker exec -it cassandra1 cqlsh -e "
  USE example;
  INSERT INTO users (user_id, username, email, created_at, account_status)
  VALUES (uuid(), 'node1_user', 'node1@example.com', toTimestamp(now()), 'active');
"

# Verify it's replicated to node 2
docker exec -it cassandra2 cqlsh -e "
  SELECT COUNT(*) FROM example.users;
"

# Check on node 3
docker exec -it cassandra3 cqlsh -e "
  SELECT COUNT(*) FROM example.users;
"
```

### 3. Consistency Level Testing

```bash
# Connect to cqlsh
docker exec -it cassandra1 cqlsh

# Try different consistency levels
CONSISTENCY ONE;
SELECT * FROM example.users;

CONSISTENCY QUORUM;
SELECT * FROM example.users;

CONSISTENCY ALL;
SELECT * FROM example.users;
```

## Common Issues

### Issue: "Keyspace example already exists"

**Solution:**
```bash
# Drop the keyspace first
docker exec -it cassandra1 cqlsh -e "DROP KEYSPACE IF EXISTS example;"

# Then re-run the example
docker exec -i cassandra1 cqlsh < examples/part-1/01-basic-operations.cql
```

### Issue: "Cannot execute this query as it might involve data filtering"

**Solution:**
This is expected! Cassandra requires partition keys for efficient queries. Either:
1. Include the partition key in your WHERE clause
2. Add `ALLOW FILTERING` (not recommended for production)
3. Create a secondary index or materialized view

## Performance Tips

1. **Always specify partition keys** in WHERE clauses
2. **Avoid ALLOW FILTERING** in production queries
3. **Use LIMIT** to bound result sets
4. **Batch wisely** - only batch operations for the same partition
5. **Choose appropriate consistency levels** for your use case

## Next Steps

- Explore Part 2 examples for storage engine deep dive
- Try the compaction strategy comparison in Part 2
- Experiment with consistency levels in Part 3 examples

## References

- [CQL Documentation](https://cassandra.apache.org/doc/latest/cql/)
- [nodetool Reference](https://cassandra.apache.org/doc/latest/tools/nodetool/)
- [Part 1 Blog Post](../../blog-series/01-cassandra-architecture-overview.md)