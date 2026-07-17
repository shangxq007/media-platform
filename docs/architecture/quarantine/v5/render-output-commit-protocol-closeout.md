---
metadata_schema_version: 1
document_id: "architecture-quarantine-v5-render-output-commit-protocol-closeout"
title: ""
artifact_type: "UNKNOWN"
domain: ""
authority_class: "QUARANTINED"
lifecycle_state: "QUARANTINED"
acceptance_state: "QUARANTINED_BLOCKED"
owner: "architecture-governance"
document_version: null
created_at: null
last_reviewed_at: "2026-07-17"
review_cadence_days: null
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: []
retention_class: "LEGAL_OR_AUDIT_HOLD"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

> [!CAUTION]
> **Status:** Quarantined and not accepted.
> This material MUST NOT be used as implementation authority.
> V5 remains blocked until document governance .1-.7 closes.
> Current canonical semantics are defined by the render-output contract candidate.
> See: [Canonical Contracts](../../governance/canonical-contracts/)

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
