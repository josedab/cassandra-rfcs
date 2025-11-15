# Cassandra Schema Designer Tool

An intelligent tool for designing, analyzing, and optimizing Apache Cassandra schemas based on query patterns and workload characteristics.

## Features

- **Query-driven schema generation**: Generate optimal schemas from your application queries
- **Anti-pattern detection**: Identify common Cassandra anti-patterns before production
- **Query validation**: Validate queries against best practices
- **Schema analysis**: Analyze existing schemas for performance issues
- **Workload profiling**: Optimize schemas based on workload characteristics

## Installation

The schema designer tool is included with Apache Cassandra. To use it:

```bash
cd $CASSANDRA_HOME/tools/schema-designer
chmod +x bin/cassandra-schema-designer
```

## Quick Start

### 1. Create a query file

Create a file `queries.txt` with your application queries:

```sql
SELECT * FROM users WHERE user_id = ?;
SELECT * FROM orders WHERE user_id = ? AND order_time > ? ORDER BY order_time DESC;
SELECT * FROM products WHERE category = ?;
```

### 2. Generate a schema

```bash
./bin/cassandra-schema-designer create --queries queries.txt --output schema.cql
```

This will:
- Analyze your queries
- Extract access patterns
- Generate an optimized schema
- Detect potential anti-patterns
- Output CQL to `schema.cql`

### 3. Analyze queries

```bash
./bin/cassandra-schema-designer analyze --queries queries.txt
```

This will:
- Analyze query patterns
- Detect anti-patterns
- Provide recommendations

### 4. Validate queries

```bash
./bin/cassandra-schema-designer validate --queries queries.txt
```

This will:
- Validate each query
- Check for common issues
- Report warnings and errors

## Usage

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

## Configuration

Edit `schema-designer.yaml` to customize:
- Analysis thresholds
- Workload profiles
- Default table options
- Replication settings

## Example Workflow

1. **Gather queries** from your application:
```bash
# queries.txt
SELECT * FROM users WHERE user_id = ?;
SELECT * FROM users WHERE email = ?;
SELECT name, created_at FROM users WHERE user_id = ?;
SELECT * FROM orders WHERE user_id = ? AND order_time > ?;
```

2. **Generate schema**:
```bash
./bin/cassandra-schema-designer create --queries queries.txt --output schema.cql
```

3. **Review output** (`schema.cql`):
```sql
CREATE KEYSPACE IF NOT EXISTS my_keyspace
WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3};

CREATE TABLE IF NOT EXISTS my_keyspace.users (
    user_id UUID,
    email TEXT,
    name TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id)
) WITH compression = {'class': 'LZ4Compressor'}
  AND compaction = {'class': 'UnifiedCompactionStrategy'};

CREATE MATERIALIZED VIEW IF NOT EXISTS my_keyspace.users_by_email AS
    SELECT * FROM my_keyspace.users
    WHERE email IS NOT NULL AND user_id IS NOT NULL
    PRIMARY KEY (email, user_id);
```

4. **Apply to cluster**:
```bash
cqlsh -f schema.cql
```

## Best Practices

1. **Start with queries**: Design your schema based on how you'll query the data
2. **Review anti-patterns**: Always check the analysis report before deploying
3. **Test with realistic data**: Validate partition sizes with production-like data volumes
4. **Iterate**: Refine your queries and regenerate the schema as needed

## Troubleshooting

### Tool doesn't start
- Verify `CASSANDRA_HOME` is set
- Check Java is installed (Java 11+ required)

### Schema generation fails
- Ensure queries file is valid CQL
- Check query syntax
- Verify queries use SELECT statements

### Anti-patterns detected
- Review recommendations carefully
- Consider workload characteristics
- Test with production data volumes

## Contributing

See the [RFC-0007](../../evolution-proposals/rfcs/RFC-0007-intelligent-schema-designer.md) for design details and contribution guidelines.

## License

Licensed under the Apache License, Version 2.0.
