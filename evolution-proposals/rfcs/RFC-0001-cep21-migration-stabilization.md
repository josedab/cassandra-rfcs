# RFC-0001: Complete CEP-21 Migration and Stabilization

- **Status:** Draft
- **Type:** Enhancement
- **Priority:** P1-Critical
- **Start Date:** 2024-11-15
- **Author(s):** Cassandra Development Team
- **Ticket:** CASSANDRA-XXXXX
- **Discussion:** [Dev mailing list thread]

## Summary

This RFC proposes comprehensive enhancements to the CEP-21 (Transactional Cluster Metadata) migration process, including automated validation tools, enhanced monitoring, improved rollback support, and better observability. These improvements are critical for production adoption of Cassandra 5.1 and will significantly reduce operational risk during CMS migration.

## Motivation

CEP-21 represents the most significant architectural change in Cassandra's history, introducing transactional cluster metadata management through a dedicated Cluster Metadata Service (CMS). While the core implementation is complete in Cassandra 5.1, the migration process presents operational challenges that must be addressed for widespread production adoption.

### Current State

The current CEP-21 implementation in Cassandra 5.1 includes:
- Complete CMS implementation with transactional guarantees
- Manual migration process via `nodetool cms initialize`
- Basic migration commands without comprehensive validation
- Limited troubleshooting capabilities for migration issues
- Minimal documentation for rollback scenarios
- No automated pre-flight checks or migration planning

### Problem Statement

Production operators face several challenges when migrating to CMS:

1. **Migration Risk**: No automated validation before starting migration, leading to potential failures mid-process
2. **Lack of Visibility**: Limited insight into migration progress and decision-making
3. **Rollback Complexity**: Unclear procedures for safely rolling back a failed or problematic migration
4. **Operational Burden**: Manual steps required with insufficient guidance on CMS membership selection
5. **Troubleshooting Difficulties**: Inadequate tooling for diagnosing and resolving migration issues

## Detailed Design

### API Changes

#### New nodetool Commands

```bash
# Pre-migration validation
nodetool cms validate [--verbose]
  Options:
    --verbose: Show detailed validation results
  Output: Validation report with pass/fail status and recommendations

# Migration planning
nodetool cms migrate --plan [--recommend-members]
  Options:
    --plan: Generate migration plan without executing
    --recommend-members: Suggest optimal CMS membership based on topology
  Output: Step-by-step migration plan with timing estimates

# Migration status monitoring
nodetool cms status
  Output: Current migration phase, progress percentage, active operations

# Migration history
nodetool cms history [--last <duration>]
  Options:
    --last: Show history for specified duration (e.g., "7d", "24h")
  Output: Timestamped migration events and state changes

# Rollback support
nodetool cms rollback [--checkpoint <id>]
  Options:
    --checkpoint: Rollback to specific checkpoint (if available)
  Output: Rollback status and required manual steps
```

#### New Virtual Tables

```sql
-- Real-time migration status
CREATE VIRTUAL TABLE system_views.cms_migration_status (
    node_id uuid,
    migration_phase text,
    migration_state text,
    progress_percentage int,
    started_at timestamp,
    last_update timestamp,
    estimated_completion timestamp,
    error_message text,
    PRIMARY KEY (node_id)
);

-- Migration events log
CREATE VIRTUAL TABLE system_views.cms_migration_events (
    event_id timeuuid,
    node_id uuid,
    event_type text,
    event_description text,
    event_timestamp timestamp,
    metadata map<text, text>,
    PRIMARY KEY (event_id)
);

-- CMS membership recommendations
CREATE VIRTUAL TABLE system_views.cms_membership_recommendations (
    recommendation_id int,
    node_id uuid,
    datacenter text,
    rack text,
    recommendation_score double,
    reasoning text,
    PRIMARY KEY (recommendation_id, node_id)
);
```

### Implementation Details

#### 1. Automated Migration Validation

```java
public class CMSMigrationValidator {
    public ValidationResult validate(ClusterMetadata metadata) {
        ValidationResult result = new ValidationResult();
        
        // Check schema consistency
        result.addCheck("schema_consistency", validateSchemaConsistency(metadata));
        
        // Verify topology stability
        result.addCheck("topology_stability", validateTopologyStability(metadata));
        
        // Check for in-progress operations
        result.addCheck("pending_operations", validateNoPendingOperations(metadata));
        
        // Validate network connectivity
        result.addCheck("network_connectivity", validateNetworkConnectivity(metadata));
        
        // Check disk space requirements
        result.addCheck("disk_space", validateDiskSpace(metadata));
        
        return result;
    }
    
    private boolean validateSchemaConsistency(ClusterMetadata metadata) {
        // Ensure all nodes have consistent schema version
        Set<UUID> schemaVersions = metadata.getSchemaVersions();
        if (schemaVersions.size() > 1) {
            logger.warn("Multiple schema versions detected: {}", schemaVersions);
            return false;
        }
        return true;
    }
    
    private boolean validateTopologyStability(ClusterMetadata metadata) {
        // Check for recent topology changes
        Duration stabilityWindow = Duration.ofMinutes(30);
        Instant lastTopologyChange = metadata.getLastTopologyChange();
        if (lastTopologyChange.plus(stabilityWindow).isAfter(Instant.now())) {
            logger.warn("Recent topology change detected at {}", lastTopologyChange);
            return false;
        }
        return true;
    }
}
```

#### 2. Migration Plan Generation

```java
public class CMSMigrationPlanner {
    public MigrationPlan generatePlan(ClusterMetadata metadata, MigrationOptions options) {
        MigrationPlan plan = new MigrationPlan();
        
        // Phase 1: Pre-migration checks
        plan.addPhase(new PreMigrationPhase()
            .withValidation()
            .withBackup(options.isBackupEnabled()));
        
        // Phase 2: CMS initialization
        plan.addPhase(new CMSInitializationPhase()
            .withMembers(selectCMSMembers(metadata, options))
            .withQuorumSize(calculateQuorumSize(metadata)));
        
        // Phase 3: Metadata migration
        plan.addPhase(new MetadataMigrationPhase()
            .withBatchSize(options.getBatchSize())
            .withThrottling(options.getThrottleMs()));
        
        // Phase 4: Validation and cleanup
        plan.addPhase(new PostMigrationPhase()
            .withValidation()
            .withCleanup(options.isCleanupEnabled()));
        
        return plan;
    }
    
    private Set<InetAddressAndPort> selectCMSMembers(ClusterMetadata metadata, MigrationOptions options) {
        if (options.hasExplicitMembers()) {
            return options.getExplicitMembers();
        }
        
        // Intelligent member selection based on:
        // - Datacenter distribution
        // - Rack awareness
        // - Node stability history
        // - Hardware capabilities
        CMSMemberSelector selector = new CMSMemberSelector();
        return selector.selectOptimalMembers(metadata, options.getCMSSize());
    }
}
```

#### 3. Migration Progress Tracking

```java
public class CMSMigrationTracker {
    private final AtomicReference<MigrationState> currentState;
    private final MigrationMetrics metrics;
    private final EventBus eventBus;
    
    public void startMigration(MigrationPlan plan) {
        currentState.set(MigrationState.INITIALIZING);
        metrics.recordMigrationStart();
        eventBus.post(new MigrationStartedEvent(plan));
        
        // Create initial checkpoint
        createCheckpoint("migration_start");
    }
    
    public void updateProgress(String phase, double percentage, String message) {
        MigrationProgress progress = new MigrationProgress(phase, percentage, message);
        currentState.updateAndGet(state -> state.withProgress(progress));
        
        metrics.recordProgress(phase, percentage);
        eventBus.post(new MigrationProgressEvent(progress));
        
        // Create checkpoint at major milestones
        if (percentage % 25 == 0) {
            createCheckpoint(String.format("phase_%s_%d", phase, (int)percentage));
        }
    }
    
    private void createCheckpoint(String checkpointName) {
        // Save current cluster state for potential rollback
        MigrationCheckpoint checkpoint = new MigrationCheckpoint()
            .withName(checkpointName)
            .withTimestamp(Instant.now())
            .withClusterState(captureClusterState())
            .withMetadata(captureMetadata());
        
        checkpointStore.save(checkpoint);
    }
}
```

#### 4. Rollback Support

```java
public class CMSMigrationRollback {
    private final CheckpointStore checkpointStore;
    private final ClusterMetadataManager metadataManager;
    
    public RollbackResult rollback(String checkpointId, RollbackOptions options) {
        // Validate rollback is possible
        if (!canRollback(checkpointId)) {
            return RollbackResult.failure("Rollback not possible from current state");
        }
        
        MigrationCheckpoint checkpoint = checkpointStore.load(checkpointId);
        
        // Phase 1: Stop ongoing migration operations
        stopMigrationOperations();
        
        // Phase 2: Restore metadata state
        restoreMetadataState(checkpoint);
        
        // Phase 3: Revert CMS initialization if needed
        if (checkpoint.isPreCMS()) {
            revertCMSInitialization();
        }
        
        // Phase 4: Validate rollback success
        validateRollback(checkpoint);
        
        return RollbackResult.success()
            .withCheckpoint(checkpoint)
            .withManualSteps(generateManualSteps(checkpoint));
    }
    
    private boolean canRollback(String checkpointId) {
        // Check if rollback window hasn't expired
        // Verify no irreversible operations have occurred
        // Ensure checkpoint data is intact
        return true; // Simplified
    }
}
```

### Performance Considerations

1. **Migration Throttling**: Configurable throttling to minimize impact on production traffic
2. **Batch Processing**: Metadata migration in configurable batch sizes
3. **Parallel Validation**: Concurrent validation checks where possible
4. **Incremental Checkpoints**: Minimal overhead checkpoint creation
5. **Efficient State Tracking**: Low-overhead progress monitoring

### Security Considerations

1. **Authorization**: Only administrators can initiate migration or rollback
2. **Audit Logging**: All migration operations logged to audit log
3. **Encrypted Checkpoints**: Checkpoint data encrypted at rest
4. **Network Security**: CMS communication uses existing internode encryption

## Alternatives Considered

### Alternative 1: Fully Automated Migration

**Description**: Completely automated migration with no manual intervention required.

**Why not chosen**: 
- Too risky for production environments
- Operators need control over timing and membership selection
- Different clusters have different requirements and constraints

### Alternative 2: External Migration Tool

**Description**: Separate tool/utility for managing CMS migration outside of Cassandra.

**Why not chosen**:
- Increases operational complexity
- Requires additional deployment and maintenance
- Better to have integrated tooling for consistency

### Alternative 3: Minimal Enhancement

**Description**: Only add basic validation and leave migration process largely manual.

**Why not chosen**:
- Insufficient for production requirements
- Doesn't address core operational concerns
- Would limit CMS adoption

## Migration Path

### Backward Compatibility

This RFC maintains full backward compatibility:
- Existing manual migration commands continue to work
- New features are additive, not replacing existing functionality
- Clusters already migrated are unaffected

### Migration Steps

1. **Upgrade to 5.1.x** (with these enhancements)
   - Rolling upgrade supported
   - No immediate migration required

2. **Validation Phase**
   ```bash
   nodetool cms validate --verbose
   ```

3. **Planning Phase**
   ```bash
   nodetool cms migrate --plan --recommend-members
   ```

4. **Execution Phase**
   ```bash
   nodetool cms initialize --members <selected_members>
   ```

5. **Monitoring Phase**
   ```bash
   nodetool cms status
   # Monitor via virtual tables
   ```

6. **Verification Phase**
   ```bash
   nodetool cms validate --post-migration
   ```

### Rollback Plan

If issues occur during migration:

1. **Within Rollback Window** (< 24 hours)
   ```bash
   nodetool cms rollback --checkpoint <checkpoint_id>
   ```

2. **After Rollback Window**
   - Manual intervention required
   - Contact support for assistance
   - Follow documented recovery procedures

## Testing Strategy

### Unit Tests

- Validation logic for each check type
- Migration plan generation with various cluster configurations
- Progress tracking state transitions
- Checkpoint creation and restoration
- Rollback logic for different scenarios

### Integration Tests

- End-to-end migration with small cluster
- Migration with simulated failures at each phase
- Rollback testing from various checkpoints
- Virtual table data accuracy
- Command output validation

### Performance Tests

- Migration impact on cluster performance
- Large cluster migration (100+ nodes)
- Checkpoint creation overhead
- Virtual table query performance
- Migration with high data volumes

### Chaos Testing

- Network partitions during migration
- Node failures during migration
- Disk space exhaustion scenarios
- Schema changes during migration

## Timeline and Milestones

| Milestone | Target Date | Description |
|-----------|------------|-------------|
| Design Review | 2024-12-01 | Complete design review with community |
| Prototype | 2024-12-15 | Working prototype with core features |
| Implementation | 2025-01-31 | Full implementation complete |
| Testing | 2025-02-28 | Comprehensive test coverage |
| Documentation | 2025-03-15 | User and operator documentation |
| Beta Release | 2025-03-31 | Available in 5.1-beta |
| GA Release | 2025-04-30 | Production-ready in 5.1.1 |

## Dependencies

- CEP-21 core implementation (completed in 5.1)
- Virtual table infrastructure
- Audit logging framework
- Metrics and monitoring framework

## Unresolved Questions

- [ ] Should we support automatic rollback on migration failure?
- [ ] What should be the default rollback window duration?
- [ ] Should checkpoint data be replicated across nodes?
- [ ] How to handle migration with multiple datacenters in different network partitions?
- [ ] Should we provide a dry-run mode that simulates the entire migration?

## References

- [CEP-21: Transactional Cluster Metadata](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-21%3A+Transactional+Cluster+Metadata)
- [CASSANDRA-18798: CEP-21 Implementation](https://issues.apache.org/jira/browse/CASSANDRA-18798)
- [Migration Best Practices Document](TBD)
- [CMS Architecture Documentation](TBD)

## Appendix

### A. Sample Migration Output

```
$ nodetool cms validate --verbose
Validating cluster for CMS migration...
[✓] Schema consistency check: PASSED
    All nodes on version: 7d4f9180-3e0a-11eb-b378-0242ac130003
[✓] Topology stability check: PASSED
    No topology changes in last 30 minutes
[✓] Pending operations check: PASSED
    No repairs, compactions, or streaming in progress
[✓] Network connectivity check: PASSED
    All nodes reachable with latency < 10ms
[✓] Disk space check: PASSED
    All nodes have > 20% free space

Validation Result: READY FOR MIGRATION
Recommended CMS members:
  - 10.0.1.1 (dc1, rack1) - Score: 95/100
  - 10.0.2.1 (dc2, rack1) - Score: 94/100
  - 10.0.3.1 (dc3, rack1) - Score: 93/100
```

### B. Migration Plan Example

```yaml
migration_plan:
  version: 1.0
  generated: 2024-11-15T10:30:00Z
  estimated_duration: 45 minutes
  
  phases:
    - name: pre_migration
      steps:
        - validate_cluster
        - create_backup
        - notify_operators
      estimated_duration: 10 minutes
    
    - name: cms_initialization
      steps:
        - initialize_cms_nodes
        - establish_quorum
        - verify_cms_health
      estimated_duration: 5 minutes
      cms_members:
        - 10.0.1.1
        - 10.0.2.1
        - 10.0.3.1
    
    - name: metadata_migration
      steps:
        - migrate_keyspaces
        - migrate_tables
        - migrate_indexes
        - migrate_views
      estimated_duration: 25 minutes
      batch_size: 100
      throttle_ms: 10
    
    - name: post_migration
      steps:
        - validate_migration
        - cleanup_old_metadata
        - update_system_tables
      estimated_duration: 5 minutes