# Visual Diagrams for Apache Cassandra Blog Series

This directory contains Mermaid diagrams that can be rendered in Markdown viewers (GitHub, GitLab, modern Markdown editors).

## Diagrams by Blog Post

### Part 1: Architecture Overview
- `token-ring.mmd` - Consistent hashing and token distribution
- `storage-engine-flow.mmd` - Write path from memtable to SSTable
- `gossip-protocol.mmd` - Three-phase gossip exchange
- `replication-strategy.mmd` - NetworkTopologyStrategy placement

### Part 2: Storage Engine
- `compaction-comparison.mmd` - Visual comparison of STCS, LCS, TWCS, UCS
- `sstable-formats.mmd` - BIG vs BTI format structures
- `write-amplification.mmd` - Comparative write amplification

### Part 3: Distributed Systems
- `phi-accrual.mmd` - Failure detector suspicion over time
- `quorum-math.mmd` - R+W>RF visualization
- `paxos-comparison.mmd` - V1 vs V2 round-trip reduction
- `multidc-write.mmd` - Cross-datacenter write coordination

### Part 4: Query Processing
- `read-path.mmd` - Complete read execution flowchart
- `write-path.mmd` - Write execution with hints
- `sai-architecture.mmd` - Storage-attached index structure
- `token-aware-routing.mmd` - Driver optimization

### Part 5: Operational Features
- `auto-repair.mmd` - CEP-37 scheduler workflow
- `backup-timeline.mmd` - Snapshot and backup schedule
- `node-operations.mmd` - Add/remove/replace flows

### Part 6: Schema Design
- `partition-design.mmd` - Good vs bad partition examples
- `denormalization.mmd` - Table relationships
- `time-series-bucketing.mmd` - Partition boundaries over time

## Rendering

These diagrams use Mermaid syntax and render automatically on:
- GitHub/GitLab
- VS Code with Mermaid extension
- Most modern Markdown previewer

To render locally:
```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i diagram.mmd -o diagram.png