---
type: architecture-governance-record
milestone: RC-FPC
name: REVISION_COMMAND_MODEL_V1_FINAL_POST_CLOSE_VERIFICATION_V1
status: CLOSED
date: 2026-08-15
authority: REVISION_COMMAND_BOUNDED_ARCHITECTURE_CONTRACT_V1 + RCP1-RCP3
---

# REVISION_COMMAND_MODEL_V1_FINAL_POST_CLOSE_VERIFICATION_V1

## RCP1 — MERGE_EXACT_SAME_REVISION_IS_NO_OP_V1
Classification: REAL_IMPLEMENTATION_DEFECT -> corrected.
Before: planner rejected sourceRevisionId == targetRevisionId as
INVALID_PARENT_SET (malformed). Now: plans semantic NO_OP (candidate hash =
target hash); apply returns NO_OP after database-enforced expected-head CAS —
no revision, no parent edges, no ref movement. First execution still validates
expected head (stale -> STALE_TARGET_REF); completed durable replay returns
original NO_OP. Same-ref-with-inconsistent-frozen-revisions remains a
resolution-layer malformed request (distinct from semantic self-merge).

## RCP2 — FROZEN_MERGE_PLAN_NEVER_REREADS_SOURCE_REF_AT_APPLY_V1
Classification: ALREADY_CORRECT (evidence added).
MergeRevisionPlan.sourceRevisionId is the sole apply-time source authority;
SOURCE_REF_READ_DURING_MERGE_APPLY_COUNT = 0 (static scan). Real-PG test:
plan pins feature=R90; feature advances to a new revision; old frozen plan
applies successfully with parents [target, R90] — pinned source, not the moved
head. Target movement remains STALE_TARGET_REF.

## RCP3 — PROJECT_REVISION_COUNTER_BOOTSTRAP_IS_ATOMIC_AND_RACE_SAFE_V1
Classification: ALREADY_CORRECT (evidence added).
Bootstrap = INSERT ... ON CONFLICT (project_id) DO NOTHING then atomic
UPDATE ... RETURNING (no process lock, no MAX+1 fallback). Migration
initializes coalesce(max(revision_number),0)+1 -> first post-migration
allocation strictly above historical max. Real-PG concurrent-first-allocation
test (counter row absent): both writers succeed, distinct numbers, exactly one
counter row.

## Verification
22 targeted IT PASS on real PostgreSQL (RCP1 A-D, RCP2 source-movement apply,
RCP3 bootstrap + migration invariant, full RevisionCommand regression).
Drift 168/168 (+6 RCFG). Full suite 7142 GREEN (0/0). bootJar, pfirr1,
Modulith PASS. Blockers = 0. Escalation = NONE.
REVISION_COMMAND_MODEL_V1_FINALIZATION = CLOSED.
NEXT_ACTION = ROADMAP_18_COLOR_IMAGE_FOUNDATION_DECISION_RECOVERY.
