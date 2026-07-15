# Agent A — Document Consistency Review

## Review Date

2026-07-15

## Reviewer

Agent A: Document Consistency Audit

## Scope

All Render Output Commit architecture artifacts reviewed against the frozen two-table model:

- `render_output_commit` — one record per RenderJob, `UNIQUE(render_output_commit.render_job_id)`
- `render_output_item` — one record per output role per commit, `UNIQUE(output_commit_id, output_role)`

## Frozen Model Summary

| Table | Cardinality | Key Constraint |
|-------|-------------|----------------|
| `render_output_commit` | 1:1 with RenderJob | `UNIQUE(render_job_id)` |
| `render_output_item` | 1:N with render_output_commit | `UNIQUE(output_commit_id, output_role)` |

Confirmed canonical by:
- ADR-026 lines 49–50, 59–68, 74–82 (correct two-table model)
- Closeout note lines 19–21, 46–48 (explicit single→two-table migration)
- Schema proposal lines 5–73 (DDL with correct tables and constraints)

---

## ERR-01 — ADR-026 Schema Implications: Stale Single-Table `UNIQUE(render_job_id, output_type)`

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Lines 236–240 (Schema Implications section)
**Severity:** HIGH — contradicts the frozen model

**Current text:**
```text
New table: render_output
New columns: render_job.idempotency_key
New constraints:
  - UNIQUE(render_job_id, output_type)
```

**Problem:**
1. References a single table `render_output` instead of `render_output_commit` + `render_output_item`.
2. Uses `UNIQUE(render_job_id, output_type)` — the stale composite key that allows multiple commits per RenderJob (one per output_type). This directly contradicts the frozen model's `UNIQUE(render_job_id)` on `render_output_commit`.
3. `output_type` does not exist in the frozen model; the equivalent concept is `output_role` on `render_output_item`.

**Required correction:**
```text
New table: render_output_commit (one per RenderJob)
New table: render_output_item (one per output role per commit)
New columns: render_job.idempotency_key
New constraints:
  - UNIQUE(render_output_commit.render_job_id)
  - UNIQUE(render_output_item.output_commit_id, output_role)
```

---

## ERR-02 — ADR-026 Transaction Boundaries: Stale Generic `render_output` Table Name

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Lines 209, 211, 213 (Transaction Boundaries section)
**Severity:** MEDIUM — ambiguous under two-table model

**Current text:**
```text
├── render_output INSERT [short transaction, PENDING]
├── StorageReference + Artifact + render_output COMMITTED [short transaction]
└── failure at any point → render_output FAILED + RenderJob FAILED [REQUIRES_NEW]
```

**Problem:** `render_output` is ambiguous. Under the two-table model, the INSERT step targets `render_output_commit` (plus individual `render_output_item` rows for each output). The COMMITTED transition targets `render_output_commit.status`. The FAILED transition targets `render_output_commit.status`.

**Required correction:** Replace all `render_output` with `render_output_commit` (or `render_output_commit + render_output_item` where item-level operations are implied).

---

## ERR-03 — ADR-026 Completion/Failure Invariants: Stale Generic `render_output` References

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Lines 89–90, 103–106 (Completion Invariant, Failure Invariant)
**Severity:** MEDIUM — ambiguous under two-table model

**Current text (Completion):**
```text
1. render_output.status = COMMITTED
2. render_output.committed_at IS NOT NULL
```

**Current text (Failure):**
```text
- render_output.status = FAILED (if exists)
```

**Problem:** `render_output` is ambiguous. These invariants apply to `render_output_commit.status` and `render_output_commit.committed_at`.

**Required correction:** Replace `render_output` with `render_output_commit` in all invariant statements.

---

## ERR-04 — ADR-026 Duplicate Finalization: Stale Generic `render_output` Reference

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Line 196 (Duplicate Finalization section)
**Severity:** LOW — ambiguous under two-table model

**Current text:**
```text
Second call: INSERT INTO render_output ... ON CONFLICT DO NOTHING
```

**Problem:** Under the two-table model, the idempotent INSERT targets `render_output_commit` (ON CONFLICT on `render_job_id`) and `render_output_item` (ON CONFLICT on `output_commit_id, output_role`).

**Required correction:** Replace `render_output` with `render_output_commit` (and optionally note `render_output_item`).

---

## ERR-05 — ADR-026 Blob Ownership Protocol: Stale Generic `render_output` Reference

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Line 139 (Blob Ownership Protocol section)
**Severity:** LOW — ambiguous under two-table model

**Current text:**
```text
- Owned by render_output record
```

**Problem:** Blob ownership in the frozen model is tracked via `render_output_item.object_identity` and `render_output_item.storage_reference_id`, not a single `render_output` record.

**Required correction:** Replace with `render_output_item record` or clarify that ownership is per-item.

---

## ERR-06 — ADR-026 Migration Strategy: Stale Single-Table Reference

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Line 248 (Migration Strategy section)
**Severity:** LOW — ambiguous under two-table model

**Current text:**
```text
1. render_output table
```

**Problem:** Should reference both `render_output_commit` and `render_output_item` tables.

**Required correction:**
```text
1. render_output_commit table
2. render_output_item table
```

---

## ERR-07 — ADR-026 V5 Migration Section: Missing `render_output_item` Table

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Location:** Line 309 (Implementation Phases section)
**Severity:** LOW — incomplete under two-table model

**Current text:**
```text
| 1 | Schema: render_output table + constraints | V5 |
```

**Problem:** Only references one table. Under the frozen model, Phase 1 creates both `render_output_commit` and `render_output_item`.

**Required correction:**
```text
| 1 | Schema: render_output_commit + render_output_item tables + constraints | V5 |
```

---

## ERR-08 — Target State: Stale `UNIQUE(render_job_id, output_type)` Constraint (Two Occurrences)

**Document:** `docs/architecture/target/render-output-commit-target-state.md`
**Location:** Lines 7 and 86
**Severity:** HIGH — directly contradicts frozen model

**Current text (line 7):**
```text
RenderOutputCommit — one record per RenderJob
UNIQUE(render_job_id, output_type)
```

**Current text (line 86):**
```text
-- One output per RenderJob per type
UNIQUE(render_job_id, output_type)
```

**Problem:** `UNIQUE(render_job_id, output_type)` allows multiple commits per RenderJob (one per output type). The frozen model enforces exactly one commit per RenderJob via `UNIQUE(render_job_id)`. Multiple outputs are modeled as child `render_output_item` records.

**Required correction:**
```text
UNIQUE(render_job_id)
```
And remove the comment "One output per RenderJob per type" which implies the composite key semantics.

---

## ERR-09 — Target State: Stale Generic `render_output` References (9 Occurrences)

**Document:** `docs/architecture/target/render-output-commit-target-state.md`
**Location:** Lines 27–28, 38, 43, 48, 73, 100–101, 109
**Severity:** MEDIUM — ambiguous under two-table model

**All affected lines:**
```text
Line 27: ├── 5. render_output INSERT [short transaction]
Line 28: │   └── INSERT INTO render_output (status=PENDING)
Line 38: │   ├── render_output: PENDING → COMMITTED
Line 43: │   ├── Billing consume (idempotent via render_output.id)
Line 48:     ├── render_output: → FAILED
Line 73: REGISTERED → READY (after render_output COMMITTED)
Line 100: Ownership: render_output record
Line 101: User-visible: Only after render_output COMMITTED
Line 109: - render_output INSERT: ON CONFLICT DO NOTHING (returns existing)
```

**Problem:** All references to `render_output` are ambiguous. Under the two-table model:
- INSERT targets `render_output_commit` + `render_output_item`
- State transitions target `render_output_commit.status`
- Billing idempotency key is `render_output_commit.id`
- Ownership is per `render_output_item`

**Required correction:** Replace each `render_output` with the specific table name (`render_output_commit` or `render_output_item`) as appropriate.

---

## ERR-10 — Failure Window Matrix: All `render_output` References Ambiguous (10 Occurrences)

**Document:** `docs/architecture/target/render-output-commit-failure-window-matrix.md`
**Location:** Lines 5–13 (DB State column), lines 19–20 (Key Invariants)
**Severity:** MEDIUM — ambiguous under two-table model

**All affected lines:**
```text
Line 5:  | No render_output |
Line 6:  | No render_output |
Line 7:  | render_output PENDING |
Line 8:  | render_output PENDING |
Line 9:  | render_output PENDING |
Line 10: | render_output PENDING |
Line 11: | render_output PENDING |
Line 12: | render_output COMMITTED |
Line 13: | render_output exists |
Line 19: No blob is user-visible before render_output COMMITTED
Line 20: No Product is READY before render_output COMMITTED
```

**Problem:** Under the two-table model, DB state should distinguish:
- `render_output_commit` status (PENDING/COMMITTED/FAILED)
- `render_output_item` presence/absence
- The "exists" check on line 13 should reference `render_output_commit`

**Required correction:** Replace `render_output` with `render_output_commit` in all DB State and invariant references.

---

## ERR-11 — Verification Contract: Generic `render_output records = 1`

**Document:** `docs/architecture/target/render-output-commit-verification-contract.md`
**Location:** Line 33 (Test B: Duplicate Finalization)
**Severity:** LOW — ambiguous under two-table model

**Current text:**
```text
render_output records = 1
```

**Problem:** Under the two-table model, the assertion should specify `render_output_commit records = 1`. Optionally, assert `render_output_item records = N` (one per expected output role).

**Required correction:**
```text
render_output_commit records = 1
```

---

## ERR-12 — Implementation Roadmap: Generic `render_output` in Phase 4

**Document:** `docs/architecture/target/render-output-commit-implementation-roadmap.md`
**Location:** Line 36 (Phase 4 description)
**Severity:** LOW — ambiguous under two-table model

**Current text:**
```text
Scope: Artifact + Product creation coupled to render_output
```

**Problem:** Under the two-table model, coupling is to `render_output_commit` (publication trigger) and `render_output_item` (per-artifact linkage).

**Required correction:**
```text
Scope: Artifact + Product creation coupled to render_output_commit / render_output_item
```

---

## ERR-13 — DETERMINISTIC_OUTPUT_CONFLICT: Not Formally Defined as Error Code

**Documents:** All
**Severity:** MEDIUM — standardization gap

**Findings:**
- ADR-026 line 148: "FAIL with deterministic-output conflict" (informal English, no error code)
- Closeout note line 39: "Different checksum: FAIL with conflict" (informal, no error code)
- `DETERMINISTIC_OUTPUT_CONFLICT` literal: **not found in any document or source file**
- No Java enum, constant, or failure code defines this error

**Problem:** The task requires that "different-checksum uses DETERMINISTIC_OUTPUT_CONFLICT" as a formal error code/failure_code value. Currently this is described only informally in prose. The `render_output_commit.failure_code` column exists in the schema proposal (line 16) but no document specifies the canonical set of failure codes.

**Required action:** Define `DETERMINISTIC_OUTPUT_CONFLICT` as a formal `failure_code` value in the schema proposal or ADR-026, used when `render_output_item.content_checksum_sha256` differs from the existing blob at the same deterministic key.

---

## ERR-14 — ADR-026 Internal Inconsistency: Sections Contradict Each Other

**Document:** `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
**Severity:** HIGH — document is self-contradictory

**Contradiction:**
- Lines 49–50 (Canonical Authority): Correctly describes two-table model with `render_output_commit` and `render_output_item`
- Lines 59–68 (Canonical Authority block): Correctly shows `UNIQUE(render_output_commit.render_job_id)` and `UNIQUE(render_output_item.output_commit_id, output_role)`
- Lines 74–82 (One-Output Invariant): Correctly enforces the two-table model
- **Lines 236–240 (Schema Implications):** Describes a single `render_output` table with `UNIQUE(render_job_id, output_type)` — **contradicts all of the above**

The same ADR simultaneously contains the correct two-table model (lines 49–82) and the stale single-table model (lines 236–240). This means the ADR was partially updated during the single→two-table migration but not fully reconciled.

**Required correction:** Update Schema Implications (lines 236–240) to match the frozen two-table model already present in lines 49–82.

---

## Consistent Documents

### Closeout Note — ✅ CONSISTENT

**Document:** `docs/architecture/target/render-output-commit-protocol-closeout.md`

Correctly documents the single→two-table migration:
- Line 19: `BEFORE: UNIQUE(render_job_id, output_type)` (old)
- Line 20: `AFTER: UNIQUE(render_output_commit.render_job_id)` (correct)
- Lines 46–48: Correctly shows the table split

### Schema Proposal — ✅ CONSISTENT

**Document:** `docs/architecture/target/render-output-commit-schema-proposal.md`

Correctly defines the two-table DDL:
- `render_output_commit` with `UNIQUE(render_job_id)` (line 31)
- `render_output_item` with `UNIQUE(output_commit_id, output_role)` (line 67)
- All field descriptions use correct table names

### Current State — ✅ CONSISTENT (N/A)

**Document:** `docs/architecture/current/render-output-commit-current-state.md`

Describes the pre-protocol current state. Does not reference the target two-table model, so no inconsistency.

---

## Summary Table

| Errata ID | Document | Severity | Type |
|-----------|----------|----------|------|
| ERR-01 | ADR-026 | HIGH | Stale UNIQUE(render_job_id, output_type) in Schema Implications |
| ERR-02 | ADR-026 | MEDIUM | Stale generic `render_output` in Transaction Boundaries |
| ERR-03 | ADR-026 | MEDIUM | Stale generic `render_output` in Completion/Failure Invariants |
| ERR-04 | ADR-026 | LOW | Stale generic `render_output` in Duplicate Finalization |
| ERR-05 | ADR-026 | LOW | Stale generic `render_output` in Blob Ownership |
| ERR-06 | ADR-026 | LOW | Stale single-table reference in Migration Strategy |
| ERR-07 | ADR-026 | LOW | Missing render_output_item in Implementation Phases |
| ERR-08 | Target State | HIGH | Stale UNIQUE(render_job_id, output_type) (2 occurrences) |
| ERR-09 | Target State | MEDIUM | Stale generic `render_output` (9 occurrences) |
| ERR-10 | Failure Matrix | MEDIUM | Stale generic `render_output` (10 occurrences) |
| ERR-11 | Verification Contract | LOW | Generic `render_output records = 1` |
| ERR-12 | Roadmap | LOW | Generic `render_output` in Phase 4 |
| ERR-13 | All | MEDIUM | DETERMINISTIC_OUTPUT_CONFLICT not formally defined |
| ERR-14 | ADR-026 | HIGH | Internal self-contradiction (correct § vs stale §) |

**Totals:** 14 errata items — 4 HIGH, 5 MEDIUM, 5 LOW

---

## Cross-Check: One RenderOutputCommit per RenderJob

| Document | Correct? | Evidence |
|----------|----------|----------|
| ADR-026 (§Canonical Authority) | ✅ | `UNIQUE(render_output_commit.render_job_id)` line 60 |
| ADR-026 (§Schema Implications) | ❌ | `UNIQUE(render_job_id, output_type)` line 240 |
| Closeout Note | ✅ | `UNIQUE(render_output_commit.render_job_id)` line 20 |
| Schema Proposal | ✅ | `UNIQUE(render_job_id)` line 31 |
| Target State | ❌ | `UNIQUE(render_job_id, output_type)` lines 7, 86 |

## Cross-Check: One-to-Many RenderOutputItems

| Document | Correct? | Evidence |
|----------|----------|----------|
| ADR-026 (§Canonical Authority) | ✅ | `UNIQUE(render_output_item.output_commit_id, output_role)` line 67 |
| Closeout Note | ✅ | `UNIQUE(output_commit_id, output_role)` line 48 |
| Schema Proposal | ✅ | `UNIQUE(output_commit_id, output_role)` line 67 |
| Target State | ✅ (no items mentioned) | N/A — target-state does not discuss items |

## Cross-Check: Different-Checksum Uses DETERMINISTIC_OUTPUT_CONFLICT

| Document | Correct? | Evidence |
|----------|----------|----------|
| ADR-026 | ⚠️ | "FAIL with deterministic-output conflict" (line 148) — informal, no formal error code |
| Closeout Note | ⚠️ | "FAIL with conflict" (line 39) — informal, no formal error code |
| Source Code | ❌ | `DETERMINISTIC_OUTPUT_CONFLICT` not found anywhere |
| Schema Proposal | N/A | `failure_code` column exists but no enum defined |
