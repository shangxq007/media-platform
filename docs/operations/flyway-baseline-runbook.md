---
status: runbook
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# Flyway Baseline Runbook

## Overview

This runbook documents the Flyway baseline strategy for the media-platform application. The baseline consolidates all pre-launch migrations into a single V1 migration (`V1__init_full_schema.sql`) to simplify database initialization for new environments.

## Purpose

### Why a Baseline?

The media-platform uses a **single V1 baseline migration** as the canonical schema definition:

- **V1__init_full_schema.sql** (2189 lines) — Complete schema including all tables, indexes, and constraints
- **V2__add_outbox_lease_columns.sql** — Additive migration for outbox lease columns
- **V6__create_project_import_metadata.sql** — Project import metadata table (identity-access-module)
- **V11__create_product_layer_tables.sql** — Product layer tables (product-layer-module)

Archived migrations (V2–V5) have been moved to `docs/archive/prelaunch-migrations/` and are no longer executed.

### Benefits

- New databases are initialized from a single source of truth
- No dependency on historical incremental migrations
- Consistent schema across dev, test, and staging environments
- Simplified CI/CD pipeline (one migration to validate)

## Baseline Consolidation (2026-06-18)

### Consolidation Summary

All pre-launch migrations have been consolidated into the single V1 baseline:

**Before Consolidation:**
- `V1__init_full_schema.sql` (2189 lines) - Core schema
- `V2__add_outbox_lease_columns.sql` - Outbox lease columns
- `V6__create_project_import_metadata.sql` - Project import metadata (identity-access-module)
- `V11__create_product_layer_tables.sql` - Product layer tables (product-layer-module)

**After Consolidation:**
- `V1__init_full_schema.sql` (2336 lines) - Complete schema with all tables, indexes, and constraints

### What Changed

1. **Outbox Lease Columns**: Added `locked_at`, `locked_by`, `max_retries` columns to `outbox_events` table
2. **Project Import Metadata**: Added `project_import_metadata` table for imported project shells
3. **Product Layer Tables**: Added `timeline_template`, `render_preset`, `asset_library`, `render_history`, `ai_suggestion` tables
4. **PostgreSQL Syntax Fix**: Fixed `double` type to `double precision` for PostgreSQL compatibility

### Why This Is Allowed

This consolidation is allowed because:

1. **Pre-production project**: No non-resettable production database has applied old migration history
2. **Greenfield deployment**: All environments can be reset to clean state
3. **Single source of truth**: V1 now represents the complete, tested PostgreSQL schema

### Validation Results

| Environment | Status | Flyway | Tables Created |
|-------------|--------|--------|----------------|
| dev-postgres,preview | ✅ Started | V1 applied | All tables created |
| prod,safe-mode | ✅ Started | Schema up to date | All tables present |

### Important Notes

- **Do NOT apply this consolidation to production databases with existing history**
- **Non-production databases should be reset** to get clean schema
- **Future migrations** should be additive (V2, V3, etc.) and never modify V1

---

## Schema Management Policy

See [Schema Management Policy](../engineering/schema-management-policy.md) for the full policy document.

### Key Rules

1. **Central Flyway is the only DDL source of truth** for production and test databases
2. **All schema changes must be additive migrations** (V7, V8, etc.)
3. **Archived migrations remain for historical reference** only
4. **Migration history must never be rewritten** after first production deploy

## Baseline Configuration

### FlywayConfiguration Bean

The application uses a custom `FlywayConfiguration` bean (not Spring Boot auto-config):

```java
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = false)
public class FlywayConfiguration {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)  // Key: allows baseline on existing databases
                .load();
        flyway.migrate();
        return flyway;
    }
}
```

### Key Configuration

| Property | Value | Description |
|----------|-------|-------------|
| `spring.flyway.enabled` | `true` | Enable Flyway migrations |
| `spring.flyway.locations` | `classpath:db/migration` | Migration file location |
| `spring.flyway.baseline-on-migrate` | `true` | Auto-baseline existing databases |
| `spring.flyway.url` | `${SPRING_FLYWAY_URL}` | Database connection URL |
| `spring.flyway.user` | `${SPRING_FLYWAY_USER}` | Database username |
| `spring.flyway.password` | `${SPRING_FLYWAY_PASSWORD}` | Database password |

## Steps to Create Baseline

### For New Databases

No manual baseline creation needed. Flyway runs V1 migration automatically on first startup:

```bash
# Start with fresh PostgreSQL
docker rm -f media-platform-postgres 2>/dev/null || true
docker run --name media-platform-postgres \
  -e POSTGRES_DB=media_platform \
  -e POSTGRES_USER=media_platform \
  -e POSTGRES_PASSWORD=media_platform \
  -p 5432:5432 \
  -d postgres:15-alpine

# Start application (Flyway runs V1 automatically)
SPRING_PROFILES_ACTIVE=dev-postgres,preview \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_DATASOURCE_USERNAME=media_platform \
SPRING_DATASOURCE_PASSWORD=media_platform \
./gradlew :platform-app:bootRun
```

### For Existing Databases (Pre-Baseline)

If you have a database with tables created outside Flyway (e.g., manual DDL), use `baselineOnMigrate`:

```bash
# Option 1: Let application handle it (recommended)
# baselineOnMigrate=true will create baseline at V1 automatically
SPRING_PROFILES_ACTIVE=dev-postgres,preview ./gradlew :platform-app:bootRun

# Option 2: Manual baseline using Flyway CLI
# Install Flyway CLI first
flyway -url=jdbc:postgresql://localhost:5432/media_platform \
       -user=media_platform \
       -password=media_platform \
       baseline -baselineVersion=1
```

### For Test Databases

Test profile uses `schema.sql` directly (Flyway disabled):

```yaml
# application-test.yml
spring:
  flyway:
    enabled: false
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

**Important**: `schema.sql` must stay synchronized with V1 baseline. Any schema changes to V1 must be reflected in `schema.sql`.

## Validation Commands

### Verify Flyway Migration Status

```bash
# Check migration history table
docker exec media-platform-postgres psql -U media_platform -d media_platform \
  -c "SELECT installed_rank, version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Expected output:
```
 installed_rank | version |        description        | type | success 
----------------+---------+---------------------------+------+---------
              1 | 1       | init full schema          | SQL  | t
```

**Note:** As of 2026-06-18, V2 (outbox lease columns) has been consolidated into V1. Only V1 should appear in new databases.

### Verify Table Count

```bash
# Count tables in public schema
docker exec media-platform-postgres psql -U media_platform -d media_platform \
  -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"
```

Expected: 30+ tables

### Verify Specific Tables

```bash
# Check core tables exist
docker exec media-platform-postgres psql -U media_platform -d media_platform \
  -c "\dt render_job"
docker exec media-platform-postgres psql -U media_platform -d media_platform \
  -c "\dt outbox_events"
docker exec media-platform-postgres psql -U media_platform -d media_platform \
  -c "\dt artifact"
```

### Verify Schema Integrity

```bash
# Check column count for key tables
docker exec media-platform-postgres psql -U media_platform -d media_platform \
  -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'outbox_events' ORDER BY ordinal_position;"
```

### Check Flyway Configuration at Runtime

```bash
# Verify Flyway bean is loaded (check logs)
# Look for: "Running Flyway migration..." and "Flyway migration completed"

# Verify flyway.enabled property
curl -s http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("application")) | .properties["spring.flyway.enabled"]'
```

## Adding New Migrations

### Naming Convention

```
V{number}__{description}.sql
```

Examples:
- `V7__add_user_preferences_table.sql`
- `V8__add_render_job_priority_column.sql`
- `V9__create_notification_settings.sql`

### Migration File Location

```
platform-app/src/main/resources/db/migration/
├── V1__init_full_schema.sql
├── V7__your_new_migration.sql  # Add here
```

**Note:** V2 (outbox lease columns) has been consolidated into V1. Only V1 and future additive migrations should exist.

### Best Practices

1. **Always additive** — Use `CREATE TABLE`, `ALTER TABLE ADD COLUMN`, `CREATE INDEX`
2. **Never destructive** — Avoid `DROP TABLE`, `DROP COLUMN` unless explicitly required
3. **Idempotent where possible** — Use `IF NOT EXISTS`, `IF EXISTS` clauses
4. **Test locally first** — Run against fresh database to verify
5. **Update schema.sql** — If modifying core tables, update `schema.sql` for test profile

### Example Migration

```sql
-- V7__add_user_preferences_table.sql
CREATE TABLE IF NOT EXISTS user_preferences (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    preference_key VARCHAR(255) NOT NULL,
    preference_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, preference_key)
);

CREATE INDEX ix_user_preferences_user_id ON user_preferences(user_id);
```

## Warning: Production Databases

> ⚠️ **CRITICAL WARNING**

**Migration history must NEVER be rewritten after first production deploy.**

### Prohibited Actions

1. **Do NOT modify V1__init_full_schema.sql** after production deployment
2. **Do NOT delete or rename existing migrations** after production deployment
3. **Do NOT use `flyway repair` to alter history** without explicit approval
4. **Do NOT use `flyway clean`** on production databases (destroys all data)

### Required Actions

1. **All changes via new additive migrations** (V7, V8, V9, etc.)
2. **Test migrations against staging** before production deployment
3. **Backup database before migration** in production
4. **Verify migration success** using validation commands above

### Rollback Strategy

Flyway does not support automatic rollback. For production issues:

1. **Stop application** to prevent further writes
2. **Restore from backup** if migration caused data loss
3. **Manual rollback script** if available (create alongside migration)
4. **Contact DBA** for complex schema issues

## Troubleshooting

### Flyway Not Running

**Symptom**: Tables not created, "relation does not exist" errors.

**Cause**: Flyway disabled or configuration missing.

**Fix**:
- Verify `spring.flyway.enabled: true` in profile
- Check `FlywayConfiguration` bean is loaded (see logs)
- Verify datasource connection is valid

### Baseline Already Exists Error

**Symptom**:
```
Found non-empty schema(s) "public" without schema history table!
```

**Cause**: Database has existing tables but no Flyway history.

**Fix**:
```bash
# Option 1: Let baselineOnMigrate handle it (should work automatically)
# Option 2: Manual baseline
flyway -url=jdbc:postgresql://localhost:5432/media_platform \
       -user=media_platform \
       -password=media_platform \
       baseline -baselineVersion=1
```

### Migration Checksum Mismatch

**Symptom**:
```
Migration checksum mismatch for migration version 1
```

**Cause**: V1 migration file was modified after it was applied.

**Fix**:
```bash
# For development only (NEVER in production)
flyway -url=jdbc:postgresql://localhost:5432/media_platform \
       -user=media_platform \
       -password=media_platform \
       repair
```

### Out-of-Order Migrations

**Symptom**:
```
Validate failed: Migrations have failed migration
```

**Cause**: Migration files added out of sequence.

**Fix**:
- Ensure migration numbers are sequential
- If needed, enable out-of-order: `spring.flyway.out-of-order=true`

### Test Database Issues

**Symptom**: Tests fail with schema errors when Flyway is enabled.

**Cause**: PostgreSQL-specific SQL may have syntax issues.

**Fix**: Disable Flyway in test profile and use `schema.sql`:
```yaml
# application-test.yml
spring:
  flyway:
    enabled: false
```

## References

- [FlywayConfiguration.java](../../platform-app/src/main/java/com/example/platform/FlywayConfiguration.java)
- [V1__init_full_schema.sql](../../platform-app/src/main/resources/db/migration/V1__init_full_schema.sql)
- [Schema Management Policy](../engineering/schema-management-policy.md)
- [Dev-Postgres Configuration](../../platform-app/src/main/resources/application-dev-postgres.yml)
- [Safe-Mode Configuration](../../platform-app/src/main/resources/application-safe-mode.yml)
