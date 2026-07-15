# ADR-026: Render Output Commit Protocol

## Status

```text
PROPOSED
```

## Context

The media-platform render execution pipeline has no single, explicit authority for completing a rendered output. The current system has multiple implicit, distributed behaviors across:

- Provider output (FFmpeg render result)
- External blob storage (R2/S3)
- StorageReference (internal storage reference)
- Artifact (materialized output)
- FINAL_RENDER Product (canonical output)
- Billing/quota accounting
- RenderJob lifecycle (COMPLETING → COMPLETED)

### Current Problems

1. **No canonical output-commit authority** — multiple services can independently mark output as complete
2. **Artifact ID mismatch** — StorageCatalogService generates different ID than RenderResult
3. **Double quota consumption** — reserveQuota + consumeQuota both count usage
4. **Content hash = URI hash** — not content-based, defeats deduplication
5. **Product registration disconnected** — ProductRuntimeService.register() not called from render path
6. **No duplicate-finalization guard** — no CAS on COMPLETING → COMPLETED
7. **QuotaUsage non-idempotent** — read-modify-write, no dedup key
8. **BillingLedger non-idempotent** — INSERT with random UUID
9. **canRetry always false** — misleading API, no retry runtime
10. **StaleCompensation reachable** — scheduled every 5m + startup listener

## Decision

Introduce a first-class `RenderOutputCommit` model as the single canonical authority for output publication.

### Considered Options

| Option | Description | DB Enforceability | Recommendation |
|--------|-------------|-------------------|----------------|
| A | RenderJob owns output-commit state | Low | Rejected — mixing concerns |
| B | First-class RenderOutputCommit | High | **SELECTED** |
| C | Existing models composite protocol | Low | Rejected — no single authority |
| D | Hybrid commit + existing models | Medium | Rejected — complexity |

### Why Option B

- DB-enforced `UNIQUE(render_output_commit.render_job_id)` guarantees one commit per RenderJob
- Multiple physical outputs modeled as child `RenderOutputItem` records
- Clear lifecycle: PENDING → COMMITTED (or FAILED)
- Single authority for publication decisions
- Future-compatible with Temporal/OpenCue distributed execution
- Clean separation of execution vs publication

## Canonical Authority

```text
RenderOutputCommit — one record per RenderJob
UNIQUE(render_output_commit.render_job_id)
```

Multiple physical outputs (video, thumbnail, subtitle, etc.) are modeled as child `RenderOutputItem` records:

```text
RenderOutputItem — one record per output role per commit
UNIQUE(render_output_item.output_commit_id, output_role)
```

No other service may independently mark the complete output as published.

## One-Output Invariant

```text
For one RenderJob:
at most one RenderOutputCommit
ENFORCED BY: UNIQUE(render_output_commit.render_job_id)

For one RenderOutputCommit:
one RenderOutputItem per output role
ENFORCED BY: UNIQUE(render_output_item.output_commit_id, output_role)
```

## Completion Invariant

```text
RenderJob.status = COMPLETED
IFF ALL of:
1. render_output.status = COMMITTED
2. render_output.committed_at IS NOT NULL
3. StorageReference exists and references committed blob
4. Artifact exists and is READY
5. FINAL_RENDER Product exists and is READY
6. RenderBillingRecord exists (accepted)
7. lifecycle event committed
```

## Failure Invariant

```text
RenderJob.status = FAILED
THEN:
- render_output.status = FAILED (if exists)
- Product.status != READY
- Artifact.status != READY (or marked FAILED)
- No false COMPLETED state
```

## State Set

Canonical target RenderJob states:

```text
QUEUED
SELECTING_PROVIDER
PROVIDER_SELECTED
EXECUTING
COMPLETING
COMPLETED
FAILED
CANCELLED
```

FALLBACKING: EXCLUDED (stale pre-launch baggage)
RETRYING: EXCLUDED (stale pre-launch baggage)
Future retry: Creates a new RenderJob

COMPLETING means: Provider succeeded and platform is performing canonical Render Output Commit Protocol.

## Blob Ownership Protocol

```text
Strategy: DETERMINISTIC_FINAL_KEY

Object key: renders/{tenantId}/{jobId}/output.{format}
- Deterministic per RenderJob
- Idempotent on replay
- Not user-visible until publication complete
- Owned by render_output record
```

### Deterministic Key Semantics

| Scenario | Behavior |
|----------|----------|
| Same RenderJob replay | Same key, idempotent overwrite |
| Same key + same checksum | Reuse existing object |
| Same key + different checksum | FAIL with deterministic-output conflict |
| Silent overwrite | FORBIDDEN |
| Blob success / DB failure | Object remains uncommitted, not user-visible |
| Process restart | Resume from durable DB/object facts |
| New retry attempt | New RenderJob ID, new key namespace |
| User visibility | RenderOutputCommit COMMITTED AND Product READY

## Content Checksum

```text
MUST be content-based (SHA-256 of actual bytes)
NOT URI hash
Source: storage ETag or computed after upload
```

## Artifact Identity

```text
Canonical source: render_output.artifact_id
Created by: RenderOutputCommit authority
Referenced by: StorageCatalogService, Product
```

## Product Lifecycle

```text
Created: When render_output reaches COMMITTED
State: REGISTERED → READY
Linked: product.render_job_id = RenderJob.id
```

## Quota/Billing Protocol

| Operation | Semantic | Idempotency Key | Unique Constraint |
|-----------|----------|-----------------|-------------------|
| reserve | Pre-render quota hold | `bill-{jobId}` | UNIQUE(job_id) ✅ |
| consume | Post-commit quota deduction | `render_output.id` | PROPOSED: UNIQUE(ref_type, ref_id) |
| release | Cancel/failure quota return | `bill-{jobId}` | Same as reserve |
| ledger | Accounting entry | `reference_type + reference_id` | PROPOSED: UNIQUE(ref_type, ref_id) |

```text
Observable invariant:
For one RenderJob: accepted consumption mutations <= 1
```

## Duplicate Finalization

```text
Second call: INSERT INTO render_output ... ON CONFLICT DO NOTHING
Returns: existing record
No duplicate: StorageReference, Artifact, Product, Billing, COMPLETED
```

## Transaction Boundaries

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

## Compensation Service Disposition

```text
Target: DEFAULT_DISABLE_UNTIL_PROTOCOL_IMPLEMENTED
Reason: Currently reachable but operates on incomplete protocol
Action: Disable scheduled/startup triggers after implementation
```

## canRetry Disposition

```text
Target: REMOVE_FROM_CURRENT_CONTRACT
Reason: Always false, misleading, no retry runtime exists
Action: Remove field, return explicit unsupported
```

## Schema Implications

```text
MINIMUM_V5_REQUIRED

New tables:
  - render_output_commit (one per RenderJob)
  - render_output_item (one per output role per commit)
New columns: render_job.idempotency_key, render_job.updated_at
New constraints:
  - UNIQUE(render_output_commit.render_job_id)
  - UNIQUE(render_output_item.output_commit_id, output_role)
  - UNIQUE(reference_type, reference_id) on billing_ledger_entry
```

## Migration Strategy

```text
V5 adds:
1. render_output_commit table
2. render_output_item table
3. render_job.idempotency_key column
4. render_job.updated_at column
5. billing_ledger_entry unique constraint
6. quota_usage unique constraint
7. product render_job_id unique constraint
```

## Security Implications

```text
- No signed URLs persisted as canonical state
- No objectKey/bucket exposed in API responses
- No StorageReference ID exposed to clients
- Content checksum not based on URI
```

## Observability

```text
- render_output.status tracks publication lifecycle
- committed_at provides atomic "output ready" marker
- failure_code/failure_summary for diagnostics
```

## Future Compatibility

```text
- Temporal: render_output.id = workflow run ID
- OpenCue: render_output tracks job submission + completion
- Distributed workers: protocol valid across process restart
```

## Consequences

### Positive
- Single source of truth for output publication
- DB-enforced uniqueness prevents duplicates
- Clear failure semantics
- Future-compatible with distributed execution

### Negative
- New table required (V5 migration)
- Existing code refactoring needed
- Two-phase publication adds complexity

## Risks

1. Migration complexity for existing data
2. Refactoring existing scattered publication code
3. Performance impact of additional DB writes

## Non-Goals

- Artifact DAG (POSTPONED)
- Retry runtime (NOT_IMPLEMENTED)
- Cleanup runtime (NOT_IMPLEMENTED)
- Temporal/OpenCue integration (NOT_STARTED)

## Implementation Phases

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

## Verification Gates

Each phase requires:
- Compilation pass
- Architecture drift guard pass (32/32)
- Targeted runtime tests
- Independent verification
