# RFC-0005: Tiered Storage Support

- **Status:** Draft
- **Type:** Feature
- **Priority:** P2-High
- **Start Date:** 2024-11-15
- **Author(s):** Cassandra Development Team
- **Ticket:** CASSANDRA-XXXXX
- **Discussion:** [Dev mailing list thread]

## Summary

This RFC proposes adding comprehensive tiered storage support to Apache Cassandra, enabling automatic data lifecycle management across multiple storage tiers (NVMe, SSD, HDD, object storage). This feature will significantly reduce storage costs while maintaining performance for hot data, allowing Cassandra to efficiently handle massive datasets with varying access patterns and retention requirements.

## Motivation

Modern data workloads often exhibit temporal access patterns where recent data is accessed frequently while older data is accessed rarely. Traditional single-tier storage forces organizations to either pay for expensive fast storage for all data or accept poor performance. Tiered storage allows optimal placement of data based on access patterns and business requirements.

### Current State

Current Cassandra storage architecture:
- Single storage tier per node
- All SSTables stored in same location
- No automatic data movement based on age or access patterns
- Manual archival processes required
- Uniform storage costs regardless of data temperature
- Limited integration with cloud object storage

### Problem Statement

Organizations face significant challenges with current storage model:

1. **Cost Inefficiency**: Storing cold data on expensive NVMe/SSD storage
2. **Manual Lifecycle Management**: No automatic data movement between tiers
3. **Capacity Limitations**: Node capacity limited by single storage tier
4. **Cloud Integration**: Poor integration with cloud object storage services
5. **Performance Trade-offs**: All-or-nothing performance characteristics

## Detailed Design

### API Changes

#### Table Configuration

```sql
-- Create table with tiering policy
CREATE TABLE events.user_activity (
    user_id uuid,
    timestamp timestamp,
    activity text,
    metadata map<text, text>,
    PRIMARY KEY (user_id, timestamp)
) WITH storage_policy = {
    'class': 'TieredStoragePolicy',
    'hot_tier_duration': '7d',
    'warm_tier_duration': '30d', 
    'cold_tier_duration': '365d',
    'archive_tier_duration': 'forever',
    'promotion_policy': 'access_frequency',
    'demotion_policy': 'age_based'
};

-- Alter existing table to add tiering
ALTER TABLE events.user_activity 
WITH storage_policy = {
    'class': 'TieredStoragePolicy',
    'tiers': [
        {'name': 'hot', 'duration': '7d', 'min_access_count': 10},
        {'name': 'warm', 'duration': '30d', 'min_access_count': 1},
        {'name': 'cold', 'duration': 'forever', 'min_access_count': 0}
    ]
};

-- Force tier migration
ALTER TABLE events.user_activity 
MIGRATE TIER WHERE timestamp < '2024-01-01' TO 'cold';
```

#### System Configuration

```yaml
# cassandra.yaml
storage_tiers:
  hot:
    class: LocalStorageTier
    path: /mnt/nvme/cassandra/data
    capacity: 1TB
    max_age: 7d
    priority: 1
    
  warm:
    class: LocalStorageTier
    path: /mnt/ssd/cassandra/data
    capacity: 10TB
    max_age: 30d
    priority: 2
    
  cold:
    class: LocalStorageTier
    path: /mnt/hdd/cassandra/data
    capacity: 100TB
    max_age: 365d
    priority: 3
    
  archive:
    class: S3StorageTier
    bucket: cassandra-archive
    region: us-east-1
    prefix: cluster-prod/
    storage_class: GLACIER
    capacity: unlimited
    priority: 4
    
tiering:
  enabled: true
  migration_thread_count: 2
  migration_throughput_mb_per_sec: 100
  access_tracking_enabled: true
  access_tracking_sample_rate: 0.01
  tier_selection_strategy: cost_optimized
```

#### New Virtual Tables

```sql
-- Tier statistics
CREATE VIRTUAL TABLE system_views.storage_tiers (
    tier_name text,
    tier_class text,
    tier_path text,
    capacity_bytes bigint,
    used_bytes bigint,
    available_bytes bigint,
    sstable_count int,
    oldest_sstable timestamp,
    newest_sstable timestamp,
    read_latency_ms double,
    write_latency_ms double,
    PRIMARY KEY (tier_name)
);

-- SSTable tier placement
CREATE VIRTUAL TABLE system_views.sstable_tiers (
    keyspace_name text,
    table_name text,
    sstable_name text,
    tier_name text,
    sstable_size_bytes bigint,
    creation_time timestamp,
    last_access_time timestamp,
    access_count bigint,
    compression_ratio double,
    tier_migration_time timestamp,
    next_tier text,
    estimated_migration_time timestamp,
    PRIMARY KEY ((keyspace_name, table_name), sstable_name)
);

-- Tier migration queue
CREATE VIRTUAL TABLE system_views.tier_migrations (
    migration_id uuid,
    keyspace_name text,
    table_name text,
    sstable_name text,
    source_tier text,
    target_tier text,
    migration_reason text,
    status text, -- 'pending', 'in_progress', 'completed', 'failed'
    progress_percent int,
    started_at timestamp,
    completed_at timestamp,
    error_message text,
    PRIMARY KEY (migration_id)
);

-- Tier access patterns
CREATE VIRTUAL TABLE system_views.tier_access_patterns (
    keyspace_name text,
    table_name text,
    tier_name text,
    time_bucket timestamp,
    read_count bigint,
    write_count bigint,
    avg_read_latency_ms double,
    avg_write_latency_ms double,
    data_retrieved_bytes bigint,
    cache_hit_ratio double,
    PRIMARY KEY ((keyspace_name, table_name), time_bucket, tier_name)
);
```

#### New nodetool Commands

```bash
# View tier status
nodetool tiers status
  Output: Tier configuration, usage statistics

# Manage tier migrations
nodetool tiers migrate --keyspace ks --table tbl --to-tier cold
nodetool tiers migrate-status [--in-progress]
nodetool tiers cancel-migration --migration-id <uuid>

# Configure tiering
nodetool tiers set-policy --keyspace ks --table tbl --policy <policy>
nodetool tiers enable [--keyspace ks] [--table tbl]
nodetool tiers disable [--keyspace ks] [--table tbl]

# Analyze tier usage
nodetool tiers analyze --keyspace ks --table tbl
  Output: Access patterns, cost analysis, recommendations
```

### Implementation Details

#### 1. Storage Tier Abstraction

```java
public abstract class StorageTier {
    protected final String name;
    protected final int priority;
    protected final TierConfiguration config;
    protected final TierMetrics metrics;
    
    public abstract CompletableFuture<SSTableReader> writeSSTable(
        SSTableWriter writer, 
        SSTableMetadata metadata);
    
    public abstract CompletableFuture<SSTableReader> readSSTable(
        Descriptor descriptor);
    
    public abstract CompletableFuture<Void> deleteSSTable(
        Descriptor descriptor);
    
    public abstract boolean isAvailable();
    
    public abstract long getAvailableSpace();
    
    public abstract double getExpectedLatency(OperationType op);
}

public class LocalStorageTier extends StorageTier {
    private final File dataDirectory;
    private final DiskSpaceMonitor spaceMonitor;
    
    @Override
    public CompletableFuture<SSTableReader> writeSSTable(
            SSTableWriter writer, SSTableMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            // Write to local filesystem
            File targetPath = getTargetPath(metadata.descriptor);
            writer.setDirectory(targetPath);
            writer.finish();
            
            // Update metrics
            metrics.recordWrite(metadata.estimatedSize);
            
            return SSTableReader.open(metadata.descriptor);
        });
    }
}

public class S3StorageTier extends StorageTier {
    private final S3Client s3Client;
    private final String bucket;
    private final String prefix;
    private final S3TransferManager transferManager;
    
    @Override
    public CompletableFuture<SSTableReader> writeSSTable(
            SSTableWriter writer, SSTableMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            // First write locally
            File tempFile = writeTempFile(writer);
            
            try {
                // Upload to S3
                String key = getS3Key(metadata.descriptor);
                UploadRequest request = UploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .file(tempFile)
                    .storageClass(config.getStorageClass())
                    .metadata(convertMetadata(metadata))
                    .build();
                
                transferManager.upload(request).join();
                
                // Create proxy reader
                return new S3SSTableReader(s3Client, bucket, key, metadata);
                
            } finally {
                tempFile.delete();
            }
        });
    }
    
    @Override
    public CompletableFuture<SSTableReader> readSSTable(Descriptor descriptor) {
        String key = getS3Key(descriptor);
        
        // Check local cache first
        SSTableReader cached = localCache.get(descriptor);
        if (cached != null) {
            metrics.recordCacheHit();
            return CompletableFuture.completedFuture(cached);
        }
        
        // Download from S3
        return CompletableFuture.supplyAsync(() -> {
            File localFile = downloadToCache(key);
            SSTableReader reader = SSTableReader.open(descriptor, localFile);
            localCache.put(descriptor, reader);
            return reader;
        });
    }
}
```

#### 2. Tier Management Engine

```java
public class TierManagementEngine {
    private final TierRegistry tierRegistry;
    private final MigrationScheduler migrationScheduler;
    private final AccessTracker accessTracker;
    private final CostOptimizer costOptimizer;
    
    public class TierSelector {
        public StorageTier selectTierForSSTable(SSTableMetadata metadata,
                                                TableStoragePolicy policy) {
            // Age-based selection
            long ageMillis = System.currentTimeMillis() - metadata.creationTime;
            
            for (TierRule rule : policy.getTierRules()) {
                if (rule.matches(metadata, ageMillis)) {
                    StorageTier tier = tierRegistry.getTier(rule.getTierName());
                    if (tier.isAvailable() && tier.getAvailableSpace() > metadata.estimatedSize) {
                        return tier;
                    }
                }
            }
            
            // Fallback to default tier
            return tierRegistry.getDefaultTier();
        }
        
        public StorageTier selectTierForRead(Collection<SSTableReader> sstables) {
            // Group SSTables by tier
            Map<StorageTier, List<SSTableReader>> byTier = 
                sstables.stream().collect(Collectors.groupingBy(SSTableReader::getTier));
            
            // Select tier with most data to minimize cross-tier reads
            return byTier.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                    (list1, list2) -> Long.compare(
                        list1.stream().mapToLong(SSTableReader::bytesOnDisk).sum(),
                        list2.stream().mapToLong(SSTableReader::bytesOnDisk).sum())))
                .map(Map.Entry::getKey)
                .orElse(tierRegistry.getDefaultTier());
        }
    }
    
    public class MigrationScheduler {
        private final ThreadPoolExecutor migrationExecutor;
        private final PriorityQueue<MigrationTask> migrationQueue;
        
        public void scheduleMigration(SSTableReader sstable, 
                                     StorageTier targetTier,
                                     MigrationReason reason) {
            MigrationTask task = new MigrationTask(sstable, targetTier, reason);
            
            // Calculate priority based on reason and potential savings
            int priority = calculatePriority(task);
            task.setPriority(priority);
            
            migrationQueue.offer(task);
            triggerMigrationIfNeeded();
        }
        
        private void executeMigration(MigrationTask task) {
            try {
                // Update status
                updateMigrationStatus(task, MigrationStatus.IN_PROGRESS);
                
                SSTableReader source = task.getSSTable();
                StorageTier targetTier = task.getTargetTier();
                
                // Create writer for target tier
                SSTableWriter writer = targetTier.createWriter(source.descriptor);
                
                // Copy data with progress tracking
                try (ISSTableScanner scanner = source.getScanner()) {
                    long totalBytes = source.bytesOnDisk();
                    long processedBytes = 0;
                    
                    while (scanner.hasNext()) {
                        UnfilteredRowIterator partition = scanner.next();
                        writer.append(partition);
                        
                        processedBytes += partition.serializedSize();
                        updateProgress(task, (int)(processedBytes * 100 / totalBytes));
                    }
                }
                
                // Finish writing and get new reader
                SSTableReader newReader = writer.finish();
                
                // Atomic replacement
                replaceSSTable(source, newReader);
                
                // Delete from source tier
                task.getSourceTier().deleteSSTable(source.descriptor);
                
                // Update status
                updateMigrationStatus(task, MigrationStatus.COMPLETED);
                
            } catch (Exception e) {
                logger.error("Migration failed for {}", task, e);
                updateMigrationStatus(task, MigrationStatus.FAILED, e.getMessage());
            }
        }
    }
    
    public class AccessTracker {
        private final Map<SSTableReader, AccessStats> accessStats;
        private final ScheduledExecutorService scheduler;
        
        public void recordAccess(SSTableReader sstable, AccessType type) {
            AccessStats stats = accessStats.computeIfAbsent(sstable, k -> new AccessStats());
            stats.recordAccess(type);
            
            // Check if promotion needed
            if (shouldPromote(stats)) {
                StorageTier currentTier = sstable.getTier();
                StorageTier targetTier = tierRegistry.getHigherTier(currentTier);
                
                if (targetTier != null) {
                    migrationScheduler.scheduleMigration(
                        sstable, 
                        targetTier, 
                        MigrationReason.HIGH_ACCESS_FREQUENCY
                    );
                }
            }
        }
        
        private boolean shouldPromote(AccessStats stats) {
            // Check access frequency threshold
            double accessRate = stats.getRecentAccessRate();
            return accessRate > config.getPromotionThreshold();
        }
        
        @Scheduled(fixedRate = "1h")
        public void analyzeDemotions() {
            for (Map.Entry<SSTableReader, AccessStats> entry : accessStats.entrySet()) {
                SSTableReader sstable = entry.getKey();
                AccessStats stats = entry.getValue();
                
                if (shouldDemote(stats)) {
                    StorageTier currentTier = sstable.getTier();
                    StorageTier targetTier = tierRegistry.getLowerTier(currentTier);
                    
                    if (targetTier != null) {
                        migrationScheduler.scheduleMigration(
                            sstable,
                            targetTier,
                            MigrationReason.LOW_ACCESS_FREQUENCY
                        );
                    }
                }
            }
        }
    }
}
```

#### 3. Cross-Tier Query Execution

```java
public class TieredStorageQueryExecutor {
    private final TierRegistry tierRegistry;
    private final TierCacheManager cacheManager;
    private final QueryOptimizer optimizer;
    
    public RowIterator execute(ReadCommand command) {
        // Get all relevant SSTables across tiers
        Map<StorageTier, List<SSTableReader>> sstablesByTier = 
            getSSTablesByTier(command);
        
        // Optimize query plan based on tier characteristics
        QueryPlan plan = optimizer.createPlan(sstablesByTier, command);
        
        // Execute based on plan
        if (plan.isParallel()) {
            return executeParallel(plan, sstablesByTier);
        } else {
            return executeSequential(plan, sstablesByTier);
        }
    }
    
    private RowIterator executeParallel(QueryPlan plan, 
                                       Map<StorageTier, List<SSTableReader>> sstablesByTier) {
        List<CompletableFuture<RowIterator>> futures = new ArrayList<>();
        
        for (Map.Entry<StorageTier, List<SSTableReader>> entry : sstablesByTier.entrySet()) {
            StorageTier tier = entry.getKey();
            List<SSTableReader> sstables = entry.getValue();
            
            CompletableFuture<RowIterator> future = CompletableFuture.supplyAsync(() -> {
                // Pre-fetch from cold tier if needed
                if (tier.getPriority() > 2) {
                    prefetchToCache(sstables);
                }
                
                return executeTierQuery(sstables, plan.getCommand());
            }, getExecutorForTier(tier));
            
            futures.add(future);
        }
        
        // Merge results from all tiers
        CompletableFuture<List<RowIterator>> all = 
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));
        
        List<RowIterator> iterators = all.join();
        return MergeIterator.create(iterators, plan.getComparator());
    }
    
    private void prefetchToCache(List<SSTableReader> sstables) {
        for (SSTableReader sstable : sstables) {
            if (!cacheManager.contains(sstable)) {
                // Async prefetch to local cache
                cacheManager.prefetch(sstable);
            }
        }
    }
    
    public class TierAwareCompaction {
        public CompactionTask selectCompaction(ColumnFamilyStore cfs) {
            // Group SSTables by tier
            Map<StorageTier, List<SSTableReader>> byTier = 
                cfs.getLiveSSTables().stream()
                    .collect(Collectors.groupingBy(SSTableReader::getTier));
            
            // Prefer compaction within same tier
            for (Map.Entry<StorageTier, List<SSTableReader>> entry : byTier.entrySet()) {
                StorageTier tier = entry.getKey();
                List<SSTableReader> sstables = entry.getValue();
                
                if (sstables.size() >= getMinThreshold(tier)) {
                    CompactionTask task = createIntraTierCompaction(tier, sstables);
                    if (task != null) {
                        return task;
                    }
                }
            }
            
            // Consider cross-tier compaction for small SSTables
            return selectCrossTierCompaction(byTier);
        }
        
        private CompactionTask createIntraTierCompaction(StorageTier tier,
                                                        List<SSTableReader> sstables) {
            // Select SSTables for compaction based on tier characteristics
            List<SSTableReader> selected;
            
            if (tier.getPriority() == 1) { // Hot tier
                // Aggressive compaction for hot data
                selected = selectBySize(sstables, 4, 32);
            } else if (tier.getPriority() == 2) { // Warm tier
                // Moderate compaction
                selected = selectBySize(sstables, 4, 10);
            } else { // Cold tier
                // Conservative compaction
                selected = selectBySize(sstables, 10, 10);
            }
            
            if (selected.isEmpty()) {
                return null;
            }
            
            return new TieredCompactionTask(cfs, selected, tier);
        }
    }
}
```

#### 4. Cost Optimization

```java
public class TierCostOptimizer {
    private final CostModel costModel;
    private final UsageAnalyzer usageAnalyzer;
    private final PredictiveModel predictor;
    
    public OptimizationPlan optimize(ColumnFamilyStore cfs) {
        // Analyze current state
        TierDistribution current = analyzeCurrent(cfs);
        
        // Calculate current cost
        double currentCost = costModel.calculate(current);
        
        // Generate optimization candidates
        List<TierDistribution> candidates = generateCandidates(current);
        
        // Evaluate each candidate
        TierDistribution optimal = null;
        double minCost = currentCost;
        
        for (TierDistribution candidate : candidates) {
            double cost = evaluateCandidate(candidate);
            if (cost < minCost) {
                minCost = cost;
                optimal = candidate;
            }
        }
        
        if (optimal != null) {
            return createMigrationPlan(current, optimal);
        }
        
        return OptimizationPlan.noChange();
    }
    
    private double evaluateCandidate(TierDistribution distribution) {
        double storageCost = 0;
        double accessCost = 0;
        double migrationCost = 0;
        
        for (TierAllocation allocation : distribution.getAllocations()) {
            StorageTier tier = allocation.getTier();
            long dataSize = allocation.getDataSize();
            double accessFrequency = allocation.getEstimatedAccessFrequency();
            
            // Storage cost
            storageCost += tier.getCostPerGB() * (dataSize / 1024.0 / 1024.0 / 1024.0);
            
            // Access cost  
            accessCost += tier.getAccessCostPerGB() * accessFrequency * dataSize;
            
            // Migration cost to reach this distribution
            migrationCost += calculateMigrationCost(allocation);
        }
        
        return storageCost + accessCost + migrationCost;
    }
    
    public class AdaptiveTieringPolicy {
        private final MachineLearningModel model;
        
        public TierPlacement predictOptimalPlacement(SSTableMetadata metadata) {
            // Extract features
            Features features = Features.builder()
                .age(metadata.getAge())
                .size(metadata.estimatedSize)
                .accessHistory(getAccessHistory(metadata))
                .tableCharacteristics(getTableCharacteristics(metadata))
                .build();
            
            // Predict future access pattern
            AccessPattern predicted = model.predict(features);
            
            // Determine optimal tier based on prediction
            return selectTierForPattern(predicted);
        }
        
        public void updateModel(SSTableMetadata metadata, AccessPattern actual) {
            // Online learning - update model with actual access pattern
            model.update(metadata, actual);
        }
    }
}
```

### Performance Considerations

1. **Tier Migration Throttling**: Configurable bandwidth limits for migrations
2. **Read Path Optimization**: Parallel reads from multiple tiers with caching
3. **Write Path Efficiency**: Direct writes to appropriate tier based on policy
4. **Compaction Strategy**: Tier-aware compaction to minimize cross-tier operations
5. **Metadata Caching**: Extensive caching of tier metadata and statistics

### Security Considerations

1. **Encryption**: Support for encryption at rest per tier
2. **Access Control**: Tier-specific access permissions
3. **Cloud Credentials**: Secure credential management for cloud tiers
4. **Audit Logging**: Complete audit trail for tier operations
5. **Data Residency**: Configurable constraints on data placement

## Alternatives Considered

### Alternative 1: External Tiering Solution

**Description**: Use external HSM (Hierarchical Storage Management) solutions.

**Why not chosen**:
- Loss of control over data placement
- Complex integration with Cassandra internals
- Additional operational complexity

### Alternative 2: Manual Tier Management

**Description**: Provide tools for manual data movement between tiers.

**Why not chosen**:
- High operational burden
- Error-prone manual processes
- Inability to react quickly to access pattern changes

### Alternative 3: Simple Age-Based Tiering Only

**Description**: Only support age-based tiering without access tracking.

**Why not chosen**:
- Misses optimization opportunities
- Cannot handle varying access patterns
- Suboptimal cost/performance trade-offs

## Migration Path

### Backward Compatibility

- Tiering is opt-in per table
- Existing tables continue to work unchanged
- No changes to wire protocol

### Migration Steps

1. **Enable Tier Configuration**
   ```yaml
   storage_tiers:
     hot:
       class: LocalStorageTier
       path: /existing/data/path
   ```

2. **Analyze Existing Data**
   ```bash
   nodetool tiers analyze --all-keyspaces
   ```

3. **Configure Policies**
   ```sql
   ALTER TABLE existing_table 
   WITH storage_policy = {...};
   ```

4. **Monitor Migration**
   ```bash
   nodetool tiers migrate-status --watch
   ```

### Rollback Plan

```bash
# Disable tiering
nodetool tiers disable --keyspace ks --table tbl

# Move all data back to primary tier
nodetool tiers migrate --all --to-tier hot
```

## Testing Strategy

### Unit Tests

- Tier selection logic
- Migration scheduling algorithms
- Cost calculation accuracy
- Access pattern tracking
- Policy evaluation

### Integration Tests

- End-to-end tier migrations
- Cross-tier query execution
- Compaction with tiered storage
- Failure recovery scenarios
- Cloud tier operations

### Performance Tests

- Migration throughput
- Query performance across tiers
- Cache effectiveness
- Compaction performance
- Large-scale tier operations

## Timeline and Milestones

| Milestone | Target Date | Description |
|-----------|------------|-------------|
| Design Review | 2025-03-01 | Complete design review |
| Core Implementation | 2025-05-01 | Basic tiering infrastructure |
| Local Tiers | 2025-06-15 | Local filesystem tiers complete |
| Cloud Integration | 2025-08-01 | S3/Azure/GCP support |
| Policy Engine | 2025-09-15 | Advanced placement policies |
| Beta Release | 2025-10-15 | Beta testing begins |
| GA Release | 2025-12-01 | Production ready in 5.3 |

## Dependencies

- Enhanced SSTable metadata
- Improved monitoring framework
- Cloud SDK integrations
- Storage abstraction layer

## Unresolved Questions

- [ ] Should we support custom tier implementations via plugins?
- [ ] What should be the default tier migration thresholds?
- [ ] How to handle tier failures gracefully?
- [ ] Should we support compression differences between tiers?
- [ ] How to coordinate tiering across replicas?

## References

- [Hierarchical Storage Management](https://research.papers/hsm-systems)
- [Cost-Effective Cloud Storage Tiering](https://research.papers/cloud-tiering)
- [Facebook's Blob Storage System](https://research.fb.com/publications/blob-storage)

## Appendix

### A. Configuration Examples

```yaml
# Time-series workload
storage_policy:
  class: TimeSeriesTieringPolicy
  tiers:
    - name: hot
      max_age: 24h
      compression: none
    - name: warm  
      max_age: 7d
      compression: lz4
    - name: cold
      max_age: 30d
      compression: zstd
    - name: archive
      max_age: forever
      compression: zstd_max
      
# Cost-optimized policy
storage_policy:
  class: CostOptimizedPolicy
  target_cost_per_gb: 0.10
  performance_sla:
    p50_latency: 10ms
    p99_latency: 100ms
```

### B. Monitoring Dashboard

```
Storage Tier Distribution - events.user_activity
================================================

Tier Usage:
  Hot (NVMe):    [████░░░░░░]  420 GB / 1 TB (42%)
  Warm (SSD):    [███████░░░]  7.2 TB / 10 TB (72%)
  Cold (HDD):    [██░░░░░░░░]  18 TB / 100 TB (18%)
  Archive (S3):  [████████░░]  892 TB / ∞ (n/a)

Access Patterns (last 24h):
  Hot tier:  98% of reads, 100% of writes
  Warm tier: 2% of reads, 0% of writes
  Cold tier: <1% of reads, 0% of writes

Cost Analysis:
  Current Monthly Cost: $3,847
  Optimized Cost: $2,912 (-24%)
  Recommendations: 
    - Migrate 2.3TB from warm to cold (age > 30d)
    - Promote 120GB from cold to warm (high access)

Migration Queue:
  In Progress: 3 SSTables (18 GB)
  Pending: 47 SSTables (312 GB)
  Estimated Completion: 4h 23m