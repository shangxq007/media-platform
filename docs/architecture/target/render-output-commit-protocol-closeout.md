# Render Output Commit Protocol — Closeout Note

## Date

2026-07-15

## Baseline

```text
ADR-026: a539594
Status: ACCEPTED
```

## Resolved Inconsistencies

### 1. Commit Cardinality

```text
BEFORE: UNIQUE(render_job_id, output_type) — allows multiple commits per RenderJob
AFTER: UNIQUE(render_output_commit.render_job_id) — one commit per RenderJob
MULTIPLE OUTPUTS: Modeled as child RenderOutputItem records
```

### 2. Canonical State Set

```text
FALLBACKING: EXCLUDED (stale pre-launch baggage)
RETRYING: EXCLUDED (stale pre-launch baggage)
Future retry: Creates new RenderJob
```

### 3. Deterministic Key Semantics

```text
Same RenderJob replay: Idempotent overwrite
Same checksum: Reuse existing object
Different checksum: FAIL with conflict
Silent overwrite: FORBIDDEN
Blob/DB failure: Object uncommitted, not user-visible
User visibility: COMMITTED + Product READY
```

## V5 Schema Changes

```text
render_output table → Split into:
  - render_output_commit (UNIQUE(render_job_id))
  - render_output_item (UNIQUE(output_commit_id, output_role))
```

## Finding Ownership

All findings assigned to V5_SCHEMA, PROTOCOL_IMPLEMENTATION, FAILURE_WINDOW_VERIFICATION, REMOVE_AS_STALE_CONTRACT, or EXPLICIT_FUTURE_DEBT.

## Next Task

```text
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0
```
