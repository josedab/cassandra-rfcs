# Interactive Code Examples - Apache Cassandra Blog Series

This directory contains working code examples that accompany the blog series. Each example can be run locally using Docker Compose.

## Quick Start

```bash
# Start 3-node Cassandra cluster
cd examples/docker-setup
docker-compose up -d

# Wait for cluster to be ready (~60 seconds)
docker-compose exec cassandra1 nodetool status

# Run example from Part 6 (Schema Design)
cd ../schema-design
./load-social-media-example.sh
```

## Directory Structure

```
examples/
├── docker-setup/           # 3-node Cassandra 5.1 cluster
├── part1-architecture/     # Architecture examples
├── part2-storage-engine/   # Compaction strategy demos
├── part3-distributed/      # Replication and consistency
├── part4-query-processing/ # Query optimization examples
├── part5-operations/       # Operational tasks
├── part6-schema-design/    # Complete schema examples
└── datasets/              # Sample data for all examples
```

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- 8GB RAM minimum
- 20GB disk space

## Examples by Blog Post

### Part 1: Architecture Overview
- **Token Ring Visualization**: See how data distributes across nodes
- **Gossip Protocol**: Monitor cluster membership changes
- **Replication Demo**: Verify RF=3 replication

### Part 2: Storage Engine & Compaction
- **Compaction Strategy Comparison**: Benchmark STCS vs LCS vs UCS
- **Memtable Types**: TrieMemtable vs SkipList performance
- **SSTable Format**: BIG vs BTI format comparison

### Part 3: Distributed Systems
- **Consistency Levels**: Measure latency across ONE, QUORUM, ALL
- **Read Repair**: Trigger and observe read repair
- **Paxos LWTs**: Compare V1 vs V2 performance
- **Multi-DC Setup**: 2 datacenter configuration

### Part 4: Query Processing
- **SAI vs Legacy Indexes**: Performance comparison
- **Prepared Statements**: Benchmark prepared vs non-prepared
- **Query Tracing**: Detailed execution analysis
- **Cache Impact**: Row cache effectiveness

### Part 5: Operational Features
- **Auto Repair**: Configure and monitor CEP-37 repair
- **Snapshot/Restore**: Complete backup workflow
- **Node Operations**: Add/remove/replace procedures
- **Monitoring**: JMX metrics collection

### Part 6: Schema Design
- **Social Media Feed**: Complete working example
- **E-Commerce Orders**: Multi-table denormalization
- **IoT Sensor Network**: Time-series with bucketing
- **User Sessions**: TTL and expiration

## Running Individual Examples

Each example directory contains:
- `schema.cql` - Table definitions
- `data.cql` - Sample data inserts
- `queries.cql` - Example queries
- `benchmark.sh` - Performance testing script
- `README.md` - Specific instructions

Example:
```bash
cd examples/part6-schema-design/social-media-feed

# Load schema and data
cqlsh -f schema.cql
cqlsh -f data.cql

# Run queries
cqlsh -f queries.cql

# Benchmark
./benchmark.sh
```

## Performance Testing

All examples include benchmarking scripts using `cassandra-stress`:

```bash
cd examples/part4-query-processing/prepared-statements
./benchmark.sh

# Output:
# Prepared statements:     10,000 ops/sec, p99: 5.2ms
# Non-prepared statements:  3,000 ops/sec, p99: 15.8ms
# Improvement: 3.3x throughput, 3.0x lower latency
```

## Troubleshooting

### Cluster won't start
```bash
# Check logs
docker-compose logs cassandra1

# Reset cluster
docker-compose down -v
docker-compose up -d
```

### Out of memory
```bash
# Reduce heap size in docker-compose.yml
environment:
  - MAX_HEAP_SIZE=1G
  - HEAP_NEWSIZE=200M
```

### Connection refused
```bash
# Wait for startup to complete
watch docker-compose exec cassandra1 nodetool status

# Should show all nodes UN (Up/Normal)
```

## Sample Data

The `datasets/` directory contains realistic sample data:
- **users.csv**: 100,000 user profiles
- **posts.csv**: 1,000,000 social media posts  
- **orders.csv**: 500,000 e-commerce orders
- **sensors.csv**: 10,000,000 IoT readings

Load with:
```bash
cqlsh -e "COPY keyspace.table FROM 'datasets/file.csv'"
```

## Additional Resources

- [Docker Compose Reference](docker-setup/README.md)
- [Schema Design Patterns](part6-schema-design/README.md)
- [Performance Tuning Guide](../blog-series/04-query-processing-optimization.md)
- [Operational Best Practices](../blog-series/05-operational-features.md)

## Contributing

Found an issue or have an improvement? Submit a PR or open an issue in the repository.

## License

Apache License 2.0 (same as Apache Cassandra)