# Cassandra Schema Designer Tool - Enhanced Version

An intelligent tool for designing, analyzing, migrating, and optimizing Apache Cassandra schemas based on query patterns and workload characteristics.

## New Features ✨

- **Interactive Wizard**: Step-by-step guided schema design for beginners
- **Migration Planning**: Plan and execute safe schema migrations
- **Dual-Write Support**: Online migrations without downtime
- **Code Generation**: Generate migration code in Java, Python, Bash, or CQL
- **Risk Assessment**: Automated risk analysis for migrations
- **Data Validation**: Verify data consistency during migrations

## Features

- **Query-driven schema generation**: Generate optimal schemas from your application queries
- **Anti-pattern detection**: Identify common Cassandra anti-patterns before production
- **Query validation**: Validate queries against best practices
- **Schema analysis**: Analyze existing schemas for performance issues
- **Workload profiling**: Optimize schemas based on workload characteristics
- **Migration planning**: Plan and execute safe schema migrations
- **Interactive wizard**: Step-by-step guided schema design
- **Dual-write support**: Safe online migrations without downtime
- **Code generation**: Generate migration code in Java, Python, Bash, or CQL

## Quick Start

### Option A: Interactive Wizard (⭐ Recommended for Beginners)

```bash
./bin/cassandra-schema-designer interactive
```

Follow the step-by-step wizard to design your schema. See [INTERACTIVE-GUIDE.md](INTERACTIVE-GUIDE.md) for details.

### Option B: Command-Line (For Advanced Users)

#### 1. Create a query file

Create a file `queries.txt` with your application queries:

```sql
SELECT * FROM users WHERE user_id = ?;
SELECT * FROM orders WHERE user_id = ? AND order_time > ? ORDER BY order_time DESC;
SELECT * FROM products WHERE category = ?;
```

#### 2. Generate a schema

```bash
./bin/cassandra-schema-designer create --queries queries.txt --output schema.cql
```

#### 3. Plan a migration

```bash
./bin/cassandra-schema-designer migrate \
  --from old-schema.cql \
  --to new-schema.cql \
  --output migration.cql
```

## Commands Overview

| Command | Description | Best For |
|---------|-------------|----------|
| `interactive` | Launch guided wizard | Beginners, learning Cassandra |
| `create` | Generate schema from queries | Automated schema generation |
| `analyze` | Detect anti-patterns | Code review, optimization |
| `validate` | Validate queries | CI/CD pipelines |
| `migrate` | Plan schema migrations | Production deployments |

## Detailed Usage

### Interactive Command

Launch the interactive design wizard:

```bash
cassandra-schema-designer interactive
```

The wizard guides you through:
1. Application requirements (QPS, consistency, etc.)
2. Entity definition (tables and columns)
3. Query specification (access patterns)
4. Schema generation and optimization
5. Validation and recommendations

**Example Session:**
```
What type of application is this?
1. Web application
2. Mobile application
3. IoT/Time-series
Choice: 3

Keyspace name: iot_sensors
Expected read QPS: 50000
Expected write QPS: 100000

Entity name: sensor_readings
Define fields: sensor_id, timestamp, temperature, humidity

[... wizard generates optimized time-series schema ...]
```

See [INTERACTIVE-GUIDE.md](INTERACTIVE-GUIDE.md) for a complete walkthrough.

### Create Command

Generate a schema from query patterns:

```bash
cassandra-schema-designer create --queries <file> [--output <file>]
```

Options:
- `--queries <file>`: File containing CQL queries (required)
- `--output <file>`: Output schema file (default: schema.cql)

### Analyze Command

Analyze queries and detect anti-patterns:

```bash
cassandra-schema-designer analyze --queries <file>
```

Options:
- `--queries <file>`: File containing CQL queries (required)

### Validate Command

Validate queries against best practices:

```bash
cassandra-schema-designer validate --queries <file>
```

Options:
- `--queries <file>`: File containing CQL queries (required)

### Migrate Command ⭐ NEW

Plan a migration from one schema to another:

```bash
cassandra-schema-designer migrate \
  --from old-schema.cql \
  --to new-schema.cql \
  --output migration.cql \
  --language cql
```

Options:
- `--from <file>`: Current schema file (required)
- `--to <file>`: Target schema file (required)
- `--output <file>`: Output file (default: migration-plan.txt)
- `--language <lang>`: Output language: cql, java, python, bash (default: cql)

The tool will:
- Compare the two schemas
- Identify changes (added/removed tables, columns)
- Determine optimal migration strategy
- Assess risks and provide mitigations
- Generate migration code
- Create rollback plan

**Example Output:**
```
=== Migration Plan ===

Strategy: ONLINE_MIGRATION
Estimated Duration: 50 minutes
Requires Downtime: NO

Phases:
1. Setup (10 min)
2. Backfill (20 min)
3. Validate (10 min)
4. Cutover (5 min)
5. Cleanup (5 min)

=== Risk Assessment ===

[MEDIUM] Column Removal: 2 columns will be removed
  Mitigation: Verify no queries reference removed columns
```

See [MIGRATION-GUIDE.md](MIGRATION-GUIDE.md) for detailed migration procedures.

## Anti-Pattern Detection

The tool detects common Cassandra anti-patterns:

### Large Partitions
Partitions exceeding 100MB can cause performance issues.

**Recommendations:**
- Add time bucketing to partition key
- Implement partition splitting strategy
- Archive old data to separate table

### Hot Partitions
Uneven partition access causing hotspots.

**Recommendations:**
- Add partition key components for better distribution
- Implement partition bucketing
- Consider random partition key suffix

### Unbounded Collections
Collection columns that can grow unbounded.

**Recommendations:**
- Set collection size limits in application logic
- Use separate table for one-to-many relationships
- Implement data retention policy

### ALLOW FILTERING Overuse
Excessive use of ALLOW FILTERING indicates poor schema design.

**Recommendations:**
- Create materialized views for common query patterns
- Add secondary indexes for frequently queried columns
- Redesign partition keys to support common queries

## Migration Strategies

The tool supports four migration strategies:

1. **Online Migration** (Recommended)
   - No downtime
   - Uses dual-write pattern
   - Best for: Adding columns, non-breaking changes

2. **Blue-Green Deployment**
   - Minimal downtime
   - Two complete environments
   - Best for: Major redesigns

3. **Rolling Upgrade**
   - No downtime
   - Gradual node-by-node
   - Best for: Minor compatible changes

4. **Big Bang**
   - Requires downtime
   - Simple and fast
   - Best for: Small clusters with maintenance windows

## Configuration

Edit `schema-designer.yaml` to customize:
- Analysis thresholds
- Workload profiles
- Default table options
- Replication settings

## Examples

### Example 1: E-Commerce Schema

```bash
# queries.txt
SELECT * FROM users WHERE user_id = ?;
SELECT * FROM users WHERE email = ?;
SELECT * FROM orders WHERE user_id = ? ORDER BY order_time DESC LIMIT 10;
SELECT * FROM products WHERE category = ?;

# Generate schema
./bin/cassandra-schema-designer create --queries queries.txt
```

**Result:**
- Users table with materialized view by email
- Orders table with clustering by time
- Products table with secondary index on category

### Example 2: Time-Series IoT

```bash
# Use interactive wizard for time-series
./bin/cassandra-schema-designer interactive

# Choose: IoT/Time-series
# Result: TWCS compaction, time-bucketed partitions
```

### Example 3: Schema Migration

```bash
# Plan migration
./bin/cassandra-schema-designer migrate \
  --from examples/old-schema.cql \
  --to examples/new-schema.cql \
  --language java \
  --output Migration.java

# Review generated migration code
cat Migration.java

# Execute migration (after review)
javac Migration.java && java Migration
```

## Best Practices

1. **Start with queries**: Design your schema based on how you'll query the data
2. **Use interactive mode**: For learning and exploring options
3. **Review anti-patterns**: Always check the analysis report before deploying
4. **Test migrations**: Use staging environment first
5. **Monitor**: Track partition sizes and query performance
6. **Iterate**: Refine your queries and regenerate as needed

## Advanced Usage

### CI/CD Integration

```yaml
# .github/workflows/schema-check.yml
steps:
  - name: Validate Schema
    run: |
      cassandra-schema-designer analyze --queries app/queries.txt
      cassandra-schema-designer validate --queries app/queries.txt
```

### Automated Migration

```bash
# Generate migration code
cassandra-schema-designer migrate \
  --from production.cql \
  --to staging.cql \
  --language python \
  --output migrate.py

# Run with monitoring
python migrate.py --dry-run
python migrate.py --execute
```

### Custom Workload Profiles

Edit `schema-designer.yaml`:
```yaml
workload:
  expected_qps: 50000
  peak_qps: 150000
  data_pattern: time_series
  retention_days: 30
```

## Troubleshooting

### Tool doesn't start
- Verify `CASSANDRA_HOME` is set
- Check Java is installed (Java 11+ required)

### Schema generation fails
- Ensure queries file is valid CQL
- Check query syntax (must be SELECT statements)
- Verify bind variables use `?`

### Migration plan incomplete
- Provide complete schema files
- Include all CREATE TABLE statements
- Ensure keyspace is specified

### Anti-patterns detected
- Review recommendations carefully
- Consider workload characteristics
- Test with production-like data volumes

## Documentation

- [Interactive Guide](INTERACTIVE-GUIDE.md) - Step-by-step wizard walkthrough
- [Migration Guide](MIGRATION-GUIDE.md) - Complete migration procedures
- [Implementation Notes](IMPLEMENTATION-NOTES.md) - Technical details
- [RFC-0007](../../evolution-proposals/rfcs/RFC-0007-intelligent-schema-designer.md) - Original design

## Contributing

See the [RFC-0007](../../evolution-proposals/rfcs/RFC-0007-intelligent-schema-designer.md) for design details and contribution guidelines.

## License

Licensed under the Apache License, Version 2.0.
