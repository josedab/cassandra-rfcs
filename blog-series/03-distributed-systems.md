# Apache Cassandra Distributed Systems: Gossip, Replication, and Consistency

## Introduction

At its core, Apache Cassandra is a masterclass in distributed systems design. Built on principles from Amazon's Dynamo and refined through years of production experience, Cassandra's distributed architecture enables linear scalability, fault tolerance, and geographic distribution. This post explores the sophisticated mechanisms that allow Cassandra clusters to operate reliably across datacenters and continents.

## The Gossip Protocol: Peer-to-Peer Communication

Cassandra uses a gossip protocol—an epidemic algorithm—for cluster membership, failure detection, and metadata dissemination.

### Gossip Implementation

From [`Gossiper.java`](src/java/org/apache/cassandra/gms/Gossiper.java:132):

```java
public class Gossiper implements IFailureDetectionEventListener
{
    // Gossip interval: every 1 second
    public final static int intervalInMillis = 1000;
    
    // Quarantine removed nodes for 2 * RING_DELAY (default: 60 seconds)
    public final static int QUARANTINE_DELAY = 
        GOSSIPER_QUARANTINE_DELAY.getInt(StorageService.RING_DELAY_MILLIS * 2);
}
```

### Gossip Round Mechanics

**Three-Phase Exchange**:
1. **GossipDigestSyn**: Initiator sends digest of known endpoints
2. **GossipDigestAck**: Recipient responds with missing/newer information
3. **GossipDigestAck2**: Initiator sends any information recipient needs

**From** [`Gossiper.java`](src/java/org/apache/cassandra/gms/Gossiper.java:243):
```java
private class GossipTask implements Runnable
{
    public void run()
    {
        // Update local heartbeat
        endpointStateMap.get(getBroadcastAddressAndPort()).updateHeartBeat();
        
        // Create gossip digests
        final List<GossipDigest> gDigests = new ArrayList<>();
        Gossiper.instance.makeGossipDigest(gDigests);
        
        // Gossip to live member
        EnumSet<GossipedWith> gossipedWith = doGossipToLiveMember(message);
        
        // Gossip to unreachable member (probabilistically)
        maybeGossipToUnreachableMember(message);
        
        // Gossip to seed if necessary
        if (!gossipedWith.contains(SEED) || liveEndpoints.size() < seeds.size())
            gossipedWith.addAll(maybeGossipToSeed(message));
    }
}
```

### Application State Management

**Gossiped Information**:
- Node tokens and ownership
- Schema version
- Node status (NORMAL, LEAVING, JOINING, etc.)
- Load information
- Network version
- Datacenter/rack location
- RPC readiness

**State Versioning**:
```java
public class EndpointState
{
    private final HeartBeatState hbState;
    private final Map<ApplicationState, VersionedValue> applicationState;
    
    // Vector clock for conflict resolution
    // Higher generation or version wins
}
```

### Failure Detection

**Phi Accrual Failure Detector**:
- Continuous suspicion level (φ) rather than binary up/down
- Adapts to network conditions
- Default conviction threshold: φ = 8

**From** [`Gossiper.java`](src/java/org/apache/cassandra/gms/Gossiper.java:521):
```java
public void convict(InetAddressAndPort endpoint, double phi)
{
    runInGossipStageBlocking(() -> {
        EndpointState epState = endpointStateMap.get(endpoint);
        
        if (isShutdown(endpoint))
            markAsShutdown(endpoint);
        else
            markDead(endpoint, epState);
            
        GossiperDiagnostics.convicted(this, endpoint, phi);
    });
}
```

**Configuration**:
```yaml
phi_convict_threshold: 8  # Higher = more tolerant of delays
```

## Replication Strategies

### Network Topology Strategy (Recommended)

The primary replication strategy for production deployments:

**Configuration**:
```sql
CREATE KEYSPACE production 
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'DC1': 3,
    'DC2': 3,
    'DC3': 2
};
```

**Token Range Calculation**:
From [`StorageService.java`](src/java/org/apache/cassandra/service/StorageService.java:2064):
```java
private EndpointsByRange constructRangeToEndpointMap(String keyspace, 
                                                      List<Range<Token>> ranges)
{
    ClusterMetadata metadata = ClusterMetadata.current();
    KeyspaceMetadata keyspaceMetadata = metadata.schema.getKeyspaces()
                                                .getNullable(keyspace);
    
    // Calculate replicas for each range
    for (Range<Token> range : ranges)
    {
        Token token = tokenMap.nextToken(tokenMap.tokens(), range.right.getToken());
        rangeToEndpointMap.put(range, 
            metadata.placements.get(keyspaceMetadata.params.replication)
                    .reads.forRange(token).get());
    }
}
```

### Data Placement

**Rack Awareness**:
- First replica on node based on token hash
- Subsequent replicas in different racks (if possible)
- Cross-datacenter replicas placed independently

**Example Placement** (RF=3, 2 racks):
```
Token: 100
Primary replica: Node1 (Rack1)
Replica 2: Node3 (Rack2)
Replica 3: Node5 (Rack1, different from Node1)
```

## Consistency Levels and Quorum

### Read Consistency Levels

**ONE**:
- Fastest reads
- May return stale data
- Use case: High throughput, eventual consistency acceptable

**QUORUM**:
```
QUORUM = floor(RF/2) + 1
```
- Majority of replicas must respond
- Balances consistency and availability
- Default for most production use cases

**LOCAL_QUORUM**:
- Quorum within local datacenter only
- Prevents cross-DC latency on reads
- Critical for geo-distributed applications

**ALL**:
- All replicas must respond
- Highest consistency, lowest availability
- Use sparingly (or never in production)

### Write Consistency Levels

**ANY**:
- Write accepted if stored anywhere (including hints)
- Fastest writes, weakest durability guarantees
- Use with caution

**ONE / LOCAL_ONE**:
- Single replica acknowledgment
- Fast writes with reasonable durability
- Common for write-heavy workloads

**QUORUM / LOCAL_QUORUM**:
- Majority must acknowledge
- Strong consistency when combined with QUORUM reads
- Recommended default for balanced workloads

**EACH_QUORUM**:
- Quorum in every datacenter
- Strongest multi-DC consistency
- High latency (multiple datacenter round-trips)

### Tunable Consistency Formula

**Strong Consistency**:
```
R + W > RF

Where:
R = Read consistency level (replicas)
W = Write consistency level (replicas)
RF = Replication factor

Example: QUORUM reads + QUORUM writes with RF=3
(2 + 2) > 3 ✓ Guaranteed consistency
```

## Read Repair and Anti-Entropy

### Read Repair

Cassandra performs read repair when replicas disagree:

**From** [`cassandra.yaml`](conf/cassandra.yaml:1969):
```yaml
# Background read repair removed in 4.0
# Only blocking read repair at CL > ONE
```

**Process**:
1. Coordinator sends read to all replicas (for CL > ONE)
2. First response returned to client
3. Responses compared via digest
4. On mismatch, full data fetched and reconciled
5. Up-to-date version written back to stale replicas

### Anti-Entropy Repair

Full-cluster repair for guaranteed consistency:

**nodetool repair Workflow**:
1. **Merkle Tree Building**: Each replica builds hash tree of data
2. **Tree Comparison**: Identify divergent ranges
3. **Streaming**: Transfer missing/outdated data
4. **Anticompaction** (incremental repair): Segregate repaired data

**Incremental Repair**:
```bash
nodetool repair -par -inc keyspace
```

**Benefits**:
- Only repairs unrepaired data
- Marks repaired SSTables separately
- Subsequent repairs are faster
- Less I/O and network overhead

**Auto Repair** (5.1+):
From [`NEWS.txt`](NEWS.txt:84):
```yaml
auto_repair:
  enabled: true
  repair_type_overrides:
    full:
      enabled: true
      min_repair_interval: 24h
    incremental:
      enabled: false
      min_repair_interval: 24h
```

## Paxos and Lightweight Transactions

Cassandra implements Paxos consensus for linearizable operations (LWTs):

### Paxos V1 (Original)

Four-phase protocol:
1. **Prepare**: Propose ballot
2. **Promise**: Accept if highest ballot seen
3. **Propose**: Send data with ballot
4. **Commit**: Finalize if quorum agrees

### Paxos V2 (5.0+)

**From** [`NEWS.txt`](NEWS.txt:502):
> A new implementation of Paxos (named v2) improves safety and performance of LWT operations. v2 halves the number of WAN messages required and guarantees linearizability across safe range movements.

**Enable**:
```yaml
paxos_variant: v2
paxos_state_purging: repaired  # After regular paxos repairs
```

**Performance Impact**:
- Uncontended writes: 2 round-trips (was 4)
- Uncontended reads: 1 round-trip (was 2-3)
- Safe with range movements

**Usage Example**:
```sql
UPDATE users.profiles
SET email = 'new@example.com'
WHERE user_id = 12345
IF email = 'old@example.com';
```

## Cluster Metadata Management (CEP-21)

The most significant architectural change in Cassandra 5.1:

### Traditional Gossip-Based Metadata

**Problems**:
- Schema disagreements during concurrent changes
- Race conditions in topology changes
- No atomic multi-step operations
- Difficult to reason about consistency

### Transactional Cluster Metadata Service (CMS)

**Architecture**:
- Distributed log for metadata changes
- Paxos consensus for linearizability
- 3-7 nodes designated as CMS members
- All nodes replicate metadata log

**Benefits**:
- Atomic schema changes
- No more schema disagreements
- Safe concurrent DDL operations
- Foundation for advanced features

**Upgrade Process**:
```bash
# Step 1: Rolling upgrade to 5.1
# All nodes running 5.1, still in gossip mode

# Step 2: Initialize CMS (single node only)
nodetool cms initialize

# Step 3: Add CMS members for redundancy
nodetool cms reconfigure DC1:3,DC2:3

# Step 4: Verify
nodetool cms status
```

## Hinted Handoff

Cassandra's mechanism for improving write availability when nodes are temporarily down:

### Architecture (3.0+ Rewrite)

From [`NEWS.txt`](NEWS.txt:1413):
> Hinted Handoff has been completely rewritten. Hints are now stored in flat files, with less overhead and more efficient dispatch.

**Implementation Details**:
- Hints stored per destination node
- Flat file format (not SSTable-based)
- Asynchronous dispatch
- Configurable compression

**From** [`cassandra.yaml`](conf/cassandra.yaml:68):
```yaml
hinted_handoff_enabled: true
max_hint_window: 3h  # Stop hinting after node down this long
hinted_handoff_throttle: 1024KiB  # Per delivery thread
max_hints_delivery_threads: 2
```

### Hint Lifecycle

1. **Write Failure**: Replica doesn't acknowledge within timeout
2. **Hint Creation**: Mutation stored as hint
3. **Node Recovery**: Failure detector marks node UP
4. **Hint Delivery**: Hints replayed to recovered node
5. **Cleanup**: Successfully delivered hints deleted

**Limits** (5.0+):
```yaml
# Limit hint storage per host
max_hints_size_per_host: 0MiB  # 0 = unlimited
```

## Streaming and Bootstrap

Streaming transfers data between nodes during topology changes:

### Bootstrap Process

**From** [`StorageService.java`](src/java/or g/apache/cassandra/service/StorageService.java:1584):
```java
public Future<StreamState> startBootstrap(ClusterMetadata metadata,
                                         InetAddressAndPort beingReplaced,
                                         MovementMap movements,
                                         MovementMap strictMovements)
{
    SystemKeyspace.setBootstrapState(SystemKeyspace.BootstrapState.IN_PROGRESS);
    BootStrapper bootstrapper = new BootStrapper(getBroadcastAddressAndPort(), 
                                                metadata, movements, strictMovements);
    
    return bootstrapper.bootstrap(streamStateStore, 
                                 useStrictConsistency && beingReplaced == null,
                                 beingReplaced);
}
```

**Bootstrap Steps**:
1. **Token Selection**: Choose tokens or use `allocate_tokens_for_local_replication_factor`
2. **Range Calculation**: Determine which ranges to stream
3. **Streaming**: Transfer data from existing replicas
4. **Validation**: Verify received data
5. **Join Ring**: Announce NORMAL status via gossip

**Optimization** (5.0+):
```yaml
stream_throughput_outbound: 24MiB/s
stream_entire_sstables: true  # Zero-copy streaming
```

### Consistent Range Movement

Ensures data consistency during topology changes:

```yaml
# Enabled by default
# cassandra.consistent.rangemovement=true
```

**Impact**:
- Bootstrap/move/decommission operations coordinate with cluster
- Prevents data loss during concurrent range movements
- Slightly slower operations but much safer

## Multi-Datacenter Replication

### Network Topology Strategy

**Intelligent Replica Placement**:
```sql
CREATE KEYSPACE global_app
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'us-east': 3,
    'us-west': 3,
    'eu-central': 2
};
```

**Optimization for Cross-DC**:
From [`cassandra.yaml`](conf/cassandra.yaml:1924):
```yaml
internode_compression: dc  # Compress only inter-DC traffic
inter_dc_tcp_nodelay: false  # Batch cross-DC packets
```

### Write Coordination Across DCs

**LOCAL_QUORUM Writes**:
```
1. Coordinator receives write (us-east)
2. Write to QUORUM in us-east (2/3 nodes)
3. Forward single copy to us-west coordinator
4. Forward single copy to eu-central coordinator
5. Remote coordinators handle replication locally
6. Original coordinator returns success after local QUORUM
```

**Efficiency Benefits**:
- Single cross-DC message per remote DC
- Reduced WAN bandwidth
- Lower latency for writes

### Read Consistency in Multi-DC

**LOCAL_QUORUM Reads**:
- Only query replicas in local DC
- Prevents cross-DC latency
- Acceptable for most applications

**EACH_QUORUM Reads** (5.0+):
- Quorum from every DC
- Guarantees global consistency
- High latency but strongest guarantees

## Transient Replication (Experimental)

Allows configuring replicas that only store data temporarily:

**Configuration**:
```sql
CREATE KEYSPACE test
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'DC1': '3/1'  -- 3 full replicas, 1 transient
};
```

**Use Cases**:
- Reduce storage requirements
- Cheap quorum reads
- Experimental, use with caution

## Cluster Metadata and Topology

### Token Management

**From** [`StorageService.java`](src/java/org/apache/cassandra/service/StorageService.java:2403):
```java
public Collection<Token> getLocalTokens()
{
    Collection<Token> tokens = SystemKeyspace.getSavedTokens();
    assert tokens != null && !tokens.isEmpty();
    return tokens;
}
```

**Token Allocation**:
```yaml
# Automatic balanced allocation
allocate_tokens_for_local_replication_factor: 3

# Manual specification (legacy)
# initial_token: <comma_separated_tokens>
```

### Ring State Transitions

**Node States**:
```
REGISTERED → BOOTSTRAPPING → JOINED (normal operation)
JOINED → MOVING → JOINED (token movement)
JOINED → LEAVING → LEFT (decommission)
```

**Critical Invariant**:
All metadata changes must be visible to all nodes before becoming effective.

## Consistency Mechanisms

### Read Repair

**Blocking Read Repair** (CL > ONE):
```java
// Digest mismatch detected
if (requiresReadRepair(digestResponses))
{
    // Fetch full data from all replicas
    List<FullDataResponse> fullData = fetchFullData(endpoints);
    
    // Reconcile by timestamp (last-write-wins)
    Row resolved = reconcile(fullData);
    
    // Write resolved version back to stale replicas
    writeRepair(resolved, staleReplicas);
}
```

### Replica Filtering Protection

Prevents stale data in secondary index queries:

**From** [`cassandra.yaml`](conf/cassandra.yaml:2024):
```yaml
replica_filtering_protection:
  cached_rows_warn_threshold: 2000
  cached_rows_fail_threshold: 32000
```

**How It Works**:
1. Index query identifies candidate partitions
2. Fetch from multiple replicas (based on CL)
3. Materialize results in memory for comparison
4. Return only rows present in QUORUM of replicas
5. Fail if too many rows need materialization (indicates severe staleness)

## Advanced Distributed Features

### 1. **Snitch Configuration**

Snitches inform Cassandra about network topology:

**Available Snitches**:
- `SimpleSnitch`: Single-DC, no rack awareness
- `GossipingPropertyFileSnitch`: Most common, reads from properties file
- `Ec2Snitch` / `Ec2MultiRegionSnitch`: AWS deployments
- `AzureSnitch`: Microsoft Azure (5.0+)
- `GoogleCloudSnitch`: Google Cloud Platform

**Configuration**:
```yaml
endpoint_snitch: GossipingPropertyFileSnitch
```

**cassandra-rackdc.properties**:
```properties
dc=DC1
rack=RAC1
```

### 2. **Dynamic Endpoint Snitch**

Monitors replica performance and routes to fastest:

```yaml
dynamic_snitch_update_interval: 100ms
dynamic_snitch_reset_interval: 600000ms
dynamic_snitch_badness_threshold: 1.0
```

**Operation**:
- Tracks latency to each replica
- Scores based on recent performance
- Reorders replica list for queries
- Automatically adapts to node performance

### 3. **Speculative Retry**

Send redundant read requests to reduce tail latency:

**Policies**:
```sql
CREATE TABLE latency_sensitive (...)
WITH speculative_retry = '99PERCENTILE';  -- Retry if slower than p99

-- Or fixed threshold
WITH speculative_retry = '10ms';

-- Or always
WITH speculative_retry = 'ALWAYS';
```

**Impact**:
- Reduces p99 latency significantly
- Increases read load slightly
- Critical for user-facing applications

## Failure Scenarios and Recovery

### Scenario 1: Single Node Failure (RF=3)

**Write** (CL=QUORUM):
```
Normal: Write to 3 nodes, wait for 2
Node1 Down: Write to Node2, Node3, hint for Node1
Success if QUORUM (2/3) ack, hint delivered when Node1 recovers
```

**Read** (CL=QUORUM):
```
Query 3 nodes, wait for 2 responses
If digest mismatch, perform read repair
Return repaired value to client
```

### Scenario 2: Datacenter Failure

**Multi-DC Keyspace** (DC1: RF=3, DC2: RF=3):
```
DC1 Fails Completely
Writes: LOCAL_QUORUM to DC2 continues working
Reads: LOCAL_QUORUM from DC2 serves traffic
DC1 Recovery: Repair synchronizes data
```

### Scenario 3: Network Partition

**Split Brain Protection**:
- No automatic resolution
- Writes may succeed in both partitions (CL=ONE, ANY)
- Reconciliation via timestamp when partition heals
- Monitoring critical to detect partitions

**Mitigation**:
- Use QUORUM consistency levels
- Monitor failure detector phi values
- Deploy across multiple availability zones
- Implement application-level conflict resolution

## Best Practices

### 1. **Replication Factor Selection**

**Recommendations**:
- Minimum RF=3 for production
- RF=5 for critical data
- Consider RF per DC independently

**Reasoning**:
- RF=2: Quorum impossible with 1 node down
- RF=3: Tolerates 1 node failure
- RF=5: Tolerates 2 node failures, better for large clusters

### 2. **Consistency Level Guidelines**

**For Most Applications**:
```python
# Balanced approach
reads: LOCAL_QUORUM
writes: LOCAL_QUORUM

# Write-optimized
reads: LOCAL_QUORUM
writes: ONE  # Use hints for durability

# Read-optimized (with LWTs)
reads: ONE
writes: QUORUM  # Ensure durability
```

### 3. **Repair Scheduling**

**Incremental Repair**:
```bash
# Schedule hourly during low-traffic period
0 2 * * * nodetool repair -pr -inc
```

**Full Repair**:
```bash
# Weekly, one node at a time
nodetool repair -pr -seq keyspace
```

**Auto Repair Configuration** (5.1+):
```yaml
auto_repair:
  enabled: true
  global_settings:
    repair_by_keyspace: true
    number_of_repair_threads: 1
    parallel_repair_count: 3
```

### 4. **Monitoring Distributed Health**

**Key Metrics**:
```bash
# Node status
nodetool status

# Ring ownership
nodetool ring keyspace

# Schema agreement
nodetool describecluster

# Repair status
nodetool repair_admin list

# Hint metrics
nodetool hintsstats
```

## Troubleshooting

### Issue: Schema Disagreement

**Pre-5.1 (Gossip-based)**:
```bash
# Check versions
nodetool describecluster

# Force schema reset (caution!)
nodetool resetlocalschema
```

**5.1+ (CMS-based)**:
- Should not occur with CMS
- If it does, indicates serious problem
- Check `nodetool cms status`

### Issue: Streaming Failures

**Diagnosis**:
```bash
nodetool netstats
# Check for failed streams
```

**Solutions**:
- Increase `streaming_keep_alive_period`
- Check network connectivity
- Verify disk space on receiving node
- Review `internode_streaming_tcp_user_timeout`

### Issue: Unavailable Exceptions

**Analysis**:
```
UnavailableException: Cannot achieve consistency level QUORUM
for RF=3, required=2, alive=1
```

**Solutions**:
- Verify nodes are up: `nodetool status`
- Check failure detector: `nodetool info`
- Review `phi_convict_threshold` if false positives
- Consider degraded operation with lower CL

## Conclusion

Cassandra's distributed systems architecture embodies decades of research and hard-won production experience. The gossip protocol provides eventual consistency for cluster membership, while tunable consistency levels allow applications to choose their point on the CAP theorem spectrum.

Key insights:
- **Gossip is eventually consistent**: Account for propagation delays
- **Consistency is tunable**: Choose appropriate CLs for each operation
- **Hints improve availability**: But aren't a substitute for proper replication
- **Repair is mandatory**: For guaranteed eventual consistency
- **CMS changes the game**: CEP-21 resolves long-standing metadata challenges

The upcoming parts of this series will explore query processing, operational features, and data modeling patterns that leverage these distributed systems capabilities effectively.

---

**Next in Series**: Part 4 examines query processing and performance optimization in Cassandra.

**References**:
- [Dynamo Paper](https://www.cs.cornell.edu/courses/cs5414/2017fa/papers/dynamo.pdf)
- [CEP-21](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-21) - Transactional Cluster Metadata
- [CASSANDRA-6230](https://issues.apache.org/jira/browse/CASSANDRA-6230) - Hints Rewrite