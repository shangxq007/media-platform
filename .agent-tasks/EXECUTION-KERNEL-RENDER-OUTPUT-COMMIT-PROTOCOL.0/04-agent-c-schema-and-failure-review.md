# 04 — Agent C: Schema & Failure-Window Review for Render Output Commit Protocol

**Commit**: 234689e on `arch/render-output-commit-protocol`
**Prior Report**: `.agent-tasks/BACKEND-INTEGRITY-REPAIR-RENDERJOB-OUTPUT-COMMIT-QUOTA-IDEMPOTENCY-AND-PRODUCT-LIFECYCLE.3/05-agent-c-product-lifecycle-and-test-design.md`
**Validation Status**: Prior report verified against current source — all findings confirmed, additional gaps identified.

---

## 1. Current Schema Inventory (Verified Against V1__init_full_schema.sql + V2–V4)

### 1.1 `render_job` (V1 + V4)

| Column | Type | Constraints |
|--------|------|-------------|
| id | varchar(64) | **PK** |
| project_id | varchar(128) | NOT NULL |
| timeline_snapshot_id | varchar(128) | NOT NULL |
| profile | varchar(128) | NOT NULL |
| status | varchar(32) | NOT NULL |
| created_at | timestamp | NOT NULL |
| ai_script | text | — |
| artifact_uri | text | — |
| error_message | text | — |
| tenant_id | varchar(64) | — |
| pipeline_plan_json | text | — |
| pipeline_execution_json | text | — |
| base_job_id | varchar(64) | — |
| trace_id | varchar(128) | — |
| selected_provider | varchar(128) | Added in V4 |

**Indexes**: `ix_render_job_project_id`, `ix_render_job_status`, `ix_render_job_base_job_id`, `ix_render_job_trace_id`

**Missing columns for output commit**:
- No `output_product_id` — no link to Product after completion
- No `output_storage_reference_id` — no link to StorageReference
- No `idempotency_key` — no deduplication key on the render_job itself
- No `attempt` / `attempt_count` — no retry tracking on the job row
- No `updated_at` — referenced in CAS queries via `set(field("updated_at"), ...)` but **not in the DDL**

### 1.2 `product` (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| product_id | varchar(64) | **PK** |
| tenant_id | varchar(64) | — |
| project_id | varchar(64) | — |
| owner_asset_id | varchar(64) | — |
| product_type | varchar(32) | NOT NULL |
| representation_kind | varchar(32) | NOT NULL |
| producer_type | varchar(32) | — |
| producer_id | varchar(64) | — |
| source_timeline_revision_id | varchar(64) | — |
| status | varchar(32) | NOT NULL DEFAULT 'REGISTERED' |
| storage_reference_id | varchar(256) | — |
| checksum | varchar(128) | — |
| content_hash | varchar(128) | — |
| mime_type | varchar(64) | — |
| version | int | NOT NULL DEFAULT 1 |
| metadata_json | text | — |
| created_at | timestamp | NOT NULL |
| updated_at | timestamp | NOT NULL |

**Indexes**: tenant, project, asset, producer, status, type

**Unique constraints**: None beyond PK. **No unique constraint on (producer_id, product_type) or (render_job_id, product_type)**.

### 1.3 `storage_reference` (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| storage_reference_id | varchar(64) | **PK** |
| provider_type | varchar(32) | NOT NULL DEFAULT 'LOCAL' |
| storage_class | varchar(32) | NOT NULL DEFAULT 'STANDARD' |
| root_path | varchar(512) | NOT NULL |
| relative_path | varchar(512) | NOT NULL |
| checksum | varchar(128) | — |
| content_hash | varchar(128) | — |
| file_size | bigint | NOT NULL DEFAULT 0 |
| mime_type | varchar(64) | — |
| created_at | timestamp | NOT NULL |
| updated_at | timestamp | NOT NULL |

**Unique constraint**: `uq_storage_path UNIQUE(provider_type, root_path, relative_path)`

### 1.4 `product_dependency` (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| dependency_id | varchar(64) | **PK** |
| tenant_id | varchar(64) | — |
| project_id | varchar(64) | — |
| product_id | varchar(64) | NOT NULL, FK → product |
| depends_on_product_id | varchar(64) | NOT NULL, FK → product |
| dependency_type | varchar(32) | NOT NULL |
| created_at | timestamp | NOT NULL |

**Unique constraint**: `uq_prod_dep UNIQUE(product_id, depends_on_product_id, dependency_type)`

### 1.5 `artifact` (V1 — catalog module)

| Column | Type | Constraints |
|--------|------|-------------|
| id | varchar(64) | **PK** |
| render_job_id | varchar(64) | NOT NULL |
| project_id | varchar(64) | NOT NULL |
| storage_uri | text | NOT NULL |
| format | varchar(32) | — |
| resolution | varchar(32) | — |
| duration | bigint | — |
| created_at | timestamp | NOT NULL |
| status | varchar(32) | NOT NULL DEFAULT 'ACTIVE' |
| tombstoned_at | timestamp | — |

**No unique constraint on (render_job_id)** — multiple artifacts per job allowed.

### 1.6 `render_job_status_history` (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| id | varchar(64) | **PK** |
| job_id | varchar(64) | NOT NULL |
| from_status | varchar(30) | — |
| to_status | varchar(30) | NOT NULL |
| reason | varchar(255) | — |
| error_code | varchar(100) | — |
| occurred_at | timestamp | NOT NULL DEFAULT now() |

**No unique constraints** — pure append-only audit log.

### 1.7 `render_job_lifecycle_events` (V2)

| Column | Type | Constraints |
|--------|------|-------------|
| id | varchar(64) | **PK** |
| tenant_id | varchar(64) | NOT NULL |
| project_id | varchar(128) | NOT NULL |
| render_job_id | varchar(64) | NOT NULL |
| event_type | varchar(64) | NOT NULL |
| status_from | varchar(32) | — |
| status_to | varchar(32) | — |
| worker_id | varchar(128) | — |
| attempt | int | DEFAULT 0 |
| retry_count | int | DEFAULT 0 |
| recovery_count | int | DEFAULT 0 |
| output_product_id | varchar(64) | — |
| reason_code | varchar(64) | — |
| reason | varchar(512) | — |
| retryable | boolean | DEFAULT FALSE |
| next_retry_at | timestamp | — |
| duration_ms | bigint | — |
| event_time | timestamp | NOT NULL |
| created_at | timestamp | NOT NULL DEFAULT NOW() |
| payload_json | text | — |
| source | varchar(64) | DEFAULT 'worker' |

**No unique constraints** — append-only.

---

## 2. Prior Agent C Report Validation

### 2.1 Confirmed Findings (from 05-agent-c-product-lifecycle-and-test-design.md)

| # | Prior Finding | Current Source Validation | Status |
|---|---------------|--------------------------|--------|
| 1 | Main render path (`finishRenderPhaseInternal`) does NOT create FINAL_RENDER Product | **CONFIRMED** — lines 321–458 of RenderJobExecutionService.java show no Product creation | Still true |
| 2 | No duplicate finalization guard on COMPLETING state | **CONFIRMED** — `finishRenderPhaseInternal` only checks COMPLETED (line 329), not COMPLETING | Still true |
| 3 | Two separate storage registration mechanisms (BlobStorage vs StorageRuntimeService) | **CONFIRMED** — Path A uses `artifactStorageService.uploadJobOutput()` (BlobStorage), Path B uses `storageRuntime.register()` (StorageReferenceRepository) | Still true |
| 4 | State machine is in-memory only (ConcurrentHashMap) | **CONFIRMED** — RenderJobStateMachine.java line 87: `ConcurrentHashMap<>` | Still true |
| 5 | Billing finalization failure silently swallowed | **CONFIRMED** — lines 385–388: catch block logs warning, doesn't fail job | Still true |
| 6 | ArtifactGraph and Artifact (catalog) are disconnected | **CONFIRMED** — ArtifactGraph uses ArtifactNode (render-module), Artifact is in artifact-catalog-module, no cross-reference | Still true |
| 7 | Product has no validation that storageReferenceId is non-null before markReady | **CONFIRMED** — ProductRuntimeService.markReady() doesn't check | Still true |
| 8 | `computeContentHash` uses URI hashCode, not actual content | **CONFIRMED** — line 667: `"hash-" + Integer.toHexString(uri.hashCode())` | Still true |

### 2.2 New Findings from Current Source Validation

| # | New Finding | Severity | Evidence |
|---|-------------|----------|----------|
| N1 | `render_job` table has no `updated_at` column in DDL but CAS queries set it | **HIGH** | V1 DDL has no `updated_at`; `RenderJobRepository.claimForSelection()` line 133 sets `updated_at`; `markActiveJobFailed()` line 168 sets `updated_at`. jOOQ will generate SQL that references a non-existent column. **Either the column exists via undocumented migration, or this is a latent bug.** |
| N2 | No `render_output` table exists anywhere | **HIGH** | Zero matches for `render_output` in all SQL files. There is no dedicated table for tracking render outputs with idempotency. |
| N3 | Product.save() uses `ON CONFLICT (product_id) DO UPDATE` — upserts by PK only | **MEDIUM** | ProductRepository line 39: `onConflict(field("product_id")).doUpdate()`. This means calling `registerOutput()` twice for the same jobId with different generated productIds creates two Products. No deduplication by jobId. |
| N4 | `claimForSelection` CAS only guards QUEUED → SELECTING_PROVIDER, not the full pipeline | **MEDIUM** | The CAS is committed in REQUIRES_NEW, but all subsequent transitions (SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED) happen in the default transaction with no CAS guard. |
| N5 | `render_job.status` is a varchar with no CHECK constraint | **LOW** | Invalid status values can be written to the DB. The state machine is in-memory only. |
| N6 | `render_job_lifecycle_events.output_product_id` exists but is never populated by the main render path | **MEDIUM** | The column exists (V2 migration) but `RenderJobExecutionService` never writes to it. |

---

## 3. Minimum Schema Requirements for Durable Idempotency

### 3.1 Definition

Durable idempotency means: **if two concurrent or replayed requests attempt to produce output for the same RenderJob, exactly one output is committed, and the second is either rejected or returns the existing output.**

### 3.2 Current Schema Assessment

**The current schema does NOT support durable idempotency for render output commit.**

Reasons:
1. **No `render_output` table** — There is no table that records "this RenderJob has produced this output" with a unique constraint.
2. **No unique constraint linking RenderJob to Product** — The `product` table has no `render_job_id` column and no unique constraint on `(producer_id, product_type)`.
3. **No unique constraint linking RenderJob to artifact** — The `artifact` table allows multiple rows per `render_job_id`.
4. **The `storage_reference` unique constraint** (`provider_type, root_path, relative_path`) prevents duplicate storage registrations for the same path, but does not prevent two different render jobs from producing outputs to different paths.
5. **Product.save() upserts by `product_id` only** — Since `product_id` is generated fresh each time (`Ids.newId("prod")`), there is no deduplication.

### 3.3 Minimum Schema Additions Required

#### Option A: Add `render_output` junction table (Recommended)

```sql
CREATE TABLE render_output (
    id VARCHAR(64) PRIMARY KEY,
    render_job_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64),
    storage_reference_id VARCHAR(64),
    output_type VARCHAR(32) NOT NULL DEFAULT 'FINAL_RENDER',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    checksum VARCHAR(128),
    file_size BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    committed_at TIMESTAMP,
    
    -- THE CRITICAL CONSTRAINT: one output per job per type
    CONSTRAINT uq_render_output_job_type UNIQUE(render_job_id, output_type),
    
    CONSTRAINT fk_render_output_job FOREIGN KEY (render_job_id) 
        REFERENCES render_job(id) ON DELETE RESTRICT
);

CREATE INDEX ix_render_output_job ON render_output(render_job_id);
CREATE INDEX ix_render_output_product ON render_output(product_id);
CREATE INDEX ix_render_output_status ON render_output(status);
```

**Why this works**:
- The `UNIQUE(render_job_id, output_type)` constraint makes the one-output-per-RenderJob invariant **durable at the DB level**.
- A concurrent attempt to insert a second row for the same `(render_job_id, 'FINAL_RENDER')` will fail with a unique violation.
- The `status` column allows a PENDING → COMMITTED lifecycle, enabling two-phase commit semantics.
- The `committed_at` timestamp provides an atomic "output is ready" marker.

#### Option B: Add `render_job_id` to `product` table with unique constraint

```sql
ALTER TABLE product ADD COLUMN render_job_id VARCHAR(64);
CREATE UNIQUE INDEX uq_product_render_job_type ON product(render_job_id, product_type) 
    WHERE render_job_id IS NOT NULL;
```

**Limitation**: This conflates Product identity with RenderJob identity. Products can come from non-render sources (imports, manual uploads). The partial unique index helps but creates a mixed-concern schema.

#### Option C: Add idempotency_key to `render_job`

```sql
ALTER TABLE render_job ADD COLUMN idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX uq_render_job_idempotency ON render_job(idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
```

**Limitation**: This prevents duplicate job submission, not duplicate output commit. Two concurrent `finishRenderPhase()` calls for the same job would still both succeed.

**Recommendation**: Option A is the minimum correct solution. It cleanly separates the "output exists" invariant from both the job lifecycle and the product lifecycle.

---

## 4. One-Output-Per-RenderJob Invariant Assessment

### 4.1 Current State: NOT ENFORCED

The current code has **no mechanism** to enforce one output per RenderJob:

1. **`finishRenderPhaseInternal`** (lines 321–458): No check for existing output. Only checks if job is COMPLETED (returns early), but does NOT check if job is COMPLETING (which would indicate an in-flight finalization).

2. **`RenderOutputRegistrationService.registerOutput()`**: Generates a new `productId` every call. No check for existing output for the same jobId.

3. **`ProductRepository.save()`**: Uses `ON CONFLICT (product_id) DO UPDATE` — since productId is always new, this is effectively a plain INSERT.

4. **`artifact` table**: No unique constraint on `render_job_id`. Multiple artifact rows per job are allowed by design (ArtifactGraph supports multiple nodes).

5. **`storage_reference` table**: The `uq_storage_path` constraint prevents duplicate paths but doesn't prevent duplicate registrations with different paths for the same logical output.

### 4.2 Race Window

```
Thread A                          Thread B
────────                          ────────
finishRenderPhaseInternal()       finishRenderPhaseInternal()
  status = EXECUTING ✓             status = EXECUTING ✓
  renderProvider.render() ✓        renderProvider.render() ✓
  COMPLETING                       COMPLETING
  uploadJobOutput() ✓              uploadJobOutput() ✓
  ArtifactGraph.save() ✓           ArtifactGraph.save() ✓
  updateArtifactUri() ✓            updateArtifactUri() ← overwrites A's URI
  COMPLETED                        COMPLETED
```

Both threads succeed. The job ends up COMPLETED with thread B's artifact URI. Thread A's upload is orphaned (uploaded bytes with no reference).

### 4.3 Required Enforcement Layer

The one-output-per-RenderJob invariant requires:
1. **Schema**: `UNIQUE(render_job_id, output_type)` on `render_output` (Option A above)
2. **Transaction**: `INSERT INTO render_output ... ON CONFLICT DO NOTHING` returning the inserted row or the existing row
3. **CAS**: The INSERT must be part of the COMPLETING → COMPLETED transition, not a separate step

---

## 5. Failure-Window Matrix

### 5.1 Failure Scenarios

| # | Failure Point | Job State After | Output State | Product State | Storage State | Recovery Possible? | Idempotent? |
|---|---------------|-----------------|--------------|---------------|---------------|-------------------|-------------|
| F1 | Script resolution fails | FAILED (REQUIRES_NEW) | None | None | None | Retry from QUEUED | N/A |
| F2 | Effect entitlement fails | FAILED (REQUIRES_NEW) | None | None | None | Manual review needed | N/A |
| F3 | Provider render() fails | FAILED (REQUIRES_NEW) | None | None | None | Retry from QUEUED | N/A |
| F4 | Billing reservation fails | FAILED (REQUIRES_NEW) | None | None | None | Retry after quota freed | N/A |
| F5 | Storage upload fails (COMPLETING) | FAILED (REQUIRES_NEW) | None | None | Partial (bytes may be uploaded but not referenced) | Retry; orphan cleanup needed | **NOT idempotent** — retry may re-upload |
| F6 | ArtifactGraph save fails | FAILED (outer txn rolls back) | None | None | Uploaded bytes exist | Retry; orphan cleanup needed | **NOT idempotent** |
| F7 | COMPLETING → COMPLETED transition fails (DB error) | UNKNOWN (could be COMPLETING stuck) | Uploaded bytes exist | None | Uploaded bytes exist | Manual intervention | **NOT idempotent** — no guard against re-entry |
| F8 | Concurrent finishRenderPhase for same job | Both COMPLETED | Two uploads, second overwrites URI | None (main path) | Two sets of uploaded bytes | Cleanup orphan from first upload | **NOT idempotent** |
| F9 | JVM crash after upload, before COMPLETED | EXECUTING (stale) | Uploaded bytes exist | None | Uploaded bytes exist | Stale job detector re-runs; will re-upload | **NOT idempotent** |
| F10 | Product registration fails (RenderOutputRegistrationService) | N/A (separate path) | N/A | None (rolled back in @Transactional) | StorageReference rolled back | Safe — single transaction | **Idempotent** (within single txn) |

### 5.2 Failure Window Details

#### F5: Storage Upload Failure During COMPLETING

**Code path**: `RenderJobExecutionService.finishRenderPhaseInternal()` lines 399–407

```java
try {
    artifactStorageService.uploadJobOutput(jobId, projectId, artifactId, relativePath, contentType);
} catch (Exception e) {
    failureService.recordDurableFailure(jobId, "Storage failed: " + e.getMessage());
    throw new IllegalStateException("Storage failed", e);
}
```

**Problem**: If the upload partially succeeds (e.g., bytes written to blob storage but the metadata registration fails), the `failureService.recordDurableFailure()` commits the job to FAILED in REQUIRES_NEW, but the uploaded bytes are orphaned. On retry, `uploadJobOutput()` will re-upload, creating duplicate bytes.

**Required fix**: Upload must be idempotent — either by using content-addressable keys (checksum-based) or by checking for existing upload before writing.

#### F7: Stuck COMPLETING State

**Code path**: If `updateStatus(jobId, projectId, COMPLETING, COMPLETED, null)` at line 449 throws (e.g., DB connection lost), the job is stuck in COMPLETING with no recovery path.

**Problem**: 
- `markActiveJobFailed` CAS includes COMPLETING as a valid source state (line 170), so the failure service CAN transition it to FAILED.
- But there's no automatic stale-COMPLETING detector. The `findStaleExecutingJobs()` method only looks for EXECUTING, not COMPLETING.

**Required fix**: Either:
1. Extend stale job detector to include COMPLETING state
2. Add a COMPLETING timeout (e.g., 5 minutes) after which the job is force-failed

#### F8: Concurrent finishRenderPhase

**Code path**: Both calls reach `finishRenderPhaseInternal()`. The only guard is `if (COMPLETED) return jobId` at line 329. If both calls read the job status as EXECUTING before either writes COMPLETING, both proceed.

**Problem**: No CAS on the EXECUTING → COMPLETING transition. The `updateStatus()` method at line 630 is a plain `UPDATE SET status = ? WHERE id = ?` with no WHERE clause on the current status.

**Required fix**: 
```sql
UPDATE render_job SET status = 'COMPLETING' 
WHERE id = ? AND status = 'EXECUTING'
```
This must return 1 row affected; if 0, another thread already claimed the transition.

---

## 6. Exact Unique Constraints Needed

### 6.1 For Durable Idempotency

| Table | Constraint | Columns | Purpose |
|-------|------------|---------|---------|
| **render_output** (NEW) | `uq_render_output_job_type` | `(render_job_id, output_type)` | One output per job per type |
| **product** | `uq_product_render_job_type` | `(render_job_id, product_type)` WHERE render_job_id IS NOT NULL | One product per job per type (if Option B chosen) |

### 6.2 For Transaction Safety

| Table | Constraint | Columns | Purpose |
|-------|------------|---------|---------|
| **render_job** | CAS on status update | `WHERE id = ? AND status = ?` | Prevent concurrent transitions |

### 6.3 For Storage Deduplication (Already Exists)

| Table | Constraint | Columns | Status |
|-------|------------|---------|--------|
| **storage_reference** | `uq_storage_path` | `(provider_type, root_path, relative_path)` | ✅ Exists |

### 6.4 For Product Deduplication (Missing)

| Table | Constraint | Columns | Purpose |
|-------|------------|---------|---------|
| **product** | None currently | — | Product.save() upserts by product_id only. No deduplication by semantic identity. |

---

## 7. Transaction Boundary Analysis

### 7.1 Current Transaction Boundaries

| Operation | Transaction | Propagation |
|-----------|------------|-------------|
| `claimForSelection()` | RenderJobClaimService | `REQUIRES_NEW` ✅ |
| `recordDurableFailure()` | RenderJobFailureService | `REQUIRES_NEW` ✅ |
| `execute()` | RenderJobExecutionService | Default (`REQUIRED`) |
| `finishRenderPhase()` | RenderJobExecutionService | `@Transactional` (Default) |
| `finishRenderPhaseInternal()` | Called from execute() or finishRenderPhase() | Inherits caller's txn |
| `RenderOutputRegistrationService.registerOutput()` | `@Transactional` | Default |

### 7.2 Problematic Boundaries

1. **`finishRenderPhaseInternal()` is NOT independently transactional when called from `execute()`**: It inherits the outer transaction from `execute()`. If any step after the claim fails, the entire outer transaction rolls back — but the claim was committed in REQUIRES_NEW, so the job is stuck in SELECTING_PROVIDER.

2. **`updateStatus()` (line 630) is NOT in its own transaction**: It's a plain jOOQ `UPDATE` within the caller's transaction. If the transaction rolls back, the status change rolls back too. This is correct for consistency but means the status change is not durable until the entire `finishRenderPhaseInternal` completes.

3. **`RenderOutputRegistrationService.registerOutput()`** runs in its own `@Transactional`: If it's called from within `finishRenderPhaseInternal()`, it would join the outer transaction (REQUIRED propagation). If called independently (from CaptionTemplateRenderService), it has its own transaction.

### 7.3 Recommended Transaction Architecture

```
┌─────────────────────────────────────────────────────┐
│ execute() — outer transaction                        │
│  ┌─────────────────────────────────────────────────┐ │
│  │ claimForSelection() — REQUIRES_NEW (committed)  │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ resolveScript + provider selection               │ │
│  │ updateStatus(SELECTING_PROVIDER → EXECUTING)     │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ provider.render()                                │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ COMPLETING phase (must be atomic):               │ │
│  │   CAS: EXECUTING → COMPLETING                    │ │
│  │   uploadJobOutput()                              │ │
│  │   insert render_output (uq constraint)           │ │
│  │   CAS: COMPLETING → COMPLETED                    │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ recordDurableFailure() — REQUIRES_NEW (on error) │ │
│  └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## 8. Summary of Schema Gaps

| Gap | Impact | Fix |
|-----|--------|-----|
| No `render_output` table | Cannot enforce one-output-per-job | Create table with UNIQUE(render_job_id, output_type) |
| No CAS on EXECUTING → COMPLETING | Concurrent double-finalization possible | Change `updateStatus` to include `WHERE status = current_status` |
| No CAS on COMPLETING → COMPLETED | Stuck COMPLETING possible | Add `WHERE status = 'COMPLETING'` to the COMPLETED transition |
| `render_job.updated_at` missing from DDL | CAS queries reference non-existent column | Add column via migration OR remove from CAS queries |
| No stale-COMPLETING detector | Jobs stuck in COMPLETING have no recovery | Extend `findStaleExecutingJobs` to include COMPLETING |
| No `render_job_id` on `product` table | Cannot link Product back to RenderJob | Add column + optional unique index |
| No `idempotency_key` on `render_job` | Cannot deduplicate job submission | Add column + unique index (if needed) |
| Product.save() upserts by PK only | No semantic deduplication | Use `ON CONFLICT (render_job_id, product_type)` if column added |
| `computeContentHash` uses URI hashCode | Not a real content hash | Replace with SHA-256 of actual file bytes |

---

## 9. Prior Report Accuracy Summary

| Section | Accuracy | Notes |
|---------|----------|-------|
| §1 Entity Inventory | **100%** | All entity descriptions match current source |
| §2 Lifecycle Coupling Map | **100%** | Flow descriptions verified |
| §3 Exact Current Conditions | **100%** | Code references verified |
| §4 Identified Gaps | **95%** | All 6 gaps confirmed; N1 (missing updated_at) was not caught |
| §5 Test Design | **100%** | Test designs are valid against current source |
| §6 Test Implementation Strategy | **100%** | Strategy is sound |
| §7 Summary of Findings | **100%** | All findings confirmed |

**Overall**: The prior Agent C report is highly accurate. This review adds 6 new findings (N1–N6) and provides the failure-window matrix and schema gap analysis required for the Render Output Commit Protocol.
