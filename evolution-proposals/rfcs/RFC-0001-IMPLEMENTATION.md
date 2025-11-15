# RFC-0001 Implementation: CEP-21 Migration Stabilization

This document describes the implementation of RFC-0001, which introduces comprehensive enhancements to the CEP-21 (Transactional Cluster Metadata) migration process.

## Implementation Summary

The implementation consists of the following components:

### 1. Core Migration Classes

#### CMSMigrationValidator
**Location**: `src/java/org/apache/cassandra/tcm/migration/CMSMigrationValidator.java`

Validates cluster readiness for CMS migration with the following checks:
- Schema consistency across all nodes
- Topology stability (no recent changes)
- No pending operations (repairs, compactions, streaming)
- Network connectivity between nodes
- Sufficient disk space availability
- Node liveness verification

#### CMSMigrationPlanner
**Location**: `src/java/org/apache/cassandra/tcm/migration/CMSMigrationPlanner.java`

Generates comprehensive migration plans with:
- Intelligent CMS member selection based on datacenter distribution and rack awareness
- Phased migration approach (pre-migration, initialization, metadata migration, post-migration)
- Timing estimates for each phase
- Configurable batch sizes and throttling
- Backup and cleanup options

#### CMSMigrationTracker
**Location**: `src/java/org/apache/cassandra/tcm/migration/CMSMigrationTracker.java`

Tracks migration progress with:
- Real-time state and progress monitoring
- Event logging with configurable history
- Checkpoint creation at major milestones
- Migration metrics collection
- Support for rollback via checkpoints

#### CMSMigrationRollback
**Location**: `src/java/org/apache/cassandra/tcm/migration/CMSMigrationRollback.java`

Handles migration rollback with:
- Checkpoint-based rollback
- Validation of rollback feasibility
- Safe rollback window enforcement
- Manual verification step generation
- Force rollback option for emergency scenarios

### 2. Virtual Tables for Monitoring

#### CMSMigrationStatusTable
**Location**: `src/java/org/apache/cassandra/db/virtual/CMSMigrationStatusTable.java`

Exposes: `system_views.cms_migration_status`
- Real-time migration phase and state
- Progress percentage
- Start time and estimated completion
- Error messages

#### CMSMigrationEventsTable
**Location**: `src/java/org/apache/cassandra/db/virtual/CMSMigrationEventsTable.java`

Exposes: `system_views.cms_migration_events`
- Complete migration event history
- Event types and timestamps
- Event metadata
- Searchable event log

#### CMSMembershipRecommendationsTable
**Location**: `src/java/org/apache/cassandra/db/virtual/CMSMembershipRecommendationsTable.java`

Exposes: `system_views.cms_membership_recommendations`
- Recommended CMS members
- Recommendation scores
- Datacenter and rack information
- Reasoning for recommendations

### 3. Nodetool Commands

Enhanced `nodetool cms` command with new subcommands:

#### `nodetool cms validate [--verbose]`
Validates cluster readiness for CMS migration
- Runs all pre-migration validation checks
- Shows detailed results with `--verbose` flag
- Returns pass/fail status for each check

#### `nodetool cms status`
Shows current CMS migration status
- Displays migration state and phase
- Shows progress percentage
- Provides estimated completion time
- Shows any error messages

#### `nodetool cms history [--last <duration>] [--limit <count>]`
Shows migration history and events
- Filter by time duration (e.g., "7d", "24h")
- Limit number of events shown
- Displays timestamped event log

#### `nodetool cms plan [--recommend-members] [--cms-size <size>]`
Generates migration plan without executing
- Shows estimated duration
- Displays all migration phases and steps
- Recommends optimal CMS members
- Configurable CMS size

#### `nodetool cms rollback [--checkpoint <id>] [--list] [--force]`
Rollback migration to previous checkpoint
- List available checkpoints with `--list`
- Rollback to specific checkpoint
- Force rollback with `--force` option
- Provides manual verification steps

### 4. Unit Tests

#### CMSMigrationValidatorTest
**Location**: `test/unit/org/apache/cassandra/tcm/migration/CMSMigrationValidatorTest.java`

Tests for validation logic:
- Validation check creation
- Result aggregation
- Pass/fail status handling

#### CMSMigrationPlannerTest
**Location**: `test/unit/org/apache/cassandra/tcm/migration/CMSMigrationPlannerTest.java`

Tests for migration planning:
- Plan creation
- Phase management
- Options configuration
- Duration estimation

## Usage Examples

### Pre-Migration Validation
```bash
# Run validation checks
nodetool cms validate --verbose

# Expected output:
# [✓] Schema consistency check: PASSED
#     All nodes on same schema version
# [✓] Topology stability check: PASSED
#     No topology changes in last 30 minutes
# [✓] Pending operations check: PASSED
#     No repairs, compactions, or streaming in progress
# [✓] Network connectivity check: PASSED
#     All nodes reachable
# [✓] Disk space check: PASSED
#     All nodes have sufficient free space
#
# Validation Result: READY FOR MIGRATION
```

### Migration Planning
```bash
# Generate migration plan
nodetool cms plan --recommend-members --cms-size 5

# Expected output:
# CMS Migration Plan
# ==================
# Estimated Duration: 45 minutes
# CMS Size: 5
#
# Recommended CMS Members:
#   Query system_views.cms_membership_recommendations
#
# Phases:
#   1. Pre-migration (10 min)
#   2. CMS initialization (5 min)
#   3. Metadata migration (25 min)
#   4. Post-migration (5 min)
```

### Monitoring Migration
```bash
# Check migration status
nodetool cms status

# View migration events
nodetool cms history --last 1h --limit 20

# Query virtual tables
cqlsh -e "SELECT * FROM system_views.cms_migration_status;"
cqlsh -e "SELECT * FROM system_views.cms_migration_events ORDER BY event_timestamp DESC LIMIT 10;"
```

### Rollback
```bash
# List available checkpoints
nodetool cms rollback --list

# Rollback to specific checkpoint
nodetool cms rollback --checkpoint migration_start

# Force rollback if outside safe window
nodetool cms rollback --checkpoint phase_cms_initialization_100 --force
```

## Integration Points

### MBean Interface
The implementation requires MBean interfaces to expose functionality to nodetool:
- CMSMigrationValidatorMBean
- CMSMigrationPlannerMBean
- CMSMigrationTrackerMBean
- CMSMigrationRollbackMBean

### Virtual Table Registration
Virtual tables must be registered in `SystemViewsKeyspace`:
```java
tables.add(new CMSMigrationStatusTable(NAME, tracker));
tables.add(new CMSMigrationEventsTable(NAME, tracker));
tables.add(new CMSMembershipRecommendationsTable(NAME));
```

### Service Integration
Components integrate with existing Cassandra services:
- ClusterMetadataService for metadata access
- Gossiper for node liveness
- CompactionManager for compaction status
- StreamManager for streaming status
- RepairRunnable for repair status

## Configuration

No new configuration parameters are required. All features use sensible defaults:
- Rollback window: 24 hours
- Safe rollback window: 1 hour
- Default CMS size: 3 nodes
- Topology stability window: 30 minutes
- Minimum free disk space: 20%

## Testing Strategy

### Unit Tests
- Validation logic for each check type
- Migration plan generation
- Progress tracking state transitions
- Checkpoint creation and restoration
- Rollback logic

### Integration Tests
- End-to-end migration with test cluster
- Migration with simulated failures
- Rollback from various checkpoints
- Virtual table data accuracy
- Command output validation

### Performance Tests
- Migration impact on cluster performance
- Large cluster migration (100+ nodes)
- Checkpoint creation overhead
- Virtual table query performance

## Future Enhancements

Potential future improvements beyond this RFC:
1. Automatic rollback on migration failure
2. Dry-run mode for migration simulation
3. Cross-datacenter migration coordination
4. Enhanced checkpoint replication
5. Migration performance tuning recommendations
6. Integration with monitoring systems (Prometheus, Grafana)

## References

- [CEP-21: Transactional Cluster Metadata](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-21%3A+Transactional+Cluster+Metadata)
- [RFC-0001: Complete CEP-21 Migration and Stabilization](RFC-0001-cep21-migration-stabilization.md)
- [CASSANDRA-18798: CEP-21 Implementation](https://issues.apache.org/jira/browse/CASSANDRA-18798)
