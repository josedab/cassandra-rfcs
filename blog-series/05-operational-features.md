# Apache Cassandra: Advanced Operational Features and Cluster Management

## Introduction

Operating Apache Cassandra at scale requires deep understanding of its operational tooling, repair mechanisms, monitoring capabilities, and maintenance procedures. This post explores advanced operational features that enable reliable, high-performance Cassandra deployments in production environments.

## Auto Repair Scheduler (CEP-37, 5.1+)

One of the most significant operational improvements in Cassandra 5.1 is the built-in Auto Repair scheduler.

### Architecture

**From [`NEWS.txt`](NEWS.txt:84)**:
> CEP-37 Auto Repair is a fully automated scheduler that provides repair orchestration within Apache Cassandra. This significantly reduces operational overhead by eliminating the need for operators to deploy external tools.

**Configuration** in [`cassandra.yaml`](conf/cassandra.yaml:2722):
```yaml
auto_repair:
  enabled: true
  repair_type_overrides:
    full:
      enabled: true
      min_repair_interval: 24h
      token_range_splitter:
        parameters:
          bytes_per_assignment: 50GiB
          max_bytes_per_schedule: 100000GiB
    incremental:
      enabled: false  # See migration guide
      min_repair_interval: 24h
      token_range_splitter:
        parameters:
          bytes_per_assignment: 50GiB
          max_bytes_per_schedule: 100GiB
```

### Repair Scheduling Strategy

**Token Range Splitting**:
- Intelligently splits ranges based on data size
- Configurable bytes per repair assignment
- Prevents overwhelming single repair sessions
- Tracks progress across cluster restarts

**Parallel Repair Control**:
```yaml
global_settings:
  parallel_repair_count: 3  # Max nodes repairing simultaneously
  parallel_repair_percentage: 3  # Or percentage of cluster
  allow_parallel_replica_repair: false  # Avoid replica conflicts
```

## Repair Mechanisms Deep Dive

### Repair Types

#### Full Repair
```bash
nodetool repair -pr  # Primary range only
```

**Characteristics**:
- Validates all data against replicas
- Marks data as repaired (5.0+)
- Resource intensive
- Recommended schedule: Weekly

#### Incremental Repair
```bash
nodetool repair -pr -inc
```

**Benefits**:
- Only repairs unrepaired data
- Faster than full repair
- Lower I/O overhead
- Anticompaction segregates repaired data

**Migration to Incremental**:
1. Run full repair on all nodes first
2. Enable incremental repair
3. Monitor anticompaction overhead
4. Adjust `bytes_per_assignment` if needed

#### Subrange Repair
```bash
nodetool repair -st <start_token> -et <end_token> keyspace
```

**Use Cases**:
- Repair specific data ranges
- Parallel repair across token ranges
- Recovery after known inconsistency

### Paxos Repair (5.0+)

Ensures Paxos state consistency:

```yaml
paxos_variant: v2
paxos_state_purging: repaired  # After paxos repair
```

**Benefits**:
- Cleans up incomplete Paxos rounds
- Required with Paxos V2
- Integrated with regular repair
- Reduces storage overhead

## Hints Management

### Monitoring Hints

```bash
# View hint statistics
nodetool hintsstats

# List pending hints per endpoint
nodetool listpendinghints
```

**Virtual Table** (4.1+):
```sql
SELECT * FROM system_views.pending_hints;
```

### Hint Delivery Control

```bash
# Pause hint delivery
nodetool pausehandoff

# Resume hint delivery
nodetool resumehandoff

# Truncate hints for specific endpoint
nodetool truncatehints <endpoint>
```

### Advanced Configuration

```yaml
# Limit hint storage per destination
max_hints_size_per_host: 10GiB

# Automatic cleanup of orphaned hints
auto_hints_cleanup_enabled: false

# Transfer hints during decommission
transfer_hints_on_decommission: true
```

## Snapshot Management

### Creating Snapshots

**From [`StorageService.java`](src/java/org/apache/cassandra/service/StorageService.java:2877)**:
```bash
# Snapshot entire keyspace
nodetool snapshot --tag backup-2024-01-15 keyspace_name

# Snapshot specific tables
nodetool snapshot --tag backup-2024-01-15 -kt keyspace.table1,keyspace.table2

# Skip memtable flush
nodetool snapshot --tag quick-snapshot --skip-flush keyspace
```

### Snapshot with TTL (4.1+)

```yaml
# Auto-expire snapshots
auto_snapshot_ttl: 30d
```

```bash
# Create snapshot with TTL
nodetool snapshot --ttl 7d --tag weekly-backup keyspace
```

### Snapshot Cleanup

```bash
# Clear specific snapshot
nodetool clearsnapshot --tag backup-2024-01-15

# Clear old snapshots
nodetool clearsnapshot --older-than 7d

# Clear snapshots by timestamp
nodetool clearsnapshot --older-than-timestamp 2024-01-01T00:00:00Z
```

### Rate Limiting (4.0+)

Prevents snapshot operations from overwhelming I/O:
```yaml
snapshot_links_per_second: 1000  # 0 = unlimited
```

## Backup and Recovery

### Incremental Backups

```yaml
incremental_backups: false
```

**Per-Table Override** (5.0+):
```sql
ALTER TABLE keyspace.table
WITH incremental_backups = true;
```

**Backup Files Location**:
```
/var/lib/cassandra/data/keyspace/table-uuid/backups/
```

### Point-in-Time Recovery

**Commit Log Archiving**:
```properties
# conf/commitlog_archiving.properties
archive_command=/path/to/backup-script.sh %path
restore_command=/path/to/restore-script.sh %from %to
restore_point_in_time=2024:01:15:14:30:00
```

**Recovery Process**:
1. Stop Cassandra
2. Clear data directories
3. Restore snapshot
4. Restore commit log archives
5. Start Cassandra (replays commit logs)

## Cluster Maintenance Operations

### Node Addition

**From [`CHANGES.txt`](CHANGES.txt:62)**:
> Automated Repair Inside Cassandra [CEP-37] (CASSANDRA-19918)

```bash
# 1. Install Cassandra on new node
# 2. Configure cassandra.yaml (especially seeds)
# 3. Start node
# It will automatically bootstrap

# Monitor progress
nodetool bootstrap resume  # If interrupted
nodetool netstats  # Stream progress
```

### Node Removal (Decommission)

```bash
# Graceful removal
nodetool decommission

# Monitor status
nodetool netstats

# Abort if needed
nodetool abortdecommission <node_id>
```

**Process**:
1. Node streams data to remaining replicas
2. Updates cluster metadata
3. Stops accepting requests
4. Shuts down cleanly

### Node Replacement

```bash
# On new node with same IP as failed node
# In cassandra-env.sh or startup
-Dcassandra.replace_address=<failed_node_ip>

# Then start Cassandra
./bin/cassandra
```

### Token Movement

```bash
# Move node to specific token
nodetool move <new_token>

# Resume interrupted move
nodetool resumemove

# Abort move
nodetool abortmove <node_id>
```

## JMX Management and Monitoring

### Essential MBeans

**Storage Service**:
```
org.apache.cassandra.db:type=StorageService
  - getTokens(): Node's tokens
  - getOperationMode(): Current state
  - forceKeyspaceFlush(keyspace): Flush to disk
  - rebuild(datacenter): Rebuild from specific DC
```

**Compaction Manager**:
```
org.apache.cassandra.db:type=CompactionManager
  - getCompactions(): Active compactions
  - stopCompaction(type): Cancel compactions
  - setPendingTasks(): Estimated remaining
```

**Commit Log**:
```
org.apache.cassandra.db:type=Commitlog
  - getCompletedTasks(): Writes completed
  - getPendingTasks(): Writes queued
  - getTotalCommitlogSize(): Disk usage
```

### nodetool Essential Commands

**Cluster Health**:
```bash
nodetool status           # Node states and load
nodetool ring             # Token ownership
nodetool info             # Local node details
nodetool describecluster  # Cluster overview
```

**Performance Monitoring**:
```bash
nodetool tpstats          # Thread pool statistics
nodetool compactionstats  # Active compactions
nodetool proxyhistograms  # Latency distributions
nodetool tablehistograms  # Per-table latencies
nodetool tablestats       # Detailed table metrics
```

**Maintenance**:
```bash
nodetool cleanup      # Remove out-of-range data
nodetool scrub        # Fix corrupted SSTables
nodetool upgradesstables  # Rewrite to current format
nodetool compact      # Force major compaction
```

## Streaming and Bandwidth Management

### Throttling Configuration

```yaml
# SSTable streaming
stream_throughput_outbound: 24MiB/s
inter_dc_stream_throughput_outbound: 24MiB/s

# Entire SSTable transfers (zero-copy)
entire_sstable_stream_throughput_outbound: 24MiB/s
entire_sstable_inter_dc_stream_throughput_outbound: 24MiB/s
```

**Runtime Adjustment**:
```bash
nodetool setstreamthroughput 100  # MiB/s
nodetool setinterdcstreamthroughput 50
```

### Stream Session Tracking

**Virtual Table** (4.1+):
```sql
SELECT * FROM system_views.streaming;
```

**Metrics**:
- Active streams
- Bytes sent/received
- Files pending
- Session duration

## Authentication and Authorization

### Built-in Auth (Recommended)

**Setup**:
```sql
-- Enable authentication
ALTER KEYSPACE system_auth 
WITH REPLICATION = {'class': 'NetworkTopologyStrategy', 
                    'DC1': 3, 'DC2': 3};

-- Create admin user
CREATE ROLE admin WITH PASSWORD = 'secure_password' 
  AND LOGIN = true 
  AND SUPERUSER = true;

-- Create application users
CREATE ROLE app_writer WITH PASSWORD = 'xxx' AND LOGIN = true;
GRANT MODIFY ON KEYSPACE app_data TO app_writer;
```

**Configuration**:
```yaml
authenticator:
  class_name: PasswordAuthenticator
  
authorizer:
  class_name: CassandraAuthorizer
  
role_manager:
  class_name: CassandraRoleManager
```

### Mutual TLS Authentication (4.1+)

```yaml
authenticator:
  class_name: org.apache.cassandra.auth.MutualTlsAuthenticator
  parameters:
    validator_class_name: org.apache.cassandra.auth.SpiffeCertificateValidator
```

### CIDR Authorization (5.0+)

Restrict access by IP address:

```yaml
cidr_authorizer:
  class_name: CassandraCIDRAuthorizer
  parameters:
    cidr_authorizer_mode: ENFORCE  # or MONITOR
```

```sql
-- Grant access from specific CIDR
CREATE ROLE external_api WITH PASSWORD = 'xxx' AND LOGIN = true;
GRANT SELECT ON KEYSPACE public_data TO external_api;
-- Configure CIDR groups in system_auth.cidr_groups table
```

## Audit Logging

**Configuration** [`cassandra.yaml`](conf/cassandra.yaml:2098):
```yaml
audit_logging_options:
  enabled: true
  logger:
    - class_name: BinAuditLogger  # Or FileAuditLogger
  included_keyspaces: app_data,user_data
  excluded_keyspaces: system,system_schema
  included_categories: DDL,DML,DCL,AUTH
  roll_cycle: HOURLY
  max_log_size: 16GiB
```

**Enable at Runtime**:
```bash
nodetool enableauditlog \
  --logger BinAuditLogger \
  --included-keyspaces app_data
```

**View Logs**:
```bash
# Using auditlogviewer tool
bin/auditlogviewer /var/log/cassandra/audit
```

## Full Query Logger (4.0+)

Capture all CQL activity for analysis:

```bash
# Enable FQL
nodetool enablefullquerylog \
  --path /var/log/cassandra/fql \
  --roll-cycle HOURLY \
  --max-log-size 16GiB

# Disable
nodetool stopfullquerylog

# Reset logs
nodetool resetfullquerylog
```

**Analyzing Logs**:
```bash
# Dump queries
bin/fqltool dump /var/log/cassandra/fql

# Replay against another cluster
bin/fqltool replay \
  --target 127.0.0.1 \
  --store /var/log/cassandra/fql

# Compare performance
bin/fqltool compare \
  --baseline cluster1.log \
  --target cluster2.log
```

## Virtual Tables (4.0+)

Query cluster state via CQL:

### System Views

**Client Connections**:
```sql
SELECT address, port, username, protocol_version, request_count
FROM system_views.clients;
```

**Thread Pools**:
```sql
SELECT name, active_tasks, pending_tasks, completed_tasks
FROM system_views.thread_pools;
```

**Compaction Activity**:
```sql
SELECT keyspace_name, table_name, task_type, 
       completed, total, unit, progress
FROM system_views.sstable_tasks;
```

**Slow Queries** (5.0+):
```sql
-- Requires enabling specific logback appender
SELECT * FROM system_views.slow_queries
ORDER BY start_time DESC
LIMIT 100;
```

## Compaction Management

### Compaction Tuning

**Thread Pool Sizing**:
```bash
# Runtime adjustment
nodetool setconcurrentcompactors 8
nodetool setconcurrentvalidators 4
```

**Throttling**:
```bash
# Set throughput limit
nodetool setcompactionthroughput 128  # MiB/s

# Disable throttling
nodetool setcompactionthroughput 0
```

### Compaction Strategies Per Table

```bash
# View current strategy
nodetool getcompactionstrategy keyspace table

# Change strategy
nodetool setcompactionstrategy keyspace table \
  UnifiedCompactionStrategy \
  scaling_parameters=T4 \
  target_sstable_size=1GiB
```

### Manual Compaction Operations

```bash
# Major compaction (use sparingly)
nodetool compact keyspace table

# Garbage collect (5.0+)
nodetool garbagecollect -g CELL keyspace table

# User-defined compaction
nodetool compact --user-defined keyspace table \
  /var/lib/cassandra/data/keyspace/table-uuid/ma-1-big-Data.db \
  /var/lib/cassandra/data/keyspace/table-uuid/ma-2-big-Data.db
```

## SSTable Operations

### Verification

```bash
# Verify SSTable integrity
nodetool verify keyspace table

# Extended verification
nodetool verify -e keyspace table

# Verify token ownership
nodetool verify --check-owns-tokens keyspace table
```

### Scrubbing

```bash
# Fix corrupted SSTables
nodetool scrub keyspace table

# Skip corrupted rows
nodetool scrub --skip-corrupted keyspace table

# Validate during scrub
nodetool scrub --validate keyspace table
```

### Upgrade SSTables

```bash
# Upgrade to current format
nodetool upgradesstables keyspace table

# Skip current version
nodetool upgradesstables --skip-current keyspace table
```

### SSTable Utilities

**sstablemetadata**:
```bash
# View SSTable metadata
./tools/bin/sstablemetadata \
  /var/lib/cassandra/data/keyspace/table-uuid/ma-1-big-Data.db
```

**sstable2json / sstabledump** (5.0+):
```bash
# Export SSTable to JSON
./tools/bin/sstabledump \
  /var/lib/cassandra/data/keyspace/table-uuid/ma-1-big-Data.db
```

**sstablesplit**:
```bash
# Split large SSTable
./tools/bin/sstablesplit \
  --size 100 \
  /var/lib/cassandra/data/keyspace/table-uuid/ma-1-big-Data.db
```

## Performance Sampling

### Partition Sampling

```bash
# Sample top partitions by size
nodetool samplepartitions keyspace.table \
  --duration 60 \
  --capacity 256 \
  --count 10 \
  --sampler WRITES

# Continuous sampling
nodetool startsamplingpartitions keyspace table \
  --duration 300 \
  --interval 3600 \
  --capacity 256 \
  --count 10 \
  --samplers READS,WRITES
```

**Top Partition Tracking** (4.1+):
- Automatically tracked during repair
- Accessible via `nodetool tablestats`
- Identifies hot spots and large partitions

### Query Sampling

```bash
# Enable probabilistic tracing
nodetool settraceprobability 0.01  # 1% of queries

# View traces
SELECT * FROM system_traces.sessions 
WHERE started_at > '2024-01-15' 
LIMIT 100;
```

## Guardrails (4.1+)

Prevent operational issues through limits:

### Configuration

**From [`cassandra.yaml`](conf/cassandra.yaml:2240)**:
```yaml
# Schema guardrails
keyspaces_warn_threshold: 40
keyspaces_fail_threshold: 50
tables_warn_threshold: 150
tables_fail_threshold: 200
columns_per_table_warn_threshold: 50
columns_per_table_fail_threshold: 100

# Query guardrails
partition_keys_in_select_warn_threshold: 20
partition_keys_in_select_fail_threshold: 100
page_size_warn_threshold: 100000
page_size_fail_threshold: 1000000

# Data guardrails
partition_size_warn_threshold: 100MiB
partition_size_fail_threshold: 1GiB
column_value_size_warn_threshold: 10MiB
column_value_size_fail_threshold: 100MiB
```

### Runtime Modification (JMX)

```bash
# View current guardrails
nodetool getguardrailsconfig

# Modify guardrail
nodetool setguardrailsconfig \
  partition_size_warn_threshold 200MiB
```

## Diagnostic Events (4.0+)

Detailed operational insights:

```yaml
diagnostic_events_enabled: true
```

**Event Categories**:
- Compaction events
- Flush events
- Hint events
- Repair events
- Bootstrap events
- Schema change events

**Consumption**:
- Via JMX notifications
- Custom subscribers
- Log aggregation

## Cluster Metadata Service (CMS) Management (5.1+)

### CMS Initialization

```bash
# Initialize CMS (once per cluster)
nodetool cms initialize

# Add CMS members
nodetool cms reconfigure DC1:3,DC2:3

# View CMS status
nodetool cms status

# Dump metadata log
nodetool cms dump
```

### CMS Reconfiguration

```bash
# Change CMS membership
nodetool cms reconfigure DC1:5,DC2:3

# Cancel ongoing reconfiguration
nodetool cms reconfigure --cancel

# Work around DOWN nodes
nodetool cms reconfigure --ignore-down-nodes
```

## Troubleshooting Tools

### Node Diagnostics

```bash
# Comprehensive node info
nodetool info

# GC statistics
nodetool gcstats

# Failure detector
nodetool statusgossip

# Network connectivity
nodetool ping <endpoint>

# Endpoint state
nodetool gossipinfo
```

### Heap Dump on OOM

**Configuration** [`cassandra.yaml`](conf/cassandra.yaml:136):
```yaml
dump_heap_on_uncaught_exception: true
heap_dump_path: /var/lib/cassandra/heapdump
```

**Limit Dumps**:
- Only one dump per startup (prevents disk exhaustion)
- Automatic cleanup of old dumps

### Thread Dump on Timeout

```bash
# Automatic thread dump when tests timeout
# Useful for debugging deadlocks
```

## Best Practices

### 1. **Capacity Planning**

**Disk Space**:
```
Required = (Data Size) × (1 + Compaction Overhead) × (1 + Snapshot Overhead)

Where:
- STCS overhead: 50%
- LCS overhead: 10%
- UCS overhead: 20-30%
- Snapshot overhead: Varies (plan for 20%)
```

**Memory**:
```
Heap = max(
  min(1/2 RAM, 8GB),  # for <= 16GB RAM
  min(1/4 RAM, 32GB)  # for > 16GB RAM
)

Off-Heap = RAM - Heap - OS_Reserved
```

### 2. **Repair Schedule**

**Recommendations**:
```bash
# Auto repair (5.1+) - preferred
auto_repair.enabled = true

# Or manual repair
# Full repair weekly per node
0 2 * * 0 nodetool repair -pr keyspace

# Incremental repair daily
0 2 * * * nodetool repair -pr -inc keyspace
```

**Important**: Complete cluster repair within `gc_grace_seconds` (default 10 days)

### 3. **Backup Strategy**

**Snapshot Schedule**:
```bash
# Daily snapshots with 7-day retention
nodetool snapshot --ttl 7d --tag daily-$(date +\%Y\%m\%d)

# Weekly snapshots with 30-day retention
nodetool snapshot --ttl 30d --tag weekly-$(date +\%Y\%m\%d)
```

**Incremental Backups**:
- Enable only if you have automated cleanup
- Significantly increases disk usage
- Provides granular recovery points

### 4. **Monitoring Checklist**

**Critical Metrics**:
- [ ] Node status (UP/DOWN/LEAVING)
- [ ] Pending compactions (should be low)
- [ ] Read/write latency (p99 < timeout)
- [ ] Disk space (> 50% free for STCS)
- [ ] GC pause time (< 1 second)
- [ ] Dropped messages (should be zero)
- [ ] Hints pending (should be zero normally)
- [ ] Tombstone warnings (investigate if frequent)

### 5. **Capacity Management**

**Disk Usage Guardrails** (4.1+):
```yaml
data_disk_usage_percentage_warn_threshold: 70
data_disk_usage_percentage_fail_threshold: 85
```

**Automatic Response**:
- Warns clients when threshold exceeded
- Fails writes at fail threshold
- Protects cluster from disk exhaustion

## Migration and Upgrades

### Rolling Upgrade Process

**From [`NEWS.txt`](NEWS.txt:139)**:
```
1. Snapshot all nodes
2. Upgrade and restart one node
3. Wait for node to complete startup
4. Verify node health (nodetool status)
5. Repeat for each node
6. Run upgradesstables on all nodes
```

**For 5.1 (with CMS)**:
```
1. Rolling upgrade to 5.1 (all nodes)
2. Initialize CMS: nodetool cms initialize
3. Reconfigure CMS members: nodetool cms reconfigure
4. Resume normal operations
```

### Downgrade Considerations

**General Rule**: Can downgrade within minor versions, not across majors

**5.1 → 5.0**: Possible before CMS initialization
**5.0 → 4.1**: Not supported (BTI format, schema changes)
**4.1 → 4.0**: Possible with caveats (check NEWS.txt)

## Emergency Procedures

### Node Recovery

**Scenario: Node won't start**
```bash
# 1. Check logs
tail -f /var/log/cassandra/system.log

# 2. Verify disk space
df -h

# 3. Check commit log for corruption
# May need to delete corrupt segments

# 4. Try offline scrub
./tools/bin/sstablescrub keyspace table
```

### Cluster Recovery

**Scenario: Multiple node failures (> quorum)**
```bash
# 1. Recover individual nodes
# 2. Run repair on all recovered nodes
nodetool repair -pr

# 3. Verify data consistency
nodetool repair -pr -vd  # Preview mode
```

### Handling Stuck Compactions

```bash
# View active compactions
nodetool compactionstats

# Stop specific compaction
nodetool stop COMPACTION

# Or by ID
nodetool stop -id <compaction-id>
```

## Conclusion

Operational excellence with Cassandra requires understanding its sophisticated management capabilities. From the new Auto Repair scheduler to comprehensive monitoring via virtual tables, modern Cassandra provides production-grade operational tooling.

Key operational principles:
- **Automate repairs**: Use Auto Repair or schedule carefully
- **Monitor continuously**: Watch key metrics and trends
- **Plan capacity**: Headroom for compaction and failures
- **Use guardrails**: Prevent operational issues proactively
- **Test procedures**: Practice recovery before emergencies
- **Keep current**: Upgrade to benefit from operational improvements

The evolution from manual operational procedures to automated systems like Auto Repair and CMS demonstrates Cassandra's maturation as an enterprise-grade platform.

---

**Next in Series**: Part 6 covers schema design patterns and best practices for Cassandra data modeling.

**References**:
- [CEP-37](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-37) - Auto Repair
- [Operational Documentation](https://cassandra.apache.org/doc/latest/cassandra/managing/)
- [nodetool Reference](https://cassandra.apache.org/doc/latest/cassandra/tools/nodetool/)