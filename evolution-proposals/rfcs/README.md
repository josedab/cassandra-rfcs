# Apache Cassandra RFC Index

This directory contains Request for Comments (RFC) documents proposing enhancements and new features for Apache Cassandra. These RFCs are based on the evolution proposals outlined in [cassandra-next-generation.md](../cassandra-next-generation.md).

## RFC Process

Each RFC follows a structured format including:
- **Summary**: Brief overview of the proposal
- **Motivation**: Problem statement and rationale
- **Detailed Design**: Technical implementation details
- **Alternatives**: Other approaches considered
- **Migration Path**: Upgrade and compatibility strategy
- **Testing Strategy**: Validation approach
- **Timeline**: Implementation milestones

## RFC Status Types

- **Draft**: Initial proposal under discussion
- **Review**: Under formal review process
- **Accepted**: Approved for implementation
- **Implemented**: Completed and released
- **Withdrawn**: No longer under consideration

## Active RFCs

### Priority 1: Critical Path Improvements

#### [RFC-0001: Complete CEP-21 Migration and Stabilization](RFC-0001-cep21-migration-stabilization.md)
- **Status**: Draft
- **Priority**: P1-Critical
- **Summary**: Enhance CEP-21 (Transactional Cluster Metadata) migration with automated validation, improved tooling, rollback support, and comprehensive observability.
- **Key Features**:
  - Automated migration validation
  - Pre-flight checks and dry-run mode
  - Enhanced rollback procedures
  - Migration progress tracking
- **Timeline**: 3-6 months

#### [RFC-0002: Auto Repair Stabilization and Enhancement](RFC-0002-auto-repair-stabilization.md)
- **Status**: Draft
- **Priority**: P1-Critical  
- **Summary**: Stabilize and enhance Auto Repair (CEP-37) with improved observability, adaptive scheduling, and production-ready features.
- **Key Features**:
  - Virtual tables for repair visibility
  - Adaptive scheduling based on load
  - Pause/resume capabilities
  - Repair effectiveness metrics
- **Timeline**: 4-6 months

#### [RFC-0003: Unified Compaction Strategy Production Hardening](RFC-0003-unified-compaction-strategy.md)
- **Status**: Draft
- **Priority**: P1-Critical
- **Summary**: Harden the Unified Compaction Strategy for production use with workload-aware tuning, adaptive parameters, and migration tooling.
- **Key Features**:
  - Workload analysis and recommendations
  - Adaptive parameter tuning
  - A/B testing framework
  - Automated migration from other strategies
- **Timeline**: 6-9 months

### Priority 2: Performance and Scalability

#### [RFC-0004: Advanced Query Planning and Optimization](RFC-0004-advanced-query-planning.md)
- **Status**: Draft
- **Priority**: P2-High
- **Summary**: Implement sophisticated query planning with cost estimation, multi-index optimization, partition pruning, and adaptive execution.
- **Key Features**:
  - Cost-based query optimization
  - Index intersection/union decisions
  - Query plan caching
  - Result size prediction
- **Timeline**: 9-12 months

#### [RFC-0005: Tiered Storage Support](RFC-0005-tiered-storage-support.md)
- **Status**: Draft
- **Priority**: P2-High
- **Summary**: Enable automatic data lifecycle management across multiple storage tiers (NVMe, SSD, HDD, S3) for cost optimization.
- **Key Features**:
  - Multiple storage tier support
  - Automatic data migration
  - Transparent cross-tier queries
  - Cost-based tier placement
- **Timeline**: 12-18 months

#### [RFC-0006: Read/Write Path Optimizations](RFC-0006-read-write-path-optimizations.md)
- **Status**: Draft
- **Priority**: P2-High
- **Summary**: Optimize core read/write paths with Project Loom virtual threads, direct buffers, lock-free structures, and SIMD operations.
- **Key Features**:
  - Virtual thread integration (Java 21+)
  - Zero-copy buffer operations
  - Lock-free data structures
  - SIMD/vectorization support
- **Timeline**: 12+ months (ongoing)

### Priority 3: Developer Experience

#### [RFC-0007: Intelligent Schema Designer Tool](RFC-0007-intelligent-schema-designer.md)
- **Status**: Draft
- **Priority**: P3-Medium
- **Summary**: Interactive tool for optimal schema design based on application queries, with anti-pattern detection and migration planning.
- **Key Features**:
  - Interactive design wizard
  - Query-driven schema generation
  - Anti-pattern detection
  - Migration plan generation
- **Timeline**: 6-8 months

#### [RFC-0008: Enhanced CQL Features](RFC-0008-enhanced-cql-features.md)
- **Status**: Draft
- **Priority**: P3-Medium
- **Summary**: Add Common Table Expressions, window functions, limited recursive queries, and enhanced aggregations to CQL.
- **Key Features**:
  - WITH clause (CTEs)
  - Window functions (ROW_NUMBER, RANK, etc.)
  - Limited recursive queries
  - Statistical aggregations
- **Timeline**: 12-15 months

### Priority 4: Cloud and Kubernetes

#### RFC-0009: First-Class Kubernetes Operator
- **Status**: Planned
- **Priority**: P4-High
- **Summary**: Sophisticated Kubernetes operator for automated cluster management, scaling, and day-2 operations.
- **Timeline**: 12 months

#### RFC-0010: Observability and Monitoring Improvements
- **Status**: Planned
- **Priority**: P4-Medium-High
- **Summary**: Native integration with OpenTelemetry, Prometheus, structured logging, and distributed tracing.
- **Timeline**: 6-8 months

### Priority 5: Advanced Features

#### RFC-0011: Enhanced Vector Capabilities
- **Status**: Planned
- **Priority**: P5-Medium-High
- **Summary**: Advanced vector search with additional similarity metrics, hybrid search, and optimized indexes.
- **Timeline**: 10-12 months

#### RFC-0012: Stream Processing Integration
- **Status**: Planned
- **Priority**: P5-Medium
- **Summary**: Native integration with Kafka, Flink, and streaming platforms via CDC and change streams API.
- **Timeline**: 8-10 months

### Priority 6: Security and Compliance

#### RFC-0013: Advanced Encryption and Key Management
- **Status**: Planned
- **Priority**: P6-Medium-High
- **Summary**: Column-level encryption, key management service integration, and hardware acceleration.
- **Timeline**: 10-12 months

## Implementation Roadmap

### Phase 1: Critical Foundations (0-6 months)
- RFC-0001: CMS Migration Tooling
- RFC-0002: Auto Repair Stabilization
- RFC-0003: UCS Production Hardening

### Phase 2: Performance (6-12 months)
- RFC-0004: Query Planning
- RFC-0006: Read/Write Optimizations
- RFC-0007: Schema Designer

### Phase 3: Advanced Features (12-24 months)
- RFC-0005: Tiered Storage
- RFC-0008: Enhanced CQL
- RFC-0009: Kubernetes Operator

### Phase 4: Ecosystem (24+ months)
- RFC-0011: Vector Capabilities
- RFC-0012: Stream Processing
- RFC-0013: Advanced Security

## Contributing

To propose a new RFC:

1. Use the [RFC Template](RFC-TEMPLATE.md)
2. Submit as a pull request to the `evolution-proposals/rfcs` directory
3. Discuss on the dev mailing list
4. Iterate based on community feedback
5. Seek formal approval from PMC

## Related Documents

- [Cassandra Next Generation Proposals](../cassandra-next-generation.md) - Original analysis and proposals
- [Apache Cassandra CEPs](https://cwiki.apache.org/confluence/display/CASSANDRA/CEPs) - Official enhancement proposals
- [Development Process](https://cassandra.apache.org/doc/latest/development/) - Contributing guidelines

## Quick Reference

| RFC | Title | Priority | Complexity | Timeline |
|-----|-------|----------|------------|----------|
| 0001 | CEP-21 Migration | P1-Critical | Medium | 3-6 months |
| 0002 | Auto Repair | P1-Critical | Medium | 4-6 months |
| 0003 | UCS Hardening | P1-Critical | Medium-High | 6-9 months |
| 0004 | Query Planning | P2-High | High | 9-12 months |
| 0005 | Tiered Storage | P2-High | Very High | 12-18 months |
| 0006 | Read/Write Optimization | P2-High | High | 12+ months |
| 0007 | Schema Designer | P3-Medium | Medium | 6-8 months |
| 0008 | Enhanced CQL | P3-Medium | High | 12-15 months |

## Contact

- **Mailing List**: dev@cassandra.apache.org
- **JIRA**: https://issues.apache.org/jira/browse/CASSANDRA
- **Slack**: #cassandra-dev on ASF Slack

---

*Last Updated: 2024-11-15*
*Document Version: 1.0*