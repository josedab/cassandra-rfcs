# Tiered Storage Implementation

This package contains the implementation of RFC-0005: Tiered Storage Support for Apache Cassandra.

## Overview

The tiered storage feature enables automatic data lifecycle management across multiple storage tiers (NVMe, SSD, HDD, object storage), significantly reducing storage costs while maintaining performance for hot data.

## Architecture

### Core Components

#### 1. Storage Tier Abstraction (`StorageTier.java`)
- Abstract base class for all storage tiers
- Defines common operations: write, read, delete
- Tracks metrics and cost information

**Implementations:**
- `LocalStorageTier`: Local filesystem-based storage (NVMe, SSD, HDD)
- `S3StorageTier`: Amazon S3 or S3-compatible object storage

#### 2. Tier Management Engine (`TierManagementEngine.java`)
- Central coordinator for all tiered storage operations
- Singleton pattern for global access
- Manages tier selection and migration orchestration

#### 3. Tier Registry (`TierRegistry.java`)
- Registry for all configured storage tiers
- Provides tier lookup and hierarchy navigation
- Manages default tier configuration

#### 4. Migration Scheduler (`MigrationScheduler.java`)
- Schedules and executes SSTable migrations between tiers
- Priority-based queue for migration tasks
- Throttling support to control migration bandwidth

#### 5. Access Tracker (`AccessTracker.java`)
- Tracks SSTable access patterns
- Supports promotion/demotion decisions based on access frequency
- Configurable sampling rate to reduce overhead

#### 6. Cost Optimizer (`TierCostOptimizer.java`)
- Calculates cost of current tier distribution
- Generates cost optimization recommendations
- Considers storage cost, access cost, and latency

### Supporting Classes

- `TierConfiguration`: Configuration for individual tiers
- `TierMetrics`: Metrics collection for tier operations
- `MigrationTask`: Represents a migration between tiers
- `AccessStats`: Access pattern statistics for SSTables
- `TableStoragePolicy`: Policy defining tier placement rules

## Usage

### Registering Tiers

```java
TierRegistry registry = TierRegistry.instance();

// Register hot tier (NVMe)
TierConfiguration hotConfig = TierConfiguration.builder()
    .tierClass("LocalStorageTier")
    .capacity(1024L * 1024 * 1024 * 1024) // 1TB
    .maxAge(7, TimeUnit.DAYS)
    .costPerGB(0.50)
    .build();
LocalStorageTier hotTier = new LocalStorageTier("hot", 1, hotConfig,
    new File("/mnt/nvme/cassandra"));
registry.registerTier(hotTier);

// Register warm tier (SSD)
TierConfiguration warmConfig = TierConfiguration.builder()
    .tierClass("LocalStorageTier")
    .capacity(10L * 1024 * 1024 * 1024 * 1024) // 10TB
    .maxAge(30, TimeUnit.DAYS)
    .costPerGB(0.20)
    .build();
LocalStorageTier warmTier = new LocalStorageTier("warm", 2, warmConfig,
    new File("/mnt/ssd/cassandra"));
registry.registerTier(warmTier);

// Register cold tier (S3)
TierConfiguration coldConfig = TierConfiguration.builder()
    .tierClass("S3StorageTier")
    .maxAge(365, TimeUnit.DAYS)
    .costPerGB(0.05)
    .accessCostPerGB(0.01)
    .additionalParams(Map.of(
        "bucket", "cassandra-archive",
        "region", "us-east-1",
        "storage_class", "GLACIER"
    ))
    .build();
S3StorageTier coldTier = new S3StorageTier("cold", 3, coldConfig);
registry.registerTier(coldTier);
```

### Enabling Tiered Storage

```java
TierManagementEngine engine = TierManagementEngine.instance();
engine.enable();
```

### Scheduling Migrations

```java
MigrationScheduler scheduler = engine.getMigrationScheduler();

// Manual migration
UUID migrationId = scheduler.scheduleMigration(
    sstable,
    sourceTier,
    targetTier,
    MigrationReason.MANUAL
);

// Check migration status
MigrationStatus status = scheduler.getStatus(migrationId);
```

### Access Tracking

```java
AccessTracker tracker = engine.getAccessTracker();

// Record access (automatically triggers promotion/demotion)
tracker.recordAccess(sstable, AccessType.READ);

// Get statistics
AccessStats stats = tracker.getStats(sstable);
double accessRate = stats.getRecentAccessRate();
```

### Cost Optimization

```java
TierCostOptimizer optimizer = new TierCostOptimizer(registry, tracker);

// Calculate current cost
double currentCost = optimizer.calculateCurrentCost(sstables);

// Get recommendations
List<OptimizationRecommendation> recommendations =
    optimizer.generateRecommendations(sstables);

for (OptimizationRecommendation rec : recommendations) {
    System.out.println(rec); // Shows potential savings
}
```

## Configuration

### cassandra.yaml

```yaml
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
    class: S3StorageTier
    bucket: cassandra-archive
    region: us-east-1
    prefix: cluster-prod/
    storage_class: GLACIER
    priority: 3

tiering:
  enabled: true
  migration_thread_count: 2
  migration_throughput_mb_per_sec: 100
  access_tracking_enabled: true
  access_tracking_sample_rate: 0.01
```

### Table Storage Policy

```sql
CREATE TABLE events.user_activity (
    user_id uuid,
    timestamp timestamp,
    activity text,
    PRIMARY KEY (user_id, timestamp)
) WITH storage_policy = {
    'class': 'TieredStoragePolicy',
    'hot_tier_duration': '7d',
    'warm_tier_duration': '30d',
    'cold_tier_duration': '365d'
};
```

## Key Features

1. **Automatic Tier Migration**: SSTables automatically move between tiers based on age and access patterns
2. **Access-Based Promotion**: Frequently accessed cold data is promoted to faster tiers
3. **Cost Optimization**: Analyzes current distribution and recommends cost-saving migrations
4. **Flexible Configuration**: Per-table policies and global tier configuration
5. **Cloud Integration**: Native support for S3-compatible object storage
6. **Throttling**: Configurable bandwidth limits for migrations to minimize impact

## Performance Considerations

- **Access Tracking Sampling**: Default 1% sample rate to minimize overhead
- **Migration Throttling**: Configurable bandwidth limits
- **Tier-Aware Compaction**: Prefers intra-tier compaction over cross-tier
- **Local Caching**: S3 tier includes local cache for frequently accessed data

## Future Enhancements

- Virtual tables for tier monitoring
- Nodetool commands for tier management
- CQL ALTER TABLE MIGRATE TIER command
- Advanced ML-based placement policies
- Azure Blob Storage and Google Cloud Storage support
- Compression strategy per tier

## Implementation Status

This is the initial implementation of RFC-0005. Core components are functional, with the following pending:
- Integration with SSTable lifecycle
- Virtual tables for monitoring
- Nodetool commands
- CQL syntax support
- Full cloud provider integrations (currently stubs)
