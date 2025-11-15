# RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)

- **Status:** Draft
- **Type:** Enhancement
- **Priority:** P1-Critical
- **Start Date:** 2024-11-15
- **Author(s):** Cassandra Development Team
- **Ticket:** CASSANDRA-XXXXX
- **Discussion:** [Dev mailing list thread]

## Summary

This RFC proposes comprehensive enhancements to the Auto Repair feature (CEP-37) to improve production readiness through better observability, adaptive scheduling, enhanced control mechanisms, and integration with compaction. These improvements will enable widespread adoption of automatic repair in production environments, reducing operational burden while maintaining data consistency.

## Motivation

Auto Repair (CEP-37) represents a significant operational improvement for Cassandra, automating the previously manual and error-prone repair process. However, production deployment feedback has revealed gaps in observability, control, and integration that must be addressed for enterprise-grade deployments.

### Current State

The current Auto Repair implementation provides:
- Basic automatic repair scheduling
- Simple time-based repair intervals
- Minimal visibility into repair operations
- Fixed repair strategies without adaptation to cluster load
- Limited integration with other maintenance operations

### Problem Statement

Production operators face several challenges with current Auto Repair:

1. **Lack of Visibility**: Insufficient insight into why specific ranges are selected for repair and when repairs will occur
2. **Resource Contention**: Repairs can conflict with compaction and normal operations, causing performance issues
3. **Inflexible Scheduling**: No ability to pause repairs during critical operations or high-load periods
4. **Missing Metrics**: Inadequate metrics to measure repair effectiveness and efficiency
5. **Poor Integration**: Lack of coordination with compaction and other maintenance tasks

## Detailed Design

### API Changes

#### New nodetool Commands

```bash
# Pause auto repair temporarily
nodetool autorepair pause --duration <duration> [--reason <reason>]
  Options:
    --duration: How long to pause (e.g., "4h", "2d")
    --reason: Optional reason for audit logging
  Example: nodetool autorepair pause --duration 4h --reason "maintenance window"

# Resume auto repair
nodetool autorepair resume [--force]
  Options:
    --force: Resume immediately even if pause duration hasn't expired

# Get detailed auto repair status
nodetool autorepair status [--detailed]
  Output: Current state, next scheduled repair, pause status

# View repair history
nodetool autorepair history --last <duration> [--keyspace <ks>]
  Options:
    --last: Time period to show (e.g., "7d", "24h")
    --keyspace: Filter by specific keyspace
  Output: Repair operations with timing and success metrics

# Configure adaptive scheduling
nodetool autorepair configure --adaptive [--threshold <value>]
  Options:
    --adaptive: Enable/disable adaptive scheduling
    --threshold: Load threshold for automatic backoff
```

#### New Virtual Tables

```sql
-- Auto repair schedule view
CREATE VIRTUAL TABLE system_views.auto_repair_schedule (
    keyspace_name text,
    table_name text,
    token_range_start text,
    token_range_end text,
    next_repair_time timestamp,
    last_repair_time timestamp,
    last_repair_duration_ms bigint,
    repair_priority int,
    estimated_data_size bigint,
    PRIMARY KEY ((keyspace_name, table_name), token_range_start)
);

-- Repair effectiveness metrics
CREATE VIRTUAL TABLE system_views.repair_effectiveness (
    keyspace_name text,
    table_name text,
    period_start timestamp,
    period_end timestamp,
    bytes_repaired bigint,
    bytes_validated bigint,
    discrepancies_found int,
    repair_efficiency_ratio double,
    merkle_trees_built int,
    streaming_sessions int,
    average_repair_time_ms bigint,
    PRIMARY KEY ((keyspace_name, table_name), period_start)
);

-- Repair decision log
CREATE VIRTUAL TABLE system_views.repair_decisions (
    decision_id timeuuid,
    decision_time timestamp,
    keyspace_name text,
    table_name text,
    token_range text,
    decision_type text,  -- 'scheduled', 'skipped', 'deferred', 'prioritized'
    reason text,
    metadata map<text, text>,
    PRIMARY KEY (decision_id)
);

-- System load metrics for adaptive scheduling
CREATE VIRTUAL TABLE system_views.repair_load_metrics (
    node_id uuid,
    measurement_time timestamp,
    cpu_usage_percent double,
    compaction_pending_tasks int,
    active_repairs int,
    streaming_connections int,
    read_latency_ms double,
    write_latency_ms double,
    load_score double,  -- Composite score for scheduling decisions
    PRIMARY KEY (node_id, measurement_time)
) WITH CLUSTERING ORDER BY (measurement_time DESC);
```

#### Configuration Options

```yaml
# cassandra.yaml additions
auto_repair:
    # Enable adaptive scheduling based on cluster load
    adaptive_scheduling_enabled: true
    
    # Load threshold above which repairs are deferred (0.0 to 1.0)
    load_threshold: 0.7
    
    # Minimum interval between repairs (prevents thrashing)
    min_repair_interval: 30m
    
    # Maximum interval between repairs (ensures consistency)
    max_repair_interval: 7d
    
    # Integration with compaction
    coordinate_with_compaction: true
    
    # Repair decision logging
    log_repair_decisions: true
    
    # Metrics collection interval
    metrics_collection_interval: 1m
    
    # Repair effectiveness threshold (triggers alerts if below)
    min_effectiveness_ratio: 0.95
```

### Implementation Details

#### 1. Scheduler Observability

```java
public class ObservableRepairScheduler extends RepairScheduler {
    private final RepairDecisionLogger decisionLogger;
    private final RepairScheduleTracker scheduleTracker;
    private final VirtualTableManager virtualTableManager;
    
    @Override
    public RepairTask selectNextRepair() {
        Collection<RepairCandidate> candidates = gatherCandidates();
        
        for (RepairCandidate candidate : candidates) {
            RepairDecision decision = evaluateCandidate(candidate);
            
            // Log all decisions for observability
            decisionLogger.logDecision(decision);
            
            if (decision.shouldRepair()) {
                // Update schedule tracking
                scheduleTracker.scheduleRepair(candidate, decision.getScheduledTime());
                
                // Update virtual tables
                updateVirtualTables(candidate, decision);
                
                return createRepairTask(candidate);
            }
        }
        
        return null;
    }
    
    private RepairDecision evaluateCandidate(RepairCandidate candidate) {
        RepairDecision.Builder decision = RepairDecision.builder()
            .candidate(candidate)
            .timestamp(System.currentTimeMillis());
        
        // Check time since last repair
        Duration timeSinceLastRepair = candidate.getTimeSinceLastRepair();
        if (timeSinceLastRepair.compareTo(minRepairInterval) < 0) {
            return decision
                .type(DecisionType.SKIPPED)
                .reason("Below minimum repair interval")
                .build();
        }
        
        // Check system load
        double systemLoad = getSystemLoad();
        if (systemLoad > loadThreshold) {
            return decision
                .type(DecisionType.DEFERRED)
                .reason(String.format("System load %.2f exceeds threshold %.2f", 
                                    systemLoad, loadThreshold))
                .build();
        }
        
        // Check for conflicting operations
        if (hasConflictingOperations(candidate)) {
            return decision
                .type(DecisionType.DEFERRED)
                .reason("Conflicting operations in progress")
                .build();
        }
        
        // Calculate priority based on multiple factors
        double priority = calculatePriority(candidate);
        
        return decision
            .type(DecisionType.SCHEDULED)
            .priority(priority)
            .scheduledTime(System.currentTimeMillis())
            .reason("Scheduled for repair")
            .build();
    }
}
```

#### 2. Adaptive Scheduling

```java
public class AdaptiveRepairScheduler {
    private final LoadMonitor loadMonitor;
    private final RepairRateController rateController;
    private final MachineLearningPredictor mlPredictor; // Optional future enhancement
    
    public class LoadMonitor {
        private final CircularFifoQueue<LoadSnapshot> loadHistory;
        
        public double getCurrentLoad() {
            LoadSnapshot snapshot = LoadSnapshot.builder()
                .cpuUsage(getCpuUsage())
                .memoryUsage(getMemoryUsage())
                .compactionPending(getCompactionPendingTasks())
                .activeRepairs(getActiveRepairCount())
                .readLatency(getReadLatency())
                .writeLatency(getWriteLatency())
                .timestamp(System.currentTimeMillis())
                .build();
            
            loadHistory.add(snapshot);
            
            // Calculate composite load score
            return calculateLoadScore(snapshot);
        }
        
        private double calculateLoadScore(LoadSnapshot snapshot) {
            // Weighted combination of metrics
            double score = 0.0;
            score += snapshot.cpuUsage * 0.3;
            score += normalizeMemoryUsage(snapshot.memoryUsage) * 0.2;
            score += normalizeCompactionPending(snapshot.compactionPending) * 0.2;
            score += normalizeLatency(snapshot.readLatency, snapshot.writeLatency) * 0.3;
            
            return Math.min(1.0, score);
        }
        
        public double predictFutureLoad(Duration lookahead) {
            // Simple trend analysis (can be enhanced with ML)
            List<LoadSnapshot> recent = getRecentSnapshots(Duration.ofMinutes(30));
            double trend = calculateTrend(recent);
            double currentLoad = getCurrentLoad();
            
            return Math.min(1.0, currentLoad + (trend * lookahead.toMinutes()));
        }
    }
    
    public class RepairRateController {
        private double currentRate = 1.0; // 1.0 = normal rate
        private final double MIN_RATE = 0.1; // 10% of normal
        private final double MAX_RATE = 1.5; // 150% of normal
        
        public void adjustRate(double systemLoad) {
            if (systemLoad > 0.8) {
                // High load - reduce repair rate
                currentRate = Math.max(MIN_RATE, currentRate * 0.9);
            } else if (systemLoad < 0.3) {
                // Low load - increase repair rate
                currentRate = Math.min(MAX_RATE, currentRate * 1.1);
            } else {
                // Moderate load - gradually return to normal
                currentRate = currentRate * 0.95 + 1.0 * 0.05;
            }
        }
        
        public Duration getNextRepairDelay() {
            Duration baseDelay = Duration.ofMinutes(5);
            return baseDelay.multipliedBy((long)(1.0 / currentRate));
        }
    }
}
```

#### 3. Enhanced Control

```java
public class RepairControlManager {
    private final AtomicReference<RepairState> state;
    private final ScheduledExecutorService executor;
    private volatile Instant pauseUntil;
    
    public enum RepairState {
        RUNNING, PAUSED, SUSPENDED, STOPPING
    }
    
    public PauseResult pauseAutoRepair(Duration duration, String reason) {
        if (!state.compareAndSet(RepairState.RUNNING, RepairState.PAUSED)) {
            return PauseResult.failure("Auto repair is not currently running");
        }
        
        pauseUntil = Instant.now().plus(duration);
        
        // Log pause event
        auditLog.log(AuditEvent.AUTO_REPAIR_PAUSED, 
                    ImmutableMap.of("duration", duration.toString(), 
                                   "reason", reason,
                                   "resume_at", pauseUntil.toString()));
        
        // Schedule automatic resume
        executor.schedule(this::resumeAutoRepair, duration.toMillis(), TimeUnit.MILLISECONDS);
        
        // Cancel any in-progress repairs gracefully
        cancelActiveRepairs(false);
        
        return PauseResult.success()
            .withResumeTime(pauseUntil)
            .withMessage("Auto repair paused until " + pauseUntil);
    }
    
    public ResumeResult resumeAutoRepair() {
        if (!state.compareAndSet(RepairState.PAUSED, RepairState.RUNNING)) {
            return ResumeResult.failure("Auto repair is not currently paused");
        }
        
        pauseUntil = null;
        
        // Log resume event
        auditLog.log(AuditEvent.AUTO_REPAIR_RESUMED, ImmutableMap.of());
        
        // Immediately schedule next repair
        scheduleNextRepair();
        
        return ResumeResult.success()
            .withMessage("Auto repair resumed successfully");
    }
    
    public StatusResult getStatus() {
        return StatusResult.builder()
            .state(state.get())
            .pausedUntil(pauseUntil)
            .activeRepairs(getActiveRepairCount())
            .pendingRepairs(getPendingRepairCount())
            .lastRepairTime(getLastRepairTime())
            .nextScheduledRepair(getNextScheduledRepair())
            .effectivenessMetrics(calculateEffectivenessMetrics())
            .build();
    }
}
```

#### 4. Repair Effectiveness Metrics

```java
public class RepairEffectivenessTracker {
    private final MeterRegistry meterRegistry;
    private final RepairMetricsStore metricsStore;
    
    public class RepairMetrics {
        private final Counter bytesRepaired;
        private final Counter bytesValidated;
        private final Counter discrepanciesFound;
        private final Timer repairDuration;
        private final Gauge repairEfficiency;
        
        public void recordRepairCompletion(RepairResult result) {
            bytesRepaired.increment(result.getBytesRepaired());
            bytesValidated.increment(result.getBytesValidated());
            discrepanciesFound.increment(result.getDiscrepanciesFound());
            repairDuration.record(result.getDuration());
            
            // Calculate and update efficiency
            double efficiency = calculateEfficiency(result);
            updateEfficiencyGauge(efficiency);
            
            // Store detailed metrics for virtual table
            metricsStore.store(RepairMetricEntry.builder()
                .keyspace(result.getKeyspace())
                .table(result.getTable())
                .timestamp(System.currentTimeMillis())
                .bytesRepaired(result.getBytesRepaired())
                .bytesValidated(result.getBytesValidated())
                .discrepanciesFound(result.getDiscrepanciesFound())
                .duration(result.getDuration())
                .efficiency(efficiency)
                .merkleTrees(result.getMerkleTreeCount())
                .streamingSessions(result.getStreamingSessionCount())
                .build());
        }
        
        private double calculateEfficiency(RepairResult result) {
            if (result.getBytesValidated() == 0) {
                return 1.0;
            }
            
            // Efficiency = 1 - (bytes_repaired / bytes_validated)
            // Higher is better (fewer repairs needed)
            return 1.0 - (double) result.getBytesRepaired() / result.getBytesValidated();
        }
    }
    
    public EffectivenessReport generateReport(Duration period) {
        Instant start = Instant.now().minus(period);
        Collection<RepairMetricEntry> entries = metricsStore.query(start, Instant.now());
        
        return EffectivenessReport.builder()
            .period(period)
            .totalBytesRepaired(sumBytesRepaired(entries))
            .totalBytesValidated(sumBytesValidated(entries))
            .averageEfficiency(calculateAverageEfficiency(entries))
            .repairsByKeyspace(groupByKeyspace(entries))
            .discrepancyTrend(calculateDiscrepancyTrend(entries))
            .recommendations(generateRecommendations(entries))
            .build();
    }
}
```

#### 5. Integration with Compaction

```java
public class RepairCompactionCoordinator {
    private final CompactionManager compactionManager;
    private final RepairScheduler repairScheduler;
    
    public boolean shouldDeferRepair(RepairCandidate candidate) {
        // Check if major compaction is running on target ranges
        if (compactionManager.hasMajorCompactionInProgress(candidate.getRanges())) {
            logger.debug("Deferring repair due to major compaction on ranges {}", 
                        candidate.getRanges());
            return true;
        }
        
        // Check compaction backlog
        int pendingCompactions = compactionManager.getPendingTasks();
        int threshold = DatabaseDescriptor.getConcurrentCompactors() * 2;
        
        if (pendingCompactions > threshold) {
            logger.debug("Deferring repair due to high compaction backlog: {} pending", 
                        pendingCompactions);
            return true;
        }
        
        return false;
    }
    
    public void coordinateOperations() {
        // Create coordination window
        CoordinationWindow window = CoordinationWindow.create(Duration.ofHours(1));
        
        // Allocate time slots for operations
        window.allocate(OperationType.REPAIR, 0.4);      // 40% for repairs
        window.allocate(OperationType.COMPACTION, 0.4);  // 40% for compaction
        window.allocate(OperationType.NORMAL_OPS, 0.2);  // 20% buffer for normal ops
        
        // Schedule operations within their windows
        scheduleInWindow(window);
    }
    
    private void scheduleInWindow(CoordinationWindow window) {
        // Get next time slots
        TimeSlot repairSlot = window.getNextSlot(OperationType.REPAIR);
        TimeSlot compactionSlot = window.getNextSlot(OperationType.COMPACTION);
        
        // Schedule repair in its slot
        if (repairSlot != null && !repairSlot.isActive()) {
            repairScheduler.scheduleAt(repairSlot.getStartTime());
        }
        
        // Adjust compaction timing
        if (compactionSlot != null) {
            compactionManager.adjustSchedule(compactionSlot);
        }
    }
}
```

### Performance Considerations

1. **Minimal Overhead**: Metrics collection uses sampling and aggregation to minimize impact
2. **Efficient Decision Making**: Caching of decision factors to avoid repeated calculations  
3. **Adaptive Backoff**: Automatic rate reduction during high load periods
4. **Smart Range Selection**: Prioritize ranges with higher discrepancy probability
5. **Resource Pooling**: Shared thread pools for repair operations

### Security Considerations

1. **Audit Logging**: All repair control operations logged to audit log
2. **Authorization**: Only administrators can pause/resume auto repair
3. **Metrics Access**: Virtual tables respect existing security policies
4. **Network Security**: Repair streaming uses existing internode encryption

## Alternatives Considered

### Alternative 1: External Repair Orchestration

**Description**: Keep auto repair simple and use external tools for advanced orchestration.

**Why not chosen**:
- Increases operational complexity
- Loses benefits of integrated scheduling
- Harder to achieve optimal coordination

### Alternative 2: ML-Based Scheduling Only

**Description**: Rely entirely on machine learning for repair scheduling decisions.

**Why not chosen**:
- Requires training period and data
- Black box decision making reduces operator trust  
- Fallback mechanisms still needed

### Alternative 3: Fixed Schedule with Manual Override

**Description**: Simple time-based schedule with manual pause/resume only.

**Why not chosen**:
- Doesn't adapt to cluster load
- Can cause performance problems during peak times
- Insufficient for production requirements

## Migration Path

### Backward Compatibility

Fully backward compatible:
- Existing auto repair configurations continue to work
- New features are opt-in via configuration
- Virtual tables are additive

### Migration Steps

1. **Upgrade to Enhanced Version**
   - Rolling upgrade supported
   - Auto repair continues with existing behavior

2. **Enable Monitoring** (Optional)
   ```sql
   SELECT * FROM system_views.auto_repair_schedule;
   SELECT * FROM system_views.repair_effectiveness;
   ```

3. **Enable Adaptive Scheduling** (Optional)
   ```yaml
   auto_repair:
     adaptive_scheduling_enabled: true
     load_threshold: 0.7
   ```

4. **Configure Integration** (Optional)
   ```yaml
   auto_repair:
     coordinate_with_compaction: true
   ```

### Rollback Plan

If issues occur:
1. Disable new features via configuration
2. Revert to simple scheduling
3. Manual repair operations remain available

## Testing Strategy

### Unit Tests

- Scheduler decision logic with various scenarios
- Load monitoring calculations
- Effectiveness metrics computation
- Pause/resume state transitions
- Compaction coordination logic

### Integration Tests

- End-to-end repair with monitoring
- Adaptive scheduling under load
- Pause/resume during active repairs
- Virtual table data accuracy
- Coordination with concurrent compaction

### Performance Tests

- Overhead of metrics collection
- Impact of adaptive scheduling
- Large cluster repair coordination
- Virtual table query performance
- Resource usage under various loads

### Chaos Testing

- Node failures during repair
- Network partitions
- Rapid load changes
- Competing maintenance operations

## Timeline and Milestones

| Milestone | Target Date | Description |
|-----------|------------|-------------|
| Design Review | 2024-12-15 | Complete design review |
| Core Implementation | 2025-01-31 | Observability and control features |
| Adaptive Scheduling | 2025-02-28 | Load-based scheduling complete |
| Integration | 2025-03-15 | Compaction coordination done |
| Testing | 2025-03-31 | Comprehensive test coverage |
| Documentation | 2025-04-15 | Operator guide complete |
| Release | 2025-05-01 | Available in 5.2 |

## Dependencies

- CEP-37 core implementation
- Virtual table infrastructure
- Metrics framework
- Audit logging system

## Unresolved Questions

- [ ] Should we provide different scheduling strategies (aggressive, balanced, conservative)?
- [ ] How to handle repairs across multiple datacenters with different load patterns?
- [ ] Should repair effectiveness trigger automatic strategy changes?
- [ ] What granularity for repair progress reporting (per range, per table, per keyspace)?
- [ ] Should we support repair rate limiting per table/keyspace?

## References

- [CEP-37: Automatic Repair](https://cwiki.apache.org/confluence/display/CASSANDRA/CEP-37)
- [Repair Improvements Design Doc](TBD)
- [Production Repair Best Practices](TBD)

## Appendix

### A. Sample Status Output

```
$ nodetool autorepair status --detailed
Auto Repair Status
==================
State: RUNNING
Adaptive Scheduling: ENABLED
Current Load: 0.42 (moderate)
Repair Rate: 0.85x

Next Scheduled Repair:
  Keyspace: user_data
  Table: events
  Range: (-3074457345618258603, -3074457345618258602]
  Estimated Start: 2024-11-15 14:30:00 UTC
  
Recent Repairs (last 24h):
  Completed: 127
  Failed: 2
  Deferred: 18
  
Effectiveness Metrics:
  Efficiency Ratio: 0.973
  Bytes Repaired: 1.2 GB
  Bytes Validated: 45.6 GB
  Discrepancies Found: 342
  
Resource Usage:
  Repair Threads: 2/4
  Streaming Connections: 3
  Merkle Trees in Memory: 5
```

### B. Decision Log Sample

```json
{
  "decision_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-11-15T12:00:00Z",
  "keyspace": "user_data",
  "table": "events",
  "range": "(-3074457345618258603, -3074457345618258602]",
  "decision": "DEFERRED",
  "reason": "System load 0.82 exceeds threshold 0.70",
  "metadata": {
    "cpu_usage": "0.75",
    "pending_compactions": "12",
    "active_repairs": "2",
    "time_since_last_repair": "5h32m"
  }
}