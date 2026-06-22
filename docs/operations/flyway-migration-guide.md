# Flyway Migration Guide

> **Last Updated:** 2026-06-22
> **Constraint:** Do NOT modify the V1 baseline.

## Current Strategy

The project uses a **single consolidated baseline** for database schema:

| Attribute | Value |
|-----------|-------|
| Migration file | `platform-app/src/main/resources/db/migration/V1__init_full_schema.sql` |
| Lines | 2,339 |
| Tables | 133 |
| Engine | PostgreSQL 16 |

The V1 baseline contains the complete schema: core infrastructure, identity, commerce, billing, entitlement, render, timeline, analytics, and all other domain tables.

## Rules

### Do NOT

- **Modify `V1__init_full_schema.sql`** — this is the immutable baseline
- **Rename or delete V1** — Flyway checksum validation will fail
- **Add migrations to V1** — always create new versioned files
- **Use `flyway.repair` in production** — only for local dev
- **Create repeatable migrations (`R__`)** — use versioned migrations only

### DO

- Create new migrations as `V2__<description>.sql`, `V3__<description>.sql`, etc.
- Use descriptive names: `V2__add_quota_persistence_tables.sql`
- Keep migrations additive (CREATE TABLE, ADD COLUMN, CREATE INDEX)
- Test migrations against a copy of production data before deploying
- Include rollback notes in migration comments (Flyway does not support automatic rollback)

## Adding a New Migration

### Step 1: Create the Migration File

```bash
# Naming convention: V<N>__<description>.sql
# Example: V2__add_quota_persistence_tables.sql
touch platform-app/src/main/resources/db/migration/V2__add_quota_persistence_tables.sql
```

### Step 2: Write the Migration

```sql
-- V2: Add quota persistence tables
-- These tables replace the in-memory ConcurrentHashMap storage in QuotaService

CREATE TABLE IF NOT EXISTS quota_bucket (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    feature_code VARCHAR(128) NOT NULL,
    "limit" BIGINT NOT NULL,
    period VARCHAR(32) NOT NULL,
    current_usage BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quota_bucket_tenant ON quota_bucket(tenant_id);
CREATE INDEX idx_quota_bucket_feature ON quota_bucket(tenant_id, feature_code);
```

### Step 3: Test Locally

```bash
# Start PostgreSQL
docker compose up -d db

# Run Flyway migration
./gradlew :platform-app:bootRun --args='--spring.profiles.active=dev-postgres'

# Verify tables exist
docker compose exec db psql -U platform -d platform -c '\dt quota*'
```

### Step 4: Run Integration Tests

```bash
./gradlew :platform-app:test --tests '*FlywaySchemaIntegrationTest*'
./gradlew test
```

### Step 5: Commit

```bash
git add platform-app/src/main/resources/db/migration/V2__add_quota_persistence_tables.sql
git commit -m "feat(db): add quota persistence tables (V2)"
```

## Naming Conventions

| Pattern | Purpose | Example |
|---------|---------|---------|
| `V<N>__<description>.sql` | Versioned migration | `V2__add_quota_tables.sql` |
| Description format | snake_case, verb + noun | `add_quota_tables`, `create_index_render_job` |
| No spaces | Use underscores | `V2__add_quota_tables` not `V2__add quota tables` |

## Migration Principles

1. **Additive only** — CREATE TABLE, ADD COLUMN, CREATE INDEX. Never DROP or ALTER in production.
2. **Backward compatible** — New columns must have defaults or be nullable.
3. **Idempotent where possible** — Use `IF NOT EXISTS`, `IF EXISTS`.
4. **Small and focused** — One logical change per migration.
5. **Tested** — Every migration must pass `FlywaySchemaIntegrationTest`.

## Rollback Strategy

Flyway does not support automatic rollback. For each migration:

1. **Document rollback steps** in migration file comments
2. **Test rollback** on a local database copy
3. **Keep rollback SQL** in a separate `rollback/` directory if complex

Example rollback for V2:

```sql
-- Rollback V2: Remove quota persistence tables
DROP TABLE IF EXISTS quota_usage_record;
DROP TABLE IF EXISTS quota_policy;
DROP TABLE IF EXISTS quota_bucket;
```

## Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `FlywayValidateException: Migration checksum mismatch` | V1 file was modified | Restore original V1 file |
| `FlywayValidateException: Detected applied migration not resolved locally` | Migration file was deleted | Restore the migration file |
| `FlywayMigrateException: Unable to acquire PostgreSQL advisory lock` | Another Flyway instance is running | Wait or kill the other instance |
| `Schema "public" already exists` | Flyway baseline not configured | Check `spring.flyway.baseline-on-migrate` |

## Integration with ProductionSafetyValidator

The `ProductionSafetyValidator` checks that:
- `spring.flyway.enabled=true`
- PostgreSQL datasource is configured (not H2)

These checks run on `ApplicationReadyEvent` in production profile.

## References

- [Flyway Baseline Runbook](flyway-baseline-runbook.md)
- [Production Safety](../production-safety.md)
- [V1 Baseline SQL](../../platform-app/src/main/resources/db/migration/V1__init_full_schema.sql)
