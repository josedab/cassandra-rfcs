# Schema Migration Guide

This guide explains how to safely migrate Cassandra schemas using the Schema Designer Tool.

## Migration Strategies

The tool supports four migration strategies:

### 1. Online Migration (Recommended)
- **No downtime required**
- Uses dual-write pattern
- Best for: Adding columns, creating new tables, non-breaking changes

```bash
cassandra-schema-designer migrate \
  --from old-schema.cql \
  --to new-schema.cql \
  --output migration.cql
```

### 2. Blue-Green Deployment
- **Minimal downtime**
- Maintains two complete environments
- Best for: Major schema redesigns, testing in production

### 3. Rolling Upgrade
- **No downtime**
- Gradual node-by-node migration
- Best for: Minor changes, compatible schema updates

### 4. Big Bang Migration
- **Requires downtime**
- Simple and straightforward
- Best for: Small clusters, maintenance windows available

## Migration Phases

A typical online migration includes these phases:

1. **Setup**: Create new tables, enable dual writes
2. **Backfill**: Copy existing data to new schema
3. **Validate**: Verify data consistency
4. **Cutover**: Switch reads to new schema
5. **Cleanup**: Remove old schema

## Example: Adding a Column

### Step 1: Plan the Migration

```bash
cassandra-schema-designer migrate \
  --from current-schema.cql \
  --to new-schema.cql \
  --output migration-plan.txt \
  --language cql
```

Output:
```
=== Migration Plan ===

Strategy: ONLINE_MIGRATION
Estimated Duration: 50 minutes
Requires Downtime: NO

Phases:
1. Setup
   Duration: 10 minutes
   Downtime: NO
   Steps: 3

2. Backfill
   Duration: 20 minutes
   Downtime: NO
   Steps: 1

3. Validate
   Duration: 10 minutes
   Downtime: NO
   Steps: 1

4. Cutover
   Duration: 5 minutes
   Downtime: NO
   Steps: 2

5. Cleanup
   Duration: 5 minutes
   Downtime: NO
   Steps: 1
```

### Step 2: Review Generated Code

The tool generates migration scripts:

**CQL Script** (`migration-plan.txt`):
```sql
-- Phase: Setup
-- Create new tables and enable dual writes

ALTER TABLE users ADD phone TEXT;
ALTER TABLE users ADD updated_at TIMESTAMP;

-- Phase: Backfill
-- Copy existing data to new schema
-- (Manual intervention required for data migration)

-- Phase: Validate
-- Verify data consistency
-- (Use validation tools)

-- Phase: Cutover
-- Switch reads to new schema
-- (Update application configuration)

-- Phase: Cleanup
-- Remove old schema
-- (No cleanup needed for column additions)
```

**Java Code** (`--language java`):
```java
import com.datastax.driver.core.*;

public class SchemaMigration {
    private final Session session;

    public void migrate() throws Exception {
        // Phase: Setup
        session.execute("ALTER TABLE users ADD phone TEXT");
        session.execute("ALTER TABLE users ADD updated_at TIMESTAMP");

        // Continue with other phases...
    }
}
```

### Step 3: Execute Migration

Execute the generated migration carefully:

```bash
# 1. Backup current data
nodetool snapshot

# 2. Apply schema changes
cqlsh -f migration-plan.txt

# 3. Monitor the migration
nodetool describecluster
```

## Dual-Write Pattern

For breaking changes, use dual writes:

```java
DualWriteManager dualWrite = new DualWriteManager();

// Enable dual writes
dualWrite.enableDualWrite("users", DualWriteConfig.defaultConfig());

// Execute writes
dualWrite.executeWrite("users", new WriteOperation() {
    public boolean executeOnOld() {
        // Write to old schema
        return true;
    }

    public boolean executeOnNew() {
        // Write to new schema
        return true;
    }
});

// Monitor progress
DualWriteStatistics stats = dualWrite.getStatistics("users");
System.out.println(stats);

// When migration complete
dualWrite.disableDualWrite("users");
```

## Data Validation

Validate data consistency after migration:

```java
DataValidator validator = new DataValidator();

ValidationReport report = validator.validateMigration(
    oldSchema,
    newSchema,
    migrationPlan
);

System.out.println(report);

if (report.hasErrors()) {
    // Handle errors
    System.err.println("Validation failed!");
}
```

## Risk Assessment

The tool automatically assesses migration risks:

```
=== Risk Assessment ===

[HIGH] Breaking Changes: Schema contains breaking changes
  Mitigation: Coordinate deployment with application updates

[MEDIUM] Column Removal: 2 columns will be removed
  Mitigation: Verify no queries reference removed columns

[LOW] New Tables: 3 new tables will be created
  Mitigation: Ensure adequate cluster capacity
```

## Best Practices

### 1. Always Backup First
```bash
nodetool snapshot my_keyspace
```

### 2. Test in Staging
Run the complete migration in a staging environment first.

### 3. Monitor Performance
```bash
# Watch query latency
nodetool tablestats my_keyspace.my_table

# Monitor compactions
watch nodetool compactionstats
```

### 4. Plan for Rollback
Keep the rollback plan ready:

```sql
-- Rollback script (auto-generated)
DROP TABLE new_table;
ALTER TABLE users DROP phone;
```

### 5. Gradual Cutover
Switch traffic gradually:
- 10% of users
- Monitor for 1 hour
- 50% of users
- Monitor for 1 hour
- 100% cutover

## Common Scenarios

### Adding a Column
- **Strategy**: Online Migration
- **Downtime**: No
- **Steps**: ALTER TABLE ADD

### Removing a Column
- **Strategy**: Rolling Upgrade
- **Downtime**: No
- **Steps**:
  1. Stop writing to column
  2. Deploy app update
  3. ALTER TABLE DROP

### Changing Primary Key
- **Strategy**: Blue-Green or Big Bang
- **Downtime**: Possible
- **Steps**:
  1. Create new table
  2. Dual-write period
  3. Backfill data
  4. Cutover
  5. Drop old table

### Adding Time Bucketing
- **Strategy**: Online Migration
- **Downtime**: No
- **Steps**:
  1. Create bucketed table
  2. Dual-write
  3. Backfill
  4. Cutover

## Troubleshooting

### High Error Rate in Dual Writes
```
WARNING: High error rate detected for users
```
**Solution**: Check new schema compatibility, review error logs

### Migration Taking Too Long
**Solution**:
- Increase compaction throughput
- Add more nodes temporarily
- Use parallel backfill

### Data Inconsistencies
**Solution**:
- Pause migration
- Run validation
- Compare sample data
- Fix inconsistencies
- Resume

## Advanced Features

### Custom Migration Code

Generate migration code in different languages:

```bash
# Java
cassandra-schema-designer migrate --from old.cql --to new.cql --language java

# Python
cassandra-schema-designer migrate --from old.cql --to new.cql --language python

# Bash
cassandra-schema-designer migrate --from old.cql --to new.cql --language bash
```

### Migration Automation

Integrate with CI/CD:

```yaml
# .github/workflows/schema-migration.yml
steps:
  - name: Plan Migration
    run: |
      cassandra-schema-designer migrate \
        --from production-schema.cql \
        --to ${{ github.sha }}-schema.cql \
        --output migration-${{ github.sha }}.cql

  - name: Review Migration
    run: cat migration-${{ github.sha }}.cql

  - name: Apply Migration (manual approval required)
    if: github.event_name == 'release'
    run: cqlsh -f migration-${{ github.sha }}.cql
```

## Further Reading

- [Cassandra Data Modeling](https://cassandra.apache.org/doc/latest/data-modeling/)
- [Schema Design Best Practices](../README.md)
- [Anti-Pattern Detection](../IMPLEMENTATION-NOTES.md)
