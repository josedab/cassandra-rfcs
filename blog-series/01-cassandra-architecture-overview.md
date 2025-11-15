# Apache Cassandra Architecture: A Deep Dive into Distributed Database Design

## Introduction

Apache Cassandra is a highly-scalable, distributed NoSQL database that implements a partitioned wide-column storage model with eventually consistent semantics. Born from a collaboration between Amazon's Dynamo distributed storage techniques and Google's Bigtable data model, Cassandra represents a sophisticated synthesis designed to meet the challenges of global-scale applications requiring high availability, fault tolerance, and linear scalability.

In this comprehensive overview, we'll explore the architectural foundations that make Cassandra one of the most robust distributed databases available today.

## Historical Context and Design Philosophy

Cassandra was initially designed at Facebook using a Staged Event-Driven Architecture (SEDA) to address limitations in existing database systems. The project sought to combine the best aspects of:

- **Amazon Dynamo**: Distributed storage, replication techniques, and eventual consistency
- **Google Bigtable**: Data and storage engine model

The core design objectives that have guided Cassandra's evolution include:

1. **Full Multi-Primary Replication**: Every node can accept reads and writes for the data it's responsible for
2. **Global Availability at Low Latency**: Designed for worldwide deployment with local access patterns
3. **Horizontal Scalability**: Linear throughput increases with additional hardware
4. **Online Load Balancing**: Cluster topology changes without downtime
5. **Flexible Schema**: Schema modifications without application downtime
6. **Commodity Hardware**: Runs efficiently on standard, affordable infrastructure

## Core Architectural Components

### 1. **Partitioning and Token Ring**

Cassandra uses consistent hashing to distribute data across nodes in a cluster. Each piece of data is assigned to a node based on a partition key, which is hashed to produce a token.

**Key Implementation Details** ([`org.apache.cassandra.dht.Murmur3Partitioner`](src/java/org/apache/cassandra/dht/Murmur3Partitioner.java:1)):
- Default partitioner is `Murmur3Partitioner`, offering ~10% better performance than `RandomPartitioner`
- Token space divided into ranges, with each node responsible for one or more ranges
- Virtual nodes (vnodes) allow dynamic token allocation, improving load distribution
- Default `num_tokens` set to 16 for optimal balance between performance and flexibility

**Token Allocation** ([`cassandra.yaml`](conf/cassandra.yaml:42)):
```yaml
num_tokens: 16
allocate_tokens_for_local_replication_factor: 3
```

### 2. **Distributed Storage Engine**

The storage engine manages data persistence through a multi-layered architecture:

#### Memtables (In-Memory Buffer)
- **SkipListMemtable**: Legacy implementation using concurrent skip lists
- **TrieMemtable**: Modern trie-based structure reducing GC pressure and improving write throughput
  
From [`ColumnFamilyStore.java`](src/java/org/apache/cassandra/db/ColumnFamilyStore.java:515):
```java
Memtable initialMemtable = DatabaseDescriptor.isDaemonInitialized() ?
                           createMemtable(new AtomicReference<>(CommitLog.instance.getCurrentPosition())) :
                           null;
```

#### Commit Log
- Write-ahead log ensuring durability
- Supports multiple sync modes: periodic, batch, and group
- Direct I/O support for improved performance (Java 10+)

#### SSTables (Sorted String Tables)
Cassandra 5.x supports two SSTable formats:
- **BIG Format**: Legacy format (Cassandra 3.0+)
- **BTI Format**: Trie-indexed format with superior performance
  - Removes index summary component
  - Eliminates need for key caching
  - Efficient searching in partitions with millions of rows

### 3. **Gossip Protocol and Cluster Membership**

The [`Gossiper`](src/java/org/apache/cassandra/gms/Gossiper.java:132) implements a peer-to-peer communication protocol for node discovery and failure detection:

```java
public class Gossiper implements IFailureDetectionEventListener, GossiperMBean, IGossiper
{
    // Gossip runs every 1 second
    public final static int intervalInMillis = 1000;
    
    // Nodes quarantined for 2 * RING_DELAY after removal
    public final static int QUARANTINE_DELAY = GOSSIPER_QUARANTINE_DELAY.getInt(
        StorageService.RING_DELAY_MILLIS * 2
    );
}
```

**Key Features**:
- Epidemic protocol for state dissemination
- Phi Accrual Failure Detector for node health monitoring
- Application state propagation (schema, tokens, status)
- Automatic dead node detection and removal

### 4. **Transactional Cluster Metadata (CEP-21)**

Cassandra 5.1 introduces a transformative change with Transactional Cluster Metadata Service (CMS):

**From [`NEWS.txt`](NEWS.txt:89)**:
> CEP-21 Transactional Cluster Metadata introduces a distributed log for linearizing modifications to cluster metadata. In the first instance, this encompasses cluster membership, token ownership and schema metadata.

**Benefits**:
- Linearized schema changes across the cluster
- Atomic topology modifications
- Elimination of schema disagreements
- Foundation for future advanced features

### 5. **Replication and Consistency**

Cassandra implements tunable consistency through multiple replication strategies:

**Replication Strategies**:
- **SimpleStrategy**: Single-datacenter deployments
- **NetworkTopologyStrategy**: Multi-datacenter with per-DC replication control
- **LocalStrategy**: Non-replicated system tables

**Consistency Levels**:
```
Writes: ANY, ONE, TWO, THREE, QUORUM, LOCAL_QUORUM, EACH_QUORUM, ALL
Reads: ONE, TWO, THREE, QUORUM, LOCAL_QUORUM, EACH_QUORUM, ALL, LOCAL_ONE
```

From [`StorageService.java`](src/java/org/apache/cassandra/service/StorageService.java:2987):
```java
public void refreshSizeEstimates() throws ExecutionException
{
    cleanupSizeEstimates();
    FBUtilities.waitOnFuture(
        ScheduledExecutors.optionalTasks.submit(SizeEstimatesRecorder.instance)
    );
}
```

### 6. **Compaction Strategies**

Cassandra offers multiple compaction strategies optimized for different workloads:

#### Size-Tiered Compaction Strategy (STCS)
- Default strategy for write-heavy workloads
- Groups similarly-sized SSTables for compaction
- Lower I/O cost but higher space amplification

#### Leveled Compaction Strategy (LCS)
From [`LeveledCompactionStrategy.java`](src/java/org/apache/cassandra/db/compaction/LeveledCompactionStrategy.java:65):
- Fixed-size SSTables organized in levels
- Lower space amplification (~10% overhead)
- Better read performance, higher write amplification

#### Time Window Compaction Strategy (TWCS)
- Optimized for time-series data
- Creates time-based windows of SSTables
- Efficient expiration of old data

#### Unified Compaction Strategy (UCS) - New in 5.0
From [`UnifiedCompactionStrategy.md`](src/java/org/apache/cassandra/db/compaction/UnifiedCompactionStrategy.md:19):
- Combines benefits of all strategies
- Adaptive sharding for parallelization
- Density-based leveling
- Configurable for various workloads

### 7. **Read and Write Paths**

#### Write Path
1. **Memtable Write**: Data written to active memtable
2. **Commit Log**: Durably logged (if enabled)
3. **Secondary Indexes**: Updated synchronously
4. **Materialized Views**: Updated asynchronously via batchlog

From [`ColumnFamilyStore.java`](src/java/org/apache/cassandra/db/ColumnFamilyStore.java:1498):
```java
public void apply(PartitionUpdate update, CassandraWriteContext context, boolean updateIndexes)
{
    long start = nanoTime();
    OpOrder.Group opGroup = context.getGroup();
    CommitLogPosition commitLogPosition = context.getPosition();
    
    Memtable mt = data.getMemtableFor(opGroup, commitLogPosition);
    UpdateTransaction indexer = newUpdateTransaction(update, context, updateIndexes, mt);
    long timeDelta = mt.put(update, indexer, opGroup);
    
    metric.writeLatency.addNano(nanoTime() - start);
}
```

#### Read Path
1. **Row Cache Check**: Optional partition-level cache
2. **Memtable Query**: Search active and flushing memtables
3. **SSTable Query**: Use bloom filters, partition index, and data files
4. **Merge Results**: Reconcile by timestamp using last-write-wins

## Configuration and Tuning

Cassandra's flexibility comes from extensive configuration options in [`cassandra.yaml`](conf/cassandra.yaml:1):

### Memory Management
```yaml
memtable_heap_space: 2048MiB
memtable_offheap_space: 2048MiB
key_cache_size: auto  # min(5% heap, 100MiB)
row_cache_size: 0MiB  # Disabled by default
```

### Compaction and I/O
```yaml
compaction_throughput: 64MiB/s
concurrent_compactors: auto  # min(disks, cores)
stream_throughput_outbound: 24MiB/s
```

### Durability and Consistency
```yaml
commitlog_sync: periodic
commitlog_sync_period: 10000ms
commitlog_segment_size: 32MiB
```

## Advanced Features

### 1. **Hints and Hinted Handoff**

Cassandra 3.0+ completely rewrote hints for improved performance:
- Hints stored in flat files (not in tables)
- More efficient dispatch mechanism
- Configurable compression

From [`cassandra.yaml`](conf/cassandra.yaml:88):
```yaml
hinted_handoff_throttle: 1024KiB
max_hints_delivery_threads: 2
max_hint_window: 3h
```

### 2. **Repair Mechanisms**

Multiple repair strategies for data consistency:
- **Full Repair**: Complete data validation across replicas
- **Incremental Repair**: Only unrepaired data
- **Subrange Repair**: Specific token ranges
- **Auto Repair** (5.1+): Fully automated repair scheduling

### 3. **Storage-Attached Indexes (SAI)**

Introduced in Cassandra 5.0 as the next-generation secondary indexing:
- Optimized SSTable and memtable-attached indexes
- Vector similarity search support
- Superior performance compared to legacy indexes

### 4. **Change Data Capture (CDC)**

Enables real-time data change tracking:
```yaml
cdc_enabled: false
cdc_block_writes: true
cdc_on_repair_enabled: true
```

## Operational Excellence

### Monitoring and Observability

Cassandra exposes comprehensive metrics through:
- **JMX MBeans**: Real-time cluster statistics
- **Virtual Tables**: System metrics queryable via CQL
- **Diagnostic Events**: Detailed operational insights
- **Full Query Logger**: Complete query auditing

### Tools and Utilities

1. **nodetool**: Primary operational tool
2. **cqlsh**: CQL shell for data access
3. **sstable utilities**: Data inspection and manipulation
4. **cassandra-stress**: Performance testing

## Performance Characteristics

### Strengths

1. **Linear Write Scalability**: Adding nodes proportionally increases write capacity
2. **High Availability**: No single point of failure
3. **Tunable Consistency**: Balance between consistency and availability
4. **Efficient Wide Rows**: Handles billions of columns per partition
5. **Geographic Distribution**: Multi-datacenter replication

### Considerations

1. **Read Performance**: Depends heavily on caching and compaction strategy
2. **Data Modeling**: Requires query-first design approach
3. **Tombstone Management**: Deleted data creates tombstone overhead
4. **Repair Overhead**: Regular repairs needed for eventual consistency
5. **JVM Tuning**: Requires appropriate garbage collection configuration

## Modern Enhancements (5.0+)

### Storage Compatibility Mode
Allows gradual adoption of new features:
```yaml
storage_compatibility_mode: NONE  # CASSANDRA_4, UPGRADING, or NONE
```

### Extended TTL Support
- Maximum expiration date extended to 2106-02-07 (from 2038-01-19)
- Backward compatible with controlled upgrade path

### Accord Transactions (Experimental)
CEP-15 introduces general-purpose transactions:
- Multi-partition ACID transactions
- Optimistic concurrency control
- Foundation for complex application patterns

## Conclusion

Apache Cassandra's architecture represents decades of distributed systems research and production experience. Its layered design—from the gossip protocol for cluster membership to sophisticated compaction strategies and tunable consistency—provides the foundation for building globally distributed, always-available applications.

The upcoming CEP-21 Transactional Cluster Metadata represents the most significant architectural evolution since Cassandra's creation, addressing long-standing operational challenges while laying groundwork for future innovations.

In subsequent posts, we'll dive deeper into specific components:
- Storage engine internals and compaction strategies
- Distributed systems aspects (gossip, replication, consistency)
- Query processing and performance optimization
- Advanced operational features
- Schema design patterns and best practices

---

**About this Series**: This blog post is part of a comprehensive series exploring Apache Cassandra's architecture, implementation, and evolution. Each post targets both newcomers seeking to understand Cassandra's design and experienced practitioners looking to optimize their deployments.

**Project Version**: Apache Cassandra 5.1 (in development)
**License**: Apache License 2.0
**Repository**: https://github.com/apache/cassandra