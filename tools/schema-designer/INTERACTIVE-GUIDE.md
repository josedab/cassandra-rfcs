# Interactive Schema Designer Guide

The Interactive Schema Designer provides a step-by-step wizard for designing Cassandra schemas without needing to know CQL or data modeling concepts upfront.

## Getting Started

Launch the interactive wizard:

```bash
cassandra-schema-designer interactive
```

## Wizard Steps

### Step 1: Basic Information

The wizard asks about your application:

```
=== Step 1: Basic Information ===

What type of application is this?
1. Web application
2. Mobile application
3. IoT/Time-series
4. Analytics
5. Other
Choice [1-5]: 1

Keyspace name: ecommerce

Expected read QPS (queries per second): 10000
Expected write QPS: 2000
```

**Tips**:
- Choose "IoT/Time-series" for time-series data to automatically use TWCS
- Higher QPS values result in more aggressive optimization recommendations
- Keyspace name should match your application domain

### Step 2: Define Entities

Define the main entities in your data model:

```
=== Step 2: Define Entities ===

Let's define the main entities in your data model.
Examples: users, orders, products, sessions, etc.

Entity name (or 'done' to continue): users
Define fields for users (comma-separated):
Example: id, name, email, created_at
> user_id, email, name, phone, created_at

✓ Entity 'users' added

Entity name (or 'done' to continue): orders
Define fields for orders (comma-separated):
> order_id, user_id, order_time, total_amount, status

✓ Entity 'orders' added

Entity name (or 'done' to continue): done

✓ 2 entities defined
```

**Tips**:
- Think in terms of nouns from your domain
- Include all fields you'll need to query or display
- Don't worry about relationships yet - focus on entities

### Step 3: Define Queries

Specify how you'll access the data:

```
=== Step 3: Define Queries ===

Now let's define your application queries.
You can enter queries manually or load from a file.

1. Enter queries manually
2. Load from file
Choice [1-2]: 1

Enter your CQL SELECT queries (one per line).
Type 'done' when finished.

> SELECT * FROM users WHERE user_id = ?;
  ✓ Query added
> SELECT * FROM users WHERE email = ?;
  ✓ Query added
> SELECT * FROM orders WHERE user_id = ? AND order_time > ?;
  ✓ Query added
> done

✓ 3 queries defined
```

**Tips**:
- Use ? for bind variables
- Focus on queries your application will actually run
- Include the most frequent queries first
- Don't worry about optimal schema yet - just express what you need

**Alternative - Load from File**:
```
Choice [1-2]: 2

Query file path: examples/example-queries.txt
✓ Loaded 12 queries from file

✓ 12 queries defined
```

### Step 4: Additional Requirements

Specify non-functional requirements:

```
=== Step 4: Additional Requirements ===

Data retention period (days, 0 for unlimited): 90

Consistency requirements:
1. Eventual consistency (best performance)
2. Strong consistency (quorum reads/writes)
Choice [1-2]: 1

✓ Requirements specified
```

**Tips**:
- Set retention < 90 days for time-series data to enable automatic cleanup
- Choose eventual consistency unless you have strong consistency requirements
- Strong consistency reduces availability during failures

### Step 5: Schema Generation

The wizard analyzes your inputs and generates an optimal schema:

```
=== Step 5: Schema Generation ===

Analyzing queries and generating schema...
✓ Identified 3 access patterns
✓ Generated schema with 2 tables

--- Schema Preview ---
CREATE KEYSPACE IF NOT EXISTS ecommerce
WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3};

CREATE TABLE IF NOT EXISTS ecommerce.users (
    user_id UUID,
    email TEXT,
    name TEXT,
    phone TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id)
) WITH compression = {'class': 'LZ4Compressor'}
  AND compaction = {'class': 'UnifiedCompactionStrategy'};

CREATE MATERIALIZED VIEW IF NOT EXISTS ecommerce.users_by_email AS
    SELECT * FROM ecommerce.users
    WHERE email IS NOT NULL AND user_id IS NOT NULL
    PRIMARY KEY (email, user_id);

CREATE TABLE IF NOT EXISTS ecommerce.orders (
    user_id UUID,
    order_time TIMESTAMP,
    order_id UUID,
    total_amount DECIMAL,
    status TEXT,
    PRIMARY KEY (user_id, order_time, order_id)
) WITH CLUSTERING ORDER BY (order_time DESC, order_id ASC);
---------------------

Accept this schema? (y/n): y
```

**Tips**:
- Review the PRIMARY KEY carefully - this determines query patterns
- Check CLUSTERING ORDER matches your query ORDER BY clauses
- Note materialized views for secondary access patterns
- Answer 'n' to regenerate (requires modifying queries)

### Step 6: Validation & Recommendations

The wizard analyzes the generated schema for issues:

```
=== Step 6: Validation & Recommendations ===

Analyzing schema for potential issues...

=== Schema Analysis Report ===

✓ No anti-patterns detected

Recommendations:
  • Consider using TimeWindowCompactionStrategy for time-series data
  • Monitor partition sizes in the orders table

Save schema to file? (y/n): y
Output file path: my-schema.cql

✓ Schema saved to: my-schema.cql

╔══════════════════════════════════════════════════╗
║   Schema Design Complete!                        ║
╚══════════════════════════════════════════════════╝
```

## Example Session

Here's a complete example session:

```bash
$ cassandra-schema-designer interactive

╔══════════════════════════════════════════════════╗
║   Cassandra Interactive Schema Designer         ║
║   Query-First Schema Design Made Easy           ║
╚══════════════════════════════════════════════════╝

This wizard will guide you through designing an optimal
Cassandra schema based on your queries and requirements.

=== Step 1: Basic Information ===

What type of application is this?
1. Web application
2. Mobile application
3. IoT/Time-series
4. Analytics
5. Other
Choice [1-5]: 3

Keyspace name: iot_sensors

Expected read QPS (queries per second): 50000
Expected write QPS: 100000

✓ Basic information gathered

=== Step 2: Define Entities ===

Entity name: sensor_readings
Define fields: sensor_id, timestamp, temperature, humidity, battery_level
✓ Entity 'sensor_readings' added

Entity name: done
✓ 1 entities defined

=== Step 3: Define Queries ===

1. Enter queries manually
2. Load from file
Choice: 1

> SELECT * FROM sensor_readings WHERE sensor_id = ? AND timestamp > ?;
> SELECT temperature, humidity FROM sensor_readings WHERE sensor_id = ? AND timestamp >= ? AND timestamp <= ?;
> done

✓ 2 queries defined

=== Step 4: Additional Requirements ===

Data retention period (days): 30
Consistency requirements: 1
✓ Requirements specified

=== Step 5: Schema Generation ===

✓ Generated schema with 1 tables

CREATE TABLE IF NOT EXISTS iot_sensors.sensor_readings (
    sensor_id UUID,
    timestamp TIMESTAMP,
    temperature DECIMAL,
    humidity DECIMAL,
    battery_level INT,
    PRIMARY KEY (sensor_id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_size': '1',
                    'compaction_window_unit': 'DAYS'};

Accept? y

Save schema? y
Output: iot-schema.cql
✓ Schema saved!
```

## Common Patterns

### User Authentication System

```
Entities: users, sessions
Queries:
- SELECT * FROM users WHERE user_id = ?;
- SELECT * FROM users WHERE email = ?;
- SELECT * FROM sessions WHERE session_id = ?;
- SELECT * FROM sessions WHERE user_id = ? AND created_at > ?;
```

Result: Users table with MV by email, sessions table with clustering by time

### E-Commerce Catalog

```
Entities: products, categories, orders
Queries:
- SELECT * FROM products WHERE product_id = ?;
- SELECT * FROM products WHERE category = ?;
- SELECT * FROM orders WHERE user_id = ? ORDER BY order_time DESC;
```

Result: Products with secondary index on category, orders clustered by time

### Time-Series Metrics

```
Entities: metrics
Queries:
- SELECT * FROM metrics WHERE metric_name = ? AND timestamp > ? AND timestamp < ?;
- SELECT avg(value) FROM metrics WHERE metric_name = ? AND timestamp > ?;
```

Result: Time-bucketed table with TWCS compaction

## Tips for Best Results

### 1. Be Specific with Queries
❌ Bad: "I need to get user data"
✓ Good: `SELECT email, name FROM users WHERE user_id = ?;`

### 2. Include All Access Patterns
List every way you'll query the data, even if similar:
```
SELECT * FROM orders WHERE user_id = ?;
SELECT * FROM orders WHERE user_id = ? AND order_time > ?;
SELECT * FROM orders WHERE user_id = ? ORDER BY order_time DESC LIMIT 10;
```

### 3. Consider Future Needs
Think about queries you might need in 6 months:
- Admin queries
- Analytics queries
- Reporting needs

### 4. Don't Over-Normalize
Unlike relational databases, denormalization is encouraged in Cassandra:
- Duplicate data across tables
- Embed related data
- Optimize for read patterns

### 5. Review Generated Schema
Always review:
- Are partition keys distributed evenly?
- Are clustering columns ordered correctly?
- Do materialized views make sense?
- Is compaction strategy appropriate?

## Troubleshooting

### "No patterns identified"
**Cause**: Queries too generic or malformed
**Solution**: Use specific SELECT queries with WHERE clauses

### "Too many materialized views"
**Cause**: Too many different access patterns
**Solution**: Consolidate similar queries, consider application-level filtering

### "Large partition warning"
**Cause**: Unbounded clustering columns
**Solution**: Add time bucketing to partition key

## Next Steps

After generating your schema:

1. **Review**: Carefully review the generated CQL
2. **Test**: Load test data and run queries
3. **Monitor**: Check partition sizes and query performance
4. **Iterate**: Refine based on real usage patterns

For more help:
```bash
cassandra-schema-designer help
cassandra-schema-designer analyze --queries my-queries.txt
```
