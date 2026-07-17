> [!CAUTION]
> **Status:** Quarantined and not accepted.
> This material MUST NOT be used as implementation authority.
> V5 remains blocked until document governance .1-.7 closes.
> Current canonical semantics are defined by the render-output contract candidate.
> See: [Canonical Contracts](../../governance/canonical-contracts/)

# Render Output Commit — Failure Window Matrix

| # | Failure Point | External State | DB State | User-Visible | Replay Behavior | Final Expected State |
|---|---------------|----------------|----------|--------------|-----------------|---------------------|
| 1 | Blob write fails | No blob | No render_output | Nothing | Retry from scratch | FAILED |
| 2 | Blob succeeds, DB metadata fails | Blob exists | No render_output | Nothing | Deterministic key = safe overwrite | FAILED |
| 3 | StorageReference fails | Blob exists | render_output PENDING | Nothing | Re-create on retry | FAILED |
| 4 | Artifact fails | Blob + StorageRef | render_output PENDING | Nothing | Re-create on retry | FAILED |
| 5 | Product fails | Blob + Artifact | render_output PENDING | Nothing | Re-create on retry | FAILED |
| 6 | Billing fails | All output metadata | render_output PENDING | Nothing | Idempotent retry | FAILED |
| 7 | Publication fails | All metadata | render_output PENDING | Nothing | Re-commit on retry | FAILED |
| 8 | Completion fails | All metadata | render_output COMMITTED | Partial | CAS retry | FAILED or COMPLETED |
| 9 | Duplicate finalization | Same blob | render_output exists | None | ON CONFLICT DO NOTHING | COMPLETED (idempotent) |
| 10 | Process interruption | Varies | Varies | Nothing | Deterministic replay | FAILED or COMPLETED |

## Key Invariants

```text
1. No blob is user-visible before render_output COMMITTED
2. No Product is READY before render_output COMMITTED
3. No Artifact is READY before blob committed
4. Duplicate finalization returns existing record
5. Orphan blobs are bounded (deterministic key = safe overwrite)
```
