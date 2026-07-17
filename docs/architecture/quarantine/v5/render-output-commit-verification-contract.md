---
metadata_schema_version: 1
document_id: "architecture-quarantine-v5-render-output-commit-verification-contract"
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

# Render Output Commit — Verification Contract

## Required Runtime Tests

### Test A: Deterministic Concurrent Start

```text
Environment: @SpringBootTest(RANDOM_PORT), Testcontainers PostgreSQL
Method: Two real TCP requests with CyclicBarrier

Required counts:
  HTTP attempts = 2
  claim attempts = 2
  claim winners = 1
  claim losers = 1
  script resolution = 1
  selector = 1
  selected_provider writes = 1
  EXECUTING transitions = 1
  render = 1
  logical executions = 1
  duplicate executions = 0
  lost updates = 0
  state regressions = 0
```

### Test B: Duplicate Finalization

```text
Method: Call finalization path twice for same RenderJob

Required:
  render_output records = 1
  StorageReference records <= 1
  Artifact records <= 1
  Product records <= 1
  Billing records <= 1
  COMPLETED transitions <= 1
```

### Test C: Duplicate Billing/Quota

```text
Method: Call accounting boundary twice with same RenderJob

Required:
  accepted mutations = 1
  second call = existing result or no-op
```

### Test D: Blob Write Failure

```text
Method: Force failure before blob commit

Required (after reload):
  RenderJob = FAILED
  Product not READY
  Artifact not READY
  COMPLETED absent
```

### Test E: Blob Success / DB Failure

```text
Method: Force failure after blob, before DB commit

Required (after reload):
  RenderJob = FAILED
  no false COMPLETED
  blob ownership classified
```

### Test F: Artifact Failure

```text
Method: Force failure at Artifact persistence

Required (after reload):
  RenderJob = FAILED
  Product not READY
```

### Test G: Product Failure

```text
Method: Force failure at Product publication

Required (after reload):
  RenderJob = FAILED
  Product not READY
```

### Test H: Billing Failure

```text
Method: Force failure at Billing boundary

Required (after reload):
  RenderJob = FAILED
  accepted Billing mutations = 0 or existing
```

### Test I: Post-Billing Completion Failure

```text
Method: Force failure after Billing success

Required (after reload):
  RenderJob not COMPLETED
  Billing mutations remain 1
  replay idempotent
```

### Test J: COMPLETED Transition Failure

```text
Method: Force failure at final transition

Required (after reload):
  RenderJob not COMPLETED
  output state consistent
```

### Test K: Product/RenderJob Consistency

```text
Assert:
  RenderJob COMPLETED → all output conditions true
  RenderJob FAILED → Product not READY
  Product READY → committed storage exists
```

### Test L: Compensation Service

```text
Assert:
  No scheduled execution
  No startup trigger
  No production transitions
```

### Test M: canRetry

```text
Assert:
  No API implies retry runtime
  retry route = 404
  no retry execution
```

### Test N: Route and Security

```text
Assert:
  execute-local = 404
  retry = 404
  SPA fallback = /app/**
  false QUEUED = absent
  false COMPLETED = absent
  sensitive data = absent
```

## Database Reload Assertions

Every failure test must verify state survives reload in a new PostgreSQL transaction:

```java
// After outer call completes
RenderJob reloaded = jdbc.query("SELECT status FROM render_job WHERE id = ?", jobId);
assertEquals("FAILED", reloaded.status());

Product product = jdbc.query("SELECT status FROM product WHERE render_job_id = ?", jobId);
assertNotEquals("READY", product.status());
```
