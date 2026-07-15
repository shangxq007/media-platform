# Agent B: Cardinality Contradiction Resolution and DETERMINISTIC_FINAL_KEY Semantics

**Branch**: `arch/render-output-commit-protocol-closeout` @ `a539594`
**Scope**: Commit cardinality models, atomic publication, billing, duplicate finalization, deterministic key semantics

---

## 1. The Cardinality Contradiction

### 1.1 Where the Contradiction Lives

| Source | Claim |
|--------|-------|
| ADR-026 line 49 | "`UNIQUE(render_job_id, output_type)` guarantees one output per RenderJob" |
| ADR-026 line 58 | "RenderOutputCommit — one record per RenderJob per output type" |
| ADR-026 line 109 (final decision) | "A RenderJob has at most one canonical output commit" |
| Schema proposal line 48 | `UNIQUE(render_job_id, output_type)` |
| Target state line 6 | "RenderOutputCommit — one record per RenderJob" |

The prose says **one output per RenderJob**. The schema says **one output per (RenderJob, output_type)**. These are materially different:

- `UNIQUE(render_job_id)` → exactly 1 row per RenderJob
- `UNIQUE(render_job_id, output_type)` → 1 row per (RenderJob, output_type), so N rows per RenderJob if N output types exist

### 1.2 Why This Matters

The contradiction affects:

1. **Atomic publication**: Can a RenderJob have both a FINAL_RENDER and a PREVIEW output committed independently?
2. **Billing**: If two output_type rows exist, does billing fire once or twice?
3. **Duplicate finalization**: Is `ON CONFLICT DO NOTHING` on `(render_job_id, output_type)` sufficient, or does it allow a second commit for a different output_type?
4. **Completion invariant**: Does `RenderJob.status = COMPLETED` require ALL output_type rows to be COMMITTED, or just one?
5. **DETERMINISTIC_FINAL_KEY**: If `output.{format}` is the key suffix, what happens when format changes per output_type?

---

## 2. Model Comparison

### Model 1: UNIQUE(render_job_id) + RenderOutputItem Children

```
render_output (1 per RenderJob)
  PK: id
  UNIQUE(render_job_id)          ← exactly one commit row per job
  status: PENDING → COMMITTED

render_output_item (0..N per render_output)
  PK: id
  FK: render_output_id
  output_type: TEXT              ← FINAL_RENDER, PREVIEW, THUMBNAIL, etc.
  staged_object_locator
  committed_object_locator
  content_checksum
  content_size
```

**Pros**:
- Matches the prose invariant: "one commit per RenderJob"
- Atomic publication: the single render_output row transitions COMMITTED atomically
- Billing fires once per render_output.id
- Completion invariant is clean: `render_output.status = COMMITTED` means ALL items are committed
- Duplicate finalization: `ON CONFLICT(render_job_id) DO NOTHING` is a single-row guard

**Cons**:
- Requires two-table design (render_output + render_output_item)
- If one item fails but others succeed, the parent row has no clean status (need partial-failure semantics)
- More complex queries to check per-item status
- Items are not independently addressable for billing/reference

**Verdict**: Cleanest semantic model. One commit = one publication event. Items are children of that event.

### Model 2: UNIQUE(render_job_id, output_type) with Multiple Commits

```
render_output (N per RenderJob)
  PK: id
  UNIQUE(render_job_id, output_type)    ← one row per (job, type)
  status: PENDING → COMMITTED
```

**Pros**:
- Matches the schema proposal as written
- Each output type is independently committable
- Simpler single-table design
- Each row has its own storage_reference_id, artifact_id, product_id

**Cons**:
- **Violates the prose invariant**: "one commit per RenderJob" is false — there are N commits per RenderJob
- **Billing ambiguity**: Does `consumeQuota` fire once per job or once per output_type? Current billing uses `bill-{jobId}` as key, which assumes once-per-job
- **Completion invariant ambiguity**: Does `COMPLETED` require ALL output_types to be COMMITTED, or just FINAL_RENDER?
- **Duplicate finalization gap**: `ON CONFLICT(render_job_id, output_type) DO NOTHING` prevents duplicates per type, but allows a second commit for PREVIEW after FINAL_RENDER is already COMMITTED
- **DETERMINISTIC_FINAL_KEY collision**: `renders/{tenantId}/{jobId}/output.{format}` doesn't encode output_type — two different output types would overwrite each other's blob
- **Artifact identity collision**: Two output_types can't share the same artifact_id, so the "one canonical artifact" claim breaks

**Verdict**: The schema as written supports this model, but it contradicts the frozen architecture decision. If adopted, every downstream invariant must be rewritten to account for N outputs per job.

### Model 3: Hybrid — Single Commit Row + output_type as Metadata

```
render_output (1 per RenderJob)
  PK: id
  UNIQUE(render_job_id)
  primary_output_type: TEXT       ← FINAL_RENDER (the committed output)
  secondary_output_types: JSONB   ← optional metadata about other outputs
  status: PENDING → COMMITTED
```

**Pros**:
- Maintains "one commit per RenderJob"
- Stores output_type metadata without creating multiple commit rows
- Billing is unambiguous: one commit = one bill

**Cons**:
- Secondary outputs stored as JSONB lose relational integrity
- Can't independently query/reference secondary outputs
- Over-engineering: if only FINAL_RENDER is the committed output, why store others?

**Verdict**: Unnecessary complexity. If secondary outputs (PREVIEW, THUMBNAIL) need independent lifecycle, they should be separate Products/Artifacts, not part of the commit row.

---

## 3. Recommended Resolution

### Decision: Model 1 (UNIQUE(render_job_id) + RenderOutputItem Children)

**Rationale**:

1. **Matches the frozen architecture**: ADR-026 final decision states "A RenderJob has at most one canonical output commit." Model 1 enforces this at the DB level.

2. **Atomic publication**: The single render_output row is the atomic publication gate. When it transitions to COMMITTED, the job is done. No partial-commit ambiguity.

3. **Billing once**: `consumeQuota` fires once, keyed on `render_output.id`. No double-counting from multiple output_type rows.

4. **Duplicate finalization**: `ON CONFLICT(render_job_id) DO NOTHING` is a single-row guard. Second call returns existing record. No second commit possible.

5. **Item children for secondary outputs**: If PREVIEW or THUMBNAIL outputs exist, they are children of the single commit row. They don't independently trigger billing, completion, or publication.

6. **Schema correction needed**: The V5 schema proposal should change:
   ```sql
   -- BEFORE (current proposal):
   CONSTRAINT uq_render_output_job_type
       UNIQUE(render_job_id, output_type),

   -- AFTER (corrected):
   CONSTRAINT uq_render_output_job
       UNIQUE(render_job_id),
   ```

### Output Type Handling

| Output Type | Where It Lives | Billing Impact | Completion Gate |
|-------------|---------------|----------------|-----------------|
| FINAL_RENDER | render_output.committed_object_locator | Yes (once) | Yes |
| PREVIEW | render_output_item (child) | No | No |
| THUMBNAIL | render_output_item (child) | No | No |
| TIMELINE_JSON | ArtifactGraph node (existing) | No | No |

If secondary outputs need independent lifecycle (e.g., PREVIEW can be READY before FINAL_RENDER), they should be managed as separate Products via the existing Product/PipelineDagExecutor pattern, NOT as additional render_output rows.

---

## 4. DETERMINISTIC_FINAL_KEY Semantics

### 4.1 Current Definition (ADR-026)

```
Strategy: DETERMINISTIC_FINAL_KEY
Object key: renders/{tenantId}/{jobId}/output.{format}
- Deterministic per RenderJob
- Idempotent on replay
- Not user-visible until publication complete
- Owned by render_output record
```

### 4.2 Full Semantics Definition

#### 4.2.1 Key Format

```
renders/{tenantId}/{jobId}/output.{format}

Examples:
  renders/tenant-abc/job-123/output.mp4
  renders/tenant-abc/job-123/output.webm
  renders/tenant-abc/job-123/output.dash
```

**Components**:
- `renders/` — namespace prefix, isolates render outputs from other object types
- `{tenantId}/` — tenant isolation at the storage level
- `{jobId}/` — job isolation, deterministic per RenderJob
- `output.{format}` — canonical filename, format derived from RenderExecutionPlan output spec

#### 4.2.2 Replay Semantics

**Scenario**: Same RenderJob, same provider, same input, process restart or retry.

| Step | Behavior |
|------|----------|
| render_output INSERT | `ON CONFLICT(render_job_id) DO NOTHING` — returns existing row |
| Blob write | Idempotent overwrite to same key — same content produces same bytes |
| StorageReference | Upsert on same path — no duplicate |
| Artifact | Upsert on same artifact_id — no duplicate |
| Product | Upsert on same render_job_id — no duplicate |
| Billing | Upsert on `bill-{jobId}` — no double charge |

**Key property**: Replay of the same job is always safe. The deterministic key means the blob is overwritten in place, not orphaned or duplicated.

#### 4.2.3 Checksum Match/Mismatch

**Checksum computation**: SHA-256 of actual blob bytes, computed after upload completes (from storage ETag or read-back).

**Match (same content)**:
- Blob already exists at deterministic key
- New checksum matches existing checksum
- `render_output.content_checksum` is updated (or left as-is)
- No new Artifact or Product needed
- Behavior: **idempotent no-op** — safe to proceed

**Mismatch (different content)**:
- Blob already exists at deterministic key
- New checksum DOES NOT match existing checksum
- This means the RenderJob was re-executed with different input/script/provider
- **Resolution strategy**: The new blob **overwrites** the old blob at the same key
- `render_output.content_checksum` is updated to new value
- `render_output.committed_at` is refreshed
- Artifact content_hash is updated
- Product is updated (if already READY)
- **Rationale**: The deterministic key is owned by the RenderJob. If the job is re-executed, the new output is authoritative. The old blob is replaced, not preserved.

**Edge case — concurrent overwrite**:
- Two processes both execute the same RenderJob and write to the same key
- Last-writer-wins at the blob storage level
- DB row is protected by `UNIQUE(render_job_id)` — second INSERT does nothing
- If the first process committed successfully, the second process's blob write is orphaned (but safe — same key, will be overwritten on next replay)
- **Mitigation**: The claim mechanism (`REQUIRES_NEW` CAS) prevents two processes from executing the same job simultaneously

#### 4.2.4 DB Failure Scenarios

**Scenario A: DB fails after blob write, before render_output COMMITTED**

```
1. blob write to renders/{tenantId}/{jobId}/output.mp4  ✅
2. StorageReference + Artifact + render_output COMMITTED  ❌ DB failure
```

- Blob exists at deterministic key but is **unowned** — no render_output row in COMMITTED state
- On restart/replay: render_output is still PENDING (or doesn't exist)
- The blob is **safe to overwrite** — it's not user-visible (no COMMITTED render_output)
- Compensation service will eventually mark the job FAILED if it's stuck
- **No orphan cleanup needed** — deterministic key means replay overwrites

**Scenario B: DB fails after render_output COMMITTED, before Product READY**

```
1. render_output: PENDING → COMMITTED  ✅
2. Product create  ❌ DB failure
```

- render_output is COMMITTED but Product doesn't exist
- On restart: check if Product exists for this render_job_id
- If not: create Product (idempotent — UNIQUE constraint on product.render_job_id)
- **Gap**: Current code has no "catch up on incomplete publication" step
- **Mitigation**: The publication commit step should be atomic: `COMMITTED + Product READY` in one transaction

**Scenario C: DB fails during render_output INSERT**

```
1. INSERT INTO render_output ...  ❌ DB failure
```

- No render_output row exists
- On restart: job is in EXECUTING state
- Re-execute: new INSERT succeeds
- **Safe**: No partial state

#### 4.2.5 Restart Semantics

**Process restart after crash**:

| Job State at Crash | Recovery Action |
|--------------------|-----------------|
| QUEUED | Job remains QUEUED, eligible for claim |
| SELECTING_PROVIDER | Compensation marks FAILED (after timeout) |
| PROVIDER_SELECTED | **Gap**: Not targeted by compensation |
| EXECUTING | Compensation marks FAILED (after timeout) |
| COMPLETING | **Gap**: Not targeted by compensation |
| COMPLETED | No action needed |
| FAILED | No action needed |

**Key insight**: The deterministic key is safe across restarts because:
1. If the job wasn't completed, the blob is unowned (not user-visible)
2. If the job is retried, the blob is overwritten
3. If the job is marked FAILED, the blob is orphaned but harmless (same key, no references)

#### 4.2.6 Visibility Semantics

**User-visible**: Only when ALL of:
1. `render_output.status = COMMITTED`
2. `render_output.committed_at IS NOT NULL`
3. StorageReference exists and references the committed blob
4. Artifact is READY
5. Product is READY

**Pre-commit visibility**: The blob at the deterministic key is NOT user-visible before commit because:
- No StorageReference points to it
- No Artifact references it
- No Product exposes it
- No AccessDescriptor resolves to it

**Access control**: The deterministic key path (`renders/{tenantId}/{jobId}/`) provides tenant isolation at the storage level. Even if a user guesses another tenant's job ID, the bucket policy enforces tenant scoping.

#### 4.2.7 New Attempt Semantics

**Scenario**: RenderJob fails, user submits a new attempt (same job, new execution).

```
Attempt 1:
  1. FFmpeg renders to local file
  2. Upload to renders/{tenantId}/{jobId}/output.mp4
  3. render_output INSERT → PENDING
  4. Failure at step 5 → render_output FAILED, render_job FAILED

Attempt 2 (same job, re-execution):
  1. FFmpeg renders to local file (possibly different content)
  2. Upload to renders/{tenantId}/{jobId}/output.mp4 (overwrites attempt 1)
  3. render_output INSERT → ON CONFLICT DO NOTHING (existing row)
     - Need: UPDATE render_output SET status = PENDING WHERE id = existing_id
  4. Continue to COMMITTED
```

**Critical gap**: The current design uses `ON CONFLICT DO NOTHING` which returns the existing row. But if the existing row is FAILED, we need to **reset it to PENDING** before proceeding. The current protocol doesn't specify this.

**Proposed semantics**:
```sql
INSERT INTO render_output (id, render_job_id, status, ...)
VALUES (?, ?, 'PENDING', ...)
ON CONFLICT(render_job_id) DO UPDATE
SET status = 'PENDING',
    failure_code = NULL,
    failure_summary = NULL,
    updated_at = NOW()
WHERE render_output.status IN ('FAILED', 'PENDING')
```

This allows re-attempt by resetting a FAILED row to PENDING. A COMMITTED row is never reset (that would be a new RenderJob, not a re-attempt).

---

## 5. Evaluation Matrix

| Criterion | Model 1 (UNIQUE + Items) | Model 2 (Composite UNIQUE) | Model 3 (Hybrid) |
|-----------|--------------------------|---------------------------|-------------------|
| Atomic publication | ✅ Single row transition | ⚠️ Multiple rows, unclear gate | ✅ Single row |
| Multiple outputs | ✅ Item children | ✅ Native rows | ⚠️ JSONB metadata |
| Billing once | ✅ One commit = one bill | ❌ Ambiguous per-type billing | ✅ One commit = one bill |
| Duplicate finalization | ✅ Single ON CONFLICT | ⚠️ Per-type ON CONFLICT | ✅ Single ON CONFLICT |
| Matches ADR prose | ✅ "one commit per RenderJob" | ❌ "one commit per (job, type)" | ✅ "one commit per RenderJob" |
| Schema complexity | ⚠️ Two tables | ✅ Single table | ⚠️ Single table + JSONB |
| Independent output lifecycle | ⚠️ Requires separate Products | ✅ Native | ❌ JSONB not relational |
| DETERMINISTIC_FINAL_KEY | ✅ One key per job | ❌ Key collision per type | ✅ One key per job |

---

## 6. Recommended Schema Correction

```sql
-- CORRECTED: One render_output per RenderJob
CREATE TABLE render_output (
    id                    TEXT PRIMARY KEY,
    tenant_id             TEXT NOT NULL,
    project_id            TEXT NOT NULL,
    render_job_id         TEXT NOT NULL,
    output_type           TEXT NOT NULL DEFAULT 'FINAL_RENDER',
    status                TEXT NOT NULL DEFAULT 'PENDING',
    
    -- Storage
    staged_object_locator TEXT,
    committed_object_locator TEXT,
    content_checksum      TEXT,
    content_size          BIGINT,
    content_type          TEXT,
    
    -- References
    storage_reference_id  TEXT,
    artifact_id           TEXT,
    product_id            TEXT,
    
    -- Billing
    quota_operation_id    TEXT,
    billing_operation_id  TEXT,
    
    -- Failure tracking
    failure_code          TEXT,
    failure_summary       TEXT,
    
    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    committed_at          TIMESTAMPTZ,
    
    -- Versioning
    version               INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT fk_render_output_job 
        FOREIGN KEY (render_job_id) REFERENCES render_job(id) ON DELETE RESTRICT,
    CONSTRAINT uq_render_output_job 
        UNIQUE(render_job_id),           -- ← CHANGED: was (render_job_id, output_type)
    CONSTRAINT ck_render_output_status 
        CHECK (status IN ('PENDING', 'COMMITTED', 'FAILED'))
);
```

**Migration note**: If the system needs to track secondary outputs (PREVIEW, THUMBNAIL), add a separate `render_output_item` table or use the existing ArtifactGraph/PipelineDagExecutor pattern. Do NOT add rows to render_output for secondary output types.

---

## 7. DETERMINISTIC_FINAL_KEY — Complete Specification

```text
KEY FORMAT:
  renders/{tenantId}/{jobId}/output.{format}

DERIVATION:
  tenantId = RenderJob.tenant_id
  jobId    = RenderJob.id
  format   = RenderExecutionPlan.outputSpec.format (default: "mp4")

PROPERTIES:
  1. Deterministic: Same (tenantId, jobId, format) → same key, always
  2. Idempotent: Writing the same content to the same key is a no-op
  3. Owned: Only the render_output record for this jobId may reference this key
  4. Not user-visible: Until render_output.status = COMMITTED
  5. Safe to overwrite: Re-execution overwrites, no orphans
  6. Tenant-isolated: Bucket policy enforces tenant scoping

REPLAY:
  Same job, same content → blob overwrite + idempotent DB ops
  Same job, different content → blob overwrite + checksum update
  Different job → different key (jobId in path)

CHECKSUM:
  Algorithm: SHA-256 of actual bytes
  Source: Storage ETag or computed after upload
  Mismatch: Overwrites blob, updates checksum, refreshes committed_at
  Not a URI hash

DB FAILURE:
  Blob written, DB failed → blob unowned, safe to overwrite on replay
  render_output COMMITTED, Product missing → catch-up on restart
  INSERT failed → re-execute, new INSERT succeeds

RESTART:
  QUEUED → re-claimable
  SELECTING_PROVIDER/EXECUTING → compensation marks FAILED after timeout
  COMPLETING → gap (not targeted by compensation)
  COMPLETED → no action
  FAILED → no action

VISIBILITY:
  Pre-commit: Blob exists but no StorageReference/Artifact/Product → invisible
  Post-commit: All references created atomically → visible

NEW ATTEMPT:
  Same job, re-execution:
    - Blob overwrites at same key
    - render_output ON CONFLICT DO UPDATE resets FAILED → PENDING
    - Proceeds through normal commit flow
  COMMITTED rows are never reset (new attempt = new RenderJob)
```

---

## 8. Open Questions for Lead Decision

1. **Secondary outputs**: Should PREVIEW/THUMBNAIL be render_output_item children, separate Products via PipelineDagExecutor, or something else?

2. **Compensation gap**: COMPLETING and PROVIDER_SELECTED are not targeted by compensation. Should they be added, or is the claim timeout sufficient?

3. **Re-attempt semantics**: Should `ON CONFLICT DO UPDATE` reset FAILED → PENDING, or should re-attempt require a new RenderJob?

4. **Format change on re-attempt**: If a re-attempt uses a different format (e.g., mp4 → webm), the deterministic key changes. Is this acceptable, or should the key be format-independent?

---

## 9. Summary

| Finding | Resolution |
|---------|------------|
| Cardinality contradiction | Adopt Model 1: UNIQUE(render_job_id) with Item children |
| Schema correction | Change UNIQUE(render_job_id, output_type) → UNIQUE(render_job_id) |
| Atomic publication | Single render_output row, single transition to COMMITTED |
| Billing once | One commit = one bill, keyed on render_output.id |
| Duplicate finalization | ON CONFLICT(render_job_id) DO NOTHING, single-row guard |
| DETERMINISTIC_FINAL_KEY | Fully specified: format, replay, checksum, DB failure, restart, visibility, new attempt |
| Re-attempt gap | Proposed: ON CONFLICT DO UPDATE resets FAILED → PENDING |
| Compensation gap | COMPLETING and PROVIDER_SELECTED not targeted — needs decision |
