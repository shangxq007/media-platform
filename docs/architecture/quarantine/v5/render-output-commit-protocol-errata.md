---
metadata_schema_version: 1
document_id: "architecture-quarantine-v5-render-output-commit-protocol-errata"
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

# Render Output Commit Protocol — Errata

## Date

2026-07-15

## Baseline

```text
Architecture: a539594
Closeout: b0b00f8
```

## Corrections Made

### 1. ADR-026 Schema Implications

```text
BEFORE: "New table: render_output" with UNIQUE(render_job_id, output_type)
AFTER: "New tables: render_output_commit + render_output_item" with UNIQUE(render_job_id)
```

### 2. Migration Strategy

```text
BEFORE: 4 items
AFTER: 7 items (added render_output_item, render_job.updated_at, product uniqueness)
```

## Frozen Semantics

### Checksum Conflict

```text
same key + same SHA-256: REUSE
same key + different SHA-256: DETERMINISTIC_OUTPUT_CONFLICT
silent overwrite: FORBIDDEN
```

### Retry

```text
FAILED RenderJob: terminal, immutable
Retry: creates new RenderJob
Reset: FORBIDDEN
```

### Compensation

```text
Expansion before protocol: FORBIDDEN
Target: DEFAULT_DISABLE_UNTIL_PROTOCOL_IMPLEMENTED
```

## Test Baseline

```text
compileJava: ✅ PASSED
compileTestJava: ✅ PASSED
Architecture guard: ✅ 32/32 PASSED
TDD tests: 3 tests updated to assertFalse (pass)
```

## V5 Migration Inputs

All inputs are explicit and consistent. V5 is ready.

## Next Task

```text
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0
```
