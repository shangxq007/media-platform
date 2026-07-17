> [!CAUTION]
> **Status:** Quarantined and not accepted.
> This material MUST NOT be used as implementation authority.
> V5 remains blocked until document governance .1-.7 closes.
> Current canonical semantics are defined by the render-output contract candidate.
> See: [Canonical Contracts](../../governance/canonical-contracts/)

# Render Output Commit — Current State

## Output-Registration Paths

### Path A: Primary (RenderJobExecutionService)

```text
Entry: finishRenderPhaseInternal()
Called by: execute() / executeAfterSubmit() / finishRenderPhase()

Sequence:
1. Load job record
2. Tenant validation
3. Early return if COMPLETED
4. Resolve ai_script
5. Effect entitlement validation
6. Ensure EXECUTING state
7. Billing reservation (reserveQuota)
8. Render execution (FFmpeg)
9. Billing finalization (finalizeCost)
10. Create ArtifactGraph
11. Upload blob to storage
12. Register in StorageCatalogService
13. Transition COMPLETING → COMPLETED
14. Emit lifecycle events
```

### Path B: Secondary (RenderOutputRegistrationService)

```text
Entry: registerOutput()
Called by: caption/preview services

Sequence:
1. Create StorageReference
2. Create FINAL_RENDER Product (REGISTERED → READY)
3. Link dependencies
```

## Current Authorities

| Action | Authority | Transaction |
|--------|-----------|-------------|
| Blob write | RenderArtifactStorageService | None (external I/O) |
| StorageReference | StorageCatalogService | Caller's transaction |
| Artifact | StorageCatalogService | Caller's transaction |
| Product | ProductRuntimeService | Not connected |
| Billing | BillingEnforcementService | Caller's transaction |
| RenderJob COMPLETING | RenderJobExecutionService | Caller's transaction |
| RenderJob COMPLETED | RenderJobExecutionService | Caller's transaction |
| RenderJob FAILED | RenderJobFailureService | REQUIRES_NEW |

## Current ID Generation

| Entity | ID Pattern | Source |
|--------|-----------|--------|
| RenderJob | `rj-{uuid}` | RenderJobExecutionService |
| Artifact | `art-{uuid}` | StorageCatalogService (MISMATCH with RenderResult) |
| StorageReference | `stor-{uuid}` | StorageCatalogService |
| Product | `prod-{uuid}` | ProductRuntimeService |
| RenderBillingRecord | `bill-{jobId}` | Deterministic ✅ |
| QuotaUsage | `qtu-{random}` | Non-deterministic ❌ |
| BillingLedger | `{random uuid}` | Non-deterministic ❌ |

## Current Failure Windows

| Window | External State | DB State | Risk |
|--------|---------------|----------|------|
| Blob write fails | No blob | No record | Clean |
| Blob succeeds, DB fails | Blob exists | No record | **ORPHAN** |
| StorageRef fails | Blob exists | Partial | **ORPHAN** |
| Artifact fails | Blob + StorageRef | Partial | **ORPHAN** |
| Product fails | Blob + Artifact | Partial | **ORPHAN** |
| Billing fails | All output | Partial | **ORPHAN** |
| Completion fails | All output | Partial | **ORPHAN** |
| Duplicate call | Duplicate blob | Duplicate records | **DUPLICATE** |
