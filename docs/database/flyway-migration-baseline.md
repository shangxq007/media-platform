# Flyway Migration Baseline and History

> **Canonical Source of Truth** — This is the single authoritative document for all Flyway migration history in the media-platform project.
> Task reports must reference this document rather than duplicating migration details.

## Status

```text
system pre-launch: YES
single-source-of-truth rule: ACTIVE
forward-only migration rule: ACTIVE
pre-launch rewriting: NOT PERMITTED (migrations executed in shared environments)
```

## Migration Inventory

| Version | Filename | Purpose | Shared Execution Status |
| ------- | -------- | ------- | ---------------------- |
| V1 | V1__init_full_schema.sql | Initial schema baseline | EXECUTED_IN_SHARED_NONPRODUCTION |
| V2 | V2__create_render_job_lifecycle_events.sql | Lifecycle event history | EXECUTED_IN_SHARED_NONPRODUCTION |
| V3 | V3__create_ingest_preflight_safe_report_records.sql | Ingest preflight records | EXECUTED_IN_SHARED_NONPRODUCTION |
| V4 | V4__add_render_job_selected_provider.sql | Provider selection provenance | EXECUTED_IN_EPHEMERAL_TESTS |

## V1 — Initial Schema Baseline

**Filename:** `V1__init_full_schema.sql`
**Purpose:** Creates the complete initial database schema.
**Shared execution status:** EXECUTED_IN_SHARED_NONPRODUCTION
**Modification policy:** NEVER EDIT — frozen baseline.

**Key tables:**
- `render_job` — Core render job entity
- `outbox_events` — Event sourcing
- `audit_records` — Audit trail
- `storage_object` — Storage references
- `project` — Project entity
- `tenant` — Tenant entity (via identity module)

**render_job columns (V1):**
- `id` VARCHAR(64) PK
- `project_id` VARCHAR(128) NOT NULL
- `timeline_snapshot_id` VARCHAR(128) NOT NULL
- `profile` VARCHAR(128) NOT NULL
- `status` VARCHAR(32) NOT NULL
- `created_at` TIMESTAMP NOT NULL
- `ai_script` TEXT
- `artifact_uri` TEXT
- `error_message` TEXT
- `tenant_id` VARCHAR(64)
- `pipeline_plan_json` TEXT
- `pipeline_execution_json` TEXT
- `base_job_id` VARCHAR(64)
- `trace_id` VARCHAR(128)

## V2 — RenderJob Lifecycle Events

**Filename:** `V2__create_render_job_lifecycle_events.sql`
**Purpose:** Creates durable event history for diagnostics and operational visibility.
**Shared execution status:** EXECUTED_IN_SHARED_NONPRODUCTION
**Modification policy:** NEVER EDIT.

**Schema change:**
```sql
CREATE TABLE render_job_lifecycle_events (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    render_job_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status_from VARCHAR(32),
    status_to VARCHAR(32),
    worker_id VARCHAR(128),
    attempt INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    recovery_count INT DEFAULT 0,
    output_product_id VARCHAR(64),
    reason_code VARCHAR(64),
    reason VARCHAR(512),
    retryable BOOLEAN DEFAULT FALSE,
    next_retry_at TIMESTAMP,
    duration_ms BIGINT,
    event_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    payload_json TEXT,
    source VARCHAR(64) DEFAULT 'worker'
);
```

## V3 — Ingest Preflight Safe Report Records

**Filename:** `V3__create_ingest_preflight_safe_report_records.sql`
**Purpose:** Creates storage for ingest preflight validation reports.
**Shared execution status:** EXECUTED_IN_SHARED_NONPRODUCTION
**Modification policy:** NEVER EDIT.

## V4 — Selected Provider Persistence

**Filename:** `V4__add_render_job_selected_provider.sql`
**Purpose:** Adds durable Provider-selection provenance to render_job.
**Introduced by:** commit ba25c2e (BACKEND-INTEGRITY-RENDERJOB-SELECTION-TRANSITION-VALIDATION.0)
**Shared execution status:** EXECUTED_IN_EPHEMERAL_TESTS only
**Modification policy:** FORWARD ONLY — do not edit after shared execution.

**Schema change:**
```sql
ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128);
```

**Nullability:** NULL (nullable)
**Existing rows:** `selected_provider = NULL`
**Default value:** NULL
**Index:** Not required (low cardinality, infrequent query)
**Foreign key:** Not appropriate (Provider IDs are application-level, not database-enforced)

**Write owner:** `RenderJobExecutionService.executeRenderWithOptionalDag()`
**Read owner:** render_job table queries, status API
**Public exposure:** Internal only — not exposed through public RenderJob DTO by default

**Rationale:**
- Provider selection is a first-class RenderJob fact
- Required for debugging stuck/failed jobs
- Required for Provider provenance auditing
- Required for future Provider-based routing decisions
- VARCHAR(128) accommodates canonical Provider IDs (e.g., "ffmpeg", "remotion", "blender")

## Upgrade Behavior

| Scenario | Result |
| -------- | ------ |
| Empty database (V1→V4) | PASSED — all migrations apply cleanly |
| Existing pre-V4 rows | PASSED — selected_provider defaults to NULL |
| Testcontainers startup | PASSED — Flyway auto-migration succeeds |
| Application startup | PASSED — Flyway validates and migrates |

## Current Schema Baseline

```text
render_job.selected_provider: VARCHAR(128) NULL
```

## Governance

1. **Never edit applied migrations** — If a migration has been executed in any shared/persistent environment, use a future forward migration for corrections.
2. **New migration naming** — Use `V<N>__<description>.sql` with sequential version numbers.
3. **Review requirements** — All new migrations require architecture review before merge.
4. **Task reports** — Must reference this document, not duplicate migration history.
5. **Status files** — README, AGENTS.md, and status documents must not contain full migration history.
