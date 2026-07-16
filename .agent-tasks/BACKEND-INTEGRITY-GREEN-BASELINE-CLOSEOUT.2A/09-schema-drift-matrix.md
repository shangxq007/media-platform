# Schema Drift Matrix

| Schema source | selected_provider | updated_at | Authority | Notes |
|---------------|----------------:|----------:|-----------|-------|
| V1 | NO | NO | DDL | render_job table created without either column |
| V2 | NO | NO | DDL | Lifecycle events table, no render_job changes |
| V3 | NO | NO | DDL | Ingest preflight, no render_job changes |
| V4 | YES | NO | DDL | `ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128)` |
| Generated jOOQ | NO | NO | Code | Project uses inline DSL, no generated jOOQ classes |
| Repository SQL | YES | YES | Code | `RenderJobRepository.java` uses both in 10+ SQL operations |
| Domain/entity | N/A | N/A | Code | No JPA entity, uses jOOQ DSL |
| Test fixture | YES | YES | DDL | `RenderTestSchemaFixture` includes both columns |
| Fresh Flyway DB | YES | NO | Runtime | V4 adds selected_provider, no updated_at migration |

## Classification

**selected_provider:** CURRENT_SCHEMA_VALID — present in V4, correctly included in fixture

**updated_at:** CURRENT_SCHEMA_DRIFT_CONFIRMED
- NOT in V1-V4 DDL
- Used by production code in 10+ SQL operations (RenderJobRepository, RenderJobQueue)
- Included in test fixture for production code alignment
- Fresh Flyway database does NOT have the column
- This will cause runtime SQL errors if tests use real Flyway migrations instead of the fixture

## Required Corrective Migration

```sql
ALTER TABLE render_job ADD COLUMN updated_at timestamp NOT NULL DEFAULT now();
```

**Future owner:** DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0

**V5 is NOT ready.** This drift must be carried as a canonical defect in the document-governance inventory.
