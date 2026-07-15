# Lead Architecture Decision

## Current State Summary

### Output-Commit Paths

```text
1. RenderJobExecutionService.finishRenderPhaseInternal() — PRIMARY
   Entry: execute() / executeAfterSubmit() / finishRenderPhase()
   
2. RenderOutputRegistrationService — SECONDARY (caption/preview only)
   Entry: registerOutput() from caption/preview services
```

### Critical Issues Found

| Issue | Severity | Status |
|-------|----------|--------|
| Artifact ID mismatch | CRITICAL | Confirmed — StorageCatalogService generates different ID |
| Double quota consumption | CRITICAL | Confirmed — reserveQuota + consumeQuota both count |
| Content hash = URI hash | HIGH | Confirmed — not content-based |
| Product registration disconnected | HIGH | Confirmed — ProductRuntimeService.register() not called |
| No duplicate finalization guard | HIGH | Confirmed — no CAS on COMPLETING→COMPLETED |
| QuotaUsage non-idempotent | HIGH | Confirmed — read-modify-write, no dedup key |
| BillingLedger non-idempotent | HIGH | Confirmed — INSERT with random UUID |
| canRetry always false | LOW | Confirmed — misleading, all states false |
| StaleCompensation reachable | MEDIUM | Confirmed — scheduled every 5m + startup |

### Billing/Quota Idempotency

| Component | Idempotent | Mechanism |
|-----------|-----------|-----------|
| RenderBillingRecord | ✅ YES | Deterministic ID `bill-{jobId}` + UPSERT + UNIQUE(job_id) |
| QuotaUsageRepository | ❌ NO | Read-modify-write, no dedup |
| BillingLedgerJdbcRepository | ❌ NO | INSERT with random UUID, no uniqueness |

## Architecture Options Evaluated

### Option A: RenderJob Owns Output-Commit State

```text
Add output-commit columns directly to render_job table.
No new table.
```

**Pros:** Simple, no new model
**Cons:** render_job already large, mixing concerns, hard to add future output types

### Option B: First-Class RenderOutputCommit Model

```text
New render_output table with UNIQUE(render_job_id, output_type).
One record per RenderJob per output type.
```

**Pros:** Clean separation, DB-enforced uniqueness, clear lifecycle
**Cons:** New table, migration required

### Option C: Existing Models Composite Protocol

```text
Use existing Artifact/Product/StorageReference as composite commit.
No new table.
```

**Pros:** No schema change
**Cons:** No single authority, hard to enforce uniqueness, multiple scattered writers

### Option D: Hybrid — RenderOutputCommit + Existing Models

```text
New render_output as commit authority, references existing Artifact/Product.
```

**Pros:** Single authority + existing model reuse
**Cons:** More complex, two-phase publication

## Decision

```text
OPTION_B_RECOMMENDED
```

**Reason:** Option B provides the cleanest DB-enforced one-output-per-RenderJob invariant, clear lifecycle (PENDING→COMMITTED), and future compatibility with Temporal/OpenCue.

## Frozen Protocol Design

### Canonical Authority

```text
RenderOutputCommit — one record per RenderJob
UNIQUE(render_job_id, output_type)
```

### One-Output Invariant

```text
For one RenderJob:
at most one render_output record per output_type
ENFORCED BY: database UNIQUE constraint
```

### RenderJob COMPLETED Invariant

```text
RenderJob.status = COMPLETED
IFF ALL of:
1. render_output.status = COMMITTED
2. render_output.committed_at IS NOT NULL
3. StorageReference exists and references committed blob
4. Artifact exists and is READY
5. FINAL_RENDER Product exists and is READY
6. Billing accepted (RenderBillingRecord exists)
7. lifecycle event committed
```

### RenderJob FAILED Invariant

```text
RenderJob.status = FAILED
THEN:
- render_output.status = FAILED (if exists)
- Product.status != READY
- Artifact.status != READY (or marked FAILED)
- No false COMPLETED state
```

### Blob Ownership Protocol

```text
Strategy: DETERMINISTIC_FINAL_KEY

Object key: renders/{tenantId}/{jobId}/output.{format}
- Deterministic per RenderJob
- Idempotent on replay
- Not user-visible until publication complete
- Owned by render_output record

Staging: Not needed (deterministic key = safe overwrite)
```

### Content Checksum

```text
MUST be content-based (SHA-256 of actual bytes)
NOT URI hash
Source: storage ETag or computed after upload
```

### Artifact Identity

```text
Canonical source: render_output.artifact_id
Created by: RenderOutputCommit authority
Referenced by: StorageCatalogService, Product
```

### Product Lifecycle

```text
Created: When render_output reaches COMMITTED
State: REGISTERED → READY
Linked: product.render_job_id = RenderJob.id
```

### Quota/Billing Protocol

```text
reserve(): Before render, idempotent via RenderBillingRecord (✅)
consume(): After commit, needs idempotency key → use render_output.id
ledger: Needs UNIQUE(reference_type, reference_id) → propose V5
```

### Duplicate Finalization

```text
Second call: INSERT INTO render_output ... ON CONFLICT DO NOTHING
Returns: existing record
No duplicate: StorageReference, Artifact, Product, Billing, COMPLETED
```

### Compensation Service

```text
Target: DEFAULT_DISABLE_UNTIL_PROTOCOL_IMPLEMENTED
Reason: Currently reachable but operates on incomplete protocol
Action: Disable scheduled/startup triggers after implementation
```

### canRetry

```text
Target: REMOVE_FROM_CURRENT_CONTRACT
Reason: Always false, misleading, no retry runtime exists
Action: Remove field, return explicit unsupported
```

### Schema Decision

```text
MINIMUM_V5_REQUIRED

New table: render_output
New columns: render_job.idempotency_key
New constraints: UNIQUE(render_job_id, output_type), UNIQUE(reference_type, reference_id)
```

### Transaction Topology

```text
execute() [NO @Transactional]
├── claim [REQUIRES_NEW, short]
├── resolve/select [no transaction]
├── persist selected_provider [short transaction]
├── render [NO transaction - FFmpeg outside]
├── render_output INSERT [short transaction, PENDING]
├── blob write [NO transaction, deterministic key]
├── StorageReference + Artifact + render_output COMMITTED [short transaction]
├── Product READY + RenderJob COMPLETED [short transaction]
└── failure at any point → render_output FAILED + RenderJob FAILED [REQUIRES_NEW]
```

## Implementation Roadmap

| Phase | Scope | Migration |
|-------|-------|-----------|
| 1 | Schema: render_output table + constraints | V5 |
| 2 | Single output-commit authority | No |
| 3 | Blob ownership + checksum | No |
| 4 | Artifact/Product publication coupling | No |
| 5 | Quota/Billing idempotency | V5 (ledger constraint) |
| 6 | Disable stale compensation | No |
| 7 | Remove canRetry | No |
| 8 | Failure-window verification | No |
| 9 | Architecture guards | No |
