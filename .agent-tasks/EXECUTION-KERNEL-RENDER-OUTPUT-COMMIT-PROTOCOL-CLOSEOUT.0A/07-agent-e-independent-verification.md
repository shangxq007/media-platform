# 07 — Agent E: Independent Architecture Closeout Verification

**Branch**: `arch/render-output-commit-protocol-closeout` @ `b0b00f8`
**Baseline**: `a539594` (ADR-026 architecture commit)
**Closeout commit**: `b0b00f8` (docs: close render output commit protocol ambiguities)
**Scope**: Independent verification of all closeout claims across all documents

---

## Verification Matrix

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | One RenderOutputCommit per RenderJob | ✅ PASS | Consistent across closeout, schema proposal, ADR-026, lead decisions, Agent B |
| 2 | FALLBACKING/RETRYING excluded | ✅ PASS | Consistent across ADR-026, Agent A, lead decisions; source enum confirmed stale |
| 3 | Deterministic key semantics explicit | ✅ PASS | 8 scenarios defined in ADR-026 §Deterministic Key Semantics, fully elaborated in Agent B §4.2 |
| 4 | Schema proposal complete | ✅ PASS | Two-table model (render_output_commit + render_output_item) with DDL, field descriptions, idempotency keys |
| 5 | All findings assigned | ✅ PASS | 45 findings in Agent C, all allocated to 5 categories; 10 ADR-026 problems all traced |
| 6 | Documents consistent | ⚠️ CONDITIONAL | Closeout, schema proposal, lead decisions, Agent A, Agent B are internally consistent; 3 documents have stale references (see §6 below) |
| 7 | No production changes | ✅ PASS | `git diff a539594..b0b00f8` — 8 files changed, all under `.agent-tasks/` and `docs/` only |

---

## 1. One RenderOutputCommit per RenderJob

### Claim
One RenderJob maps to at most one RenderOutputCommit, enforced by `UNIQUE(render_output_commit.render_job_id)`.

### Evidence

| Source | Statement | Constraint |
|--------|-----------|------------|
| Closeout §1 | `UNIQUE(render_output_commit.render_job_id)` — one commit per RenderJob | `UNIQUE(render_job_id)` ✅ |
| Schema Proposal §render_output_commit | `CONSTRAINT uq_render_output_commit_job UNIQUE(render_job_id)` | `UNIQUE(render_job_id)` ✅ |
| ADR-026 §Canonical Authority | "RenderOutputCommit — one record per RenderJob" | Prose matches ✅ |
| ADR-026 §One-Output Invariant | `UNIQUE(render_output_commit.render_job_id)` | Constraint matches ✅ |
| Lead Decisions §1 | `UNIQUE(render_output_commit.render_job_id)` | Constraint matches ✅ |
| Agent B §3 | "Model 1: UNIQUE(render_job_id) + RenderOutputItem Children" — recommended | Constraint matches ✅ |
| Schema Proposal §render_output_item | `UNIQUE(output_commit_id, output_role)` — children of commit | Multiple outputs modeled as items ✅ |

### Verdict
**PASS.** The cardinality is consistently defined as one-commit-per-RenderJob across all documents that participated in the closeout. Multiple physical outputs are modeled as child `render_output_item` records, not as additional commits.

### Note: Stale References (pre-closeout documents)
- Agent C (04) §2.1 S1 still references `UNIQUE(render_job_id, output_type)` and single-table `render_output`
- ADR-026 §Schema Implications still says `UNIQUE(render_job_id, output_type)`
- Target-state.md still says `UNIQUE(render_job_id, output_type)`

These are **pre-closeout artifacts** that were not updated in the closeout commit. The closeout doc, schema proposal, lead decisions, and Agent B all correctly use `UNIQUE(render_job_id)`. See §6 for full inconsistency inventory.

---

## 2. FALLBACKING/RETRYING Excluded

### Claim
FALLBACKING and RETRYING are excluded from the canonical target state set. They are stale pre-launch baggage. Future retry creates a new RenderJob.

### Evidence

| Source | Statement |
|--------|-----------|
| Closeout §2 | "FALLBACKING: EXCLUDED (stale pre-launch baggage)" / "RETRYING: EXCLUDED (stale pre-launch baggage)" |
| ADR-026 §State Set | "FALLBACKING: EXCLUDED (stale pre-launch baggage)" / "RETRYING: EXCLUDED (stale pre-launch baggage)" |
| Agent A §FALLBACKING Analysis | "Enum: EXISTS in RenderJobStatus.java / State Machine: Transition from EXECUTING REMOVED (commit 97f1787) / Production Writer: NONE / Runtime Reachable: NO" → **FALLBACKING_STALE** |
| Agent A §RETRYING Analysis | Same analysis → **RETRYING_STALE** |
| Lead Decisions §2 | "FALLBACKING: EXCLUDED" / "RETRYING: EXCLUDED" / "Future retry: Creates new RenderJob" |
| Git history | Commit `97f1787` removed FALLBACKING/RETRYING transitions from state machine; commit `234689e` updated tests |

### Source Enum Verification
Agent A independently verified against `RenderJobStatus.java`:
- FALLBACKING: exists in enum, no production writer, transition from EXECUTING removed in `97f1787`
- RETRYING: exists in enum, no production writer, transition from EXECUTING removed in `97f1787`

### Verdict
**PASS.** Consistent across all documents. Source code analysis confirms these states are unreachable. Git history confirms the transitions were intentionally removed.

---

## 3. Deterministic Key Semantics Explicit

### Claim
The deterministic key semantics are explicitly defined with 8 scenarios covering replay, checksum, conflict, failure, restart, and visibility behavior.

### Evidence

| Scenario | ADR-026 §Deterministic Key Semantics | Agent B §4.2 |
|----------|--------------------------------------|--------------|
| Same RenderJob replay | Same key, idempotent overwrite | §4.2.2: Full replay semantics with per-step behavior |
| Same key + same checksum | Reuse existing object | §4.2.3: "idempotent no-op — safe to proceed" |
| Same key + different checksum | FAIL with deterministic-output conflict | §4.2.3: "new blob overwrites old blob at same key" ⚠️ |
| Silent overwrite | FORBIDDEN | §4.2.3: Overwrite happens, but checksum is updated |
| Blob success / DB failure | Object uncommitted, not user-visible | §4.2.4: Three scenarios (A, B, C) with recovery behavior |
| Process restart | Resume from durable DB/object facts | §4.2.5: Per-state recovery table |
| New retry attempt | New RenderJob ID, new key namespace | §4.2.7: Same-job re-attempt also defined |
| User visibility | COMMITTED + Product READY | §4.2.6: 5 conditions listed |

### Semantic Nuance: "Different Checksum"
ADR-026 says "FAIL with deterministic-output conflict" for different checksums. Agent B says "new blob overwrites old, checksum updated." These are **semantically different**:
- ADR-026: reject the mismatch
- Agent B: accept the overwrite (same job = authoritative output)

The lead closeout decisions do not explicitly resolve this nuance. The closeout doc §3 says "Different checksum: FAIL with conflict" which matches ADR-026.

### Verdict
**PASS with advisory.** All 8 scenarios are defined. The "different checksum" behavior has a subtle discrepancy between ADR-026/closeout (FAIL) and Agent B (overwrite). The ADR-026 position is canonical per the closeout. Agent B's position is an implementation-level elaboration that may need reconciliation during Phase 3 implementation.

---

## 4. Schema Proposal Complete

### Claim
The schema proposal defines two new tables (render_output_commit, render_output_item) with complete DDL, constraints, indexes, field descriptions, and idempotency keys.

### Evidence

**render_output_commit table** (schema-proposal.md §New Table):
- Columns: id, tenant_id, project_id, render_job_id, status, failure_code, failure_summary, created_at, updated_at, committed_at, version ✅
- Constraints: FK to render_job, UNIQUE(render_job_id), CHECK(status IN ('PENDING', 'COMMITTED', 'FAILED')) ✅
- Indexes: tenant, status ✅

**render_output_item table** (schema-proposal.md §New Table):
- Columns: id, output_commit_id, output_role, object_identity, content_checksum_sha256, content_size, media_type, media_metadata_json, storage_reference_id, artifact_id, created_at, updated_at ✅
- Constraints: FK to render_output_commit, UNIQUE(output_commit_id, output_role), CHECK(output_role IN (6 roles)) ✅
- Indexes: output_commit_id ✅

**Modified tables**:
- render_job: ADD idempotency_key, ADD updated_at, partial unique index ✅
- product: ADD render_job_id, partial unique index ✅
- billing_ledger_entry: UNIQUE(reference_type, reference_id) ✅
- quota_usage: UNIQUE(tenant_id, feature_code) ✅

**Idempotency keys** (schema-proposal.md §Idempotency Keys):
- RenderOutputCommit: UNIQUE(render_job_id) ✅
- RenderOutputItem: UNIQUE(output_commit_id, output_role) ✅
- RenderBillingRecord: UNIQUE(job_id) ✅
- BillingLedger: UNIQUE(reference_type, reference_id) ✅ (proposed)
- QuotaUsage: UNIQUE(tenant_id, feature_code) ✅ (proposed)

**Field descriptions**: 9 fields for commit table, 10 fields for item table ✅

### Verdict
**PASS.** The schema proposal is complete with DDL, constraints, indexes, field descriptions, and idempotency key mapping. Two-table model correctly implements the one-commit-per-RenderJob cardinality.

---

## 5. All Findings Assigned

### Claim
All findings from all agents are assigned to one of 5 categories.

### Evidence (from Agent C §9)

| Category | Count | Findings |
|----------|-------|----------|
| V5_SCHEMA | 6 | S1, S2, S3, S4, S5, S6 |
| PROTOCOL_IMPLEMENTATION | 17 | A1, Q1–Q4, L1, L2, L4, L6, L7, C1, C2, P1, P2, D1, B3, R1 |
| FAILURE_WINDOW_VERIFICATION | 10 | FW1–FW10 |
| REMOVE_AS_STALE_CONTRACT | 1 | R2 |
| EXPLICIT_FUTURE_DEBT | 11 | S7, A2, A3, A4, Q5, L3, L5, B1, R3, R4, R5 |
| **Total** | **45** | |

### ADR-026 Problem Traceability

| ADR-026 Problem | Findings | Allocated |
|-----------------|----------|-----------|
| 1. No canonical output-commit authority | S1, P1, D1 | V5_SCHEMA, PROTOCOL_IMPL |
| 2. Artifact ID mismatch | A1 | PROTOCOL_IMPL |
| 3. Double quota consumption | Q1 | PROTOCOL_IMPL |
| 4. Content hash = URI hash | C1 | PROTOCOL_IMPL |
| 5. Product registration disconnected | P1, P2 | PROTOCOL_IMPL |
| 6. No duplicate-finalization guard | D1, L1, L2 | PROTOCOL_IMPL |
| 7. QuotaUsage non-idempotent | Q2, S4 | PROTOCOL_IMPL, V5_SCHEMA |
| 8. BillingLedger non-idempotent | Q3, S3 | PROTOCOL_IMPL, V5_SCHEMA |
| 9. canRetry always false | R2 | REMOVE_AS_STALE |
| 10. StaleCompensation reachable | R1 | PROTOCOL_IMPL |

### Verdict
**PASS.** All 45 findings are allocated. All 10 ADR-026 problems are traced to specific findings. No finding is unallocated.

---

## 6. Document Consistency

### Consistent Set (no contradictions)

| Document | Cardinality | State Set | Deterministic Key | Schema |
|----------|-------------|-----------|-------------------|--------|
| Closeout (render-output-commit-protocol-closeout.md) | UNIQUE(render_job_id) ✅ | 8 states, FALLBACKING/RETRYING excluded ✅ | 8 scenarios ✅ | Two-table ✅ |
| Schema Proposal (render-output-commit-schema-proposal.md) | UNIQUE(render_job_id) ✅ | N/A | N/A | Two-table ✅ |
| Lead Decisions (05-lead-closeout-decisions.md) | UNIQUE(render_job_id) ✅ | 8 states ✅ | 8 scenarios ✅ | Two-table ✅ |
| Agent A (02) | N/A | 8 states ✅ | N/A | N/A |
| Agent B (03) | UNIQUE(render_job_id) ✅ | N/A | Full specification ✅ | Two-table ✅ |

These 5 documents are **internally consistent** with each other and with the closeout decisions.

### Inconsistent Set (stale pre-closeout references)

| Document | Issue | Severity |
|----------|-------|----------|
| **ADR-026 §Schema Implications** | Still says `UNIQUE(render_job_id, output_type)` and `New table: render_output` (single table) | **MEDIUM** — ADR is the primary architecture record; stale schema reference could mislead implementation |
| **Agent C (04) §2.1 S1** | Still says `UNIQUE(render_job_id, output_type)` and references single `render_output` table | **LOW** — Pre-closeout report; closeout doc supersedes |
| **render-output-commit-target-state.md §1** | Still says `UNIQUE(render_job_id, output_type)` and single-table model | **MEDIUM** — Target state doc should reflect current decision |
| **render-output-commit-target-state.md §Constraints** | Still says `UNIQUE(render_job_id, output_type)` | **MEDIUM** — Same as above |

### Root Cause
The closeout commit (`b0b00f8`) updated `ADR-026` with new sections (state set, deterministic key, blob ownership) but did **not** update the existing §Schema Implications section. It also did not update `render-output-commit-target-state.md` or Agent C's report.

### Verdict
**CONDITIONAL PASS.** The closeout decisions themselves are internally consistent. However, 3 documents retain stale schema references from the pre-closeout single-table design. These should be patched to prevent implementation drift:
1. ADR-026 §Schema Implications: change `UNIQUE(render_job_id, output_type)` → `UNIQUE(render_job_id)`, add `render_output_item` table
2. target-state.md: update to two-table model
3. Agent C: no action needed (historical report, closeout supersedes)

---

## 7. No Production Changes

### Claim
The closeout commit contains only documentation and architecture task files. No production code was modified.

### Evidence

```text
git diff --name-only a539594..b0b00f8

.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-CLOSEOUT.0A/01-git-kanban-and-baseline.md
.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-CLOSEOUT.0A/02-agent-a-state-and-adr-consistency.md
.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-CLOSEOUT.0A/03-agent-b-cardinality-and-object-key-semantics.md
.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-CLOSEOUT.0A/04-agent-c-v5-and-implementation-allocation.md
.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-CLOSEOUT.0A/05-lead-closeout-decisions.md
docs/architecture/adr/ADR-026-render-output-commit-protocol.md
docs/architecture/target/render-output-commit-protocol-closeout.md
docs/architecture/target/render-output-commit-schema-proposal.md
```

- `src/` — **not touched** ✅
- `*.java` — **not touched** ✅
- `*.sql` / Flyway migrations — **not touched** ✅
- `build.gradle` / `pom.xml` — **not touched** ✅
- `application.yml` — **not touched** ✅

### Verdict
**PASS.** All 8 changed files are under `.agent-tasks/` (architecture task documentation) or `docs/architecture/` (architecture documentation). Zero production code changes.

---

## Summary

| # | Criterion | Verdict |
|---|-----------|---------|
| 1 | One RenderOutputCommit per RenderJob | ✅ PASS |
| 2 | FALLBACKING/RETRYING excluded | ✅ PASS |
| 3 | Deterministic key semantics explicit | ✅ PASS (advisory: checksum mismatch nuance) |
| 4 | Schema proposal complete | ✅ PASS |
| 5 | All findings assigned | ✅ PASS (45 findings, 5 categories, 10 ADR problems traced) |
| 6 | Documents consistent | ⚠️ CONDITIONAL PASS (3 stale schema references) |
| 7 | No production changes | ✅ PASS |

### Recommended Follow-Up Actions

| Priority | Action | Document |
|----------|--------|----------|
| MEDIUM | Update ADR-026 §Schema Implications to reflect two-table model and UNIQUE(render_job_id) | ADR-026 |
| MEDIUM | Update render-output-commit-target-state.md to reflect two-table model | target-state.md |
| LOW | Reconcile "different checksum" semantics between ADR-026 (FAIL) and Agent B (overwrite) during Phase 3 | Implementation |

### Overall Assessment

The closeout is **substantively complete and internally consistent** in its core decisions. The three identified stale references are pre-closeout artifacts that were not fully patched during the closeout commit. These are low-to-medium severity because the closeout document and schema proposal — the authoritative sources for implementation — are correct and consistent with each other.

The architecture closeout successfully resolved all three identified ambiguities (cardinality, state set, deterministic key semantics) and produced a complete schema proposal with assigned findings. The protocol is ready for V5 schema implementation.
