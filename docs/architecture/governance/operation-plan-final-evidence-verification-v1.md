---
type: architecture-governance-record
milestone: OPTM-EV
name: OPERATION_PLAN_TRANSACTION_FINAL_EVIDENCE_VERIFICATION_V1
status: CLOSED
date: 2026-08-15
authority: OPERATION_PLAN_TRANSACTION_BOUNDED_ARCHITECTURE_CONTRACT_V1 + OPC1-OPC3 + RBC1-RBC3
---

# OPERATION_PLAN_TRANSACTION_FINAL_EVIDENCE_VERIFICATION_V1

## EV1 — HEAD FK + CAS INSERT ORDER (classification: FAIL_IMPLEMENTATION_DEFECT -> corrected)
- timeline_revision_ref (V2 migration): project_id/ref_id PK, head_revision_id
  (nullable, FK -> timeline_revision(id)), version, updated_at.
- Original FK: IMMEDIATE (PostgreSQL default). Actual apply order: CAS writes
  new revision id into head BEFORE the revision INSERT => immediate FK would
  reject every production apply (test DDL lacked the FK, masking the defect).
- Correction (EV1_C, minimal): V3 migration drops + recreates
  fk_timeline_revision_ref_head as DEFERRABLE INITIALLY DEFERRED. CAS-before-insert
  is valid inside one transaction; FK is validated at COMMIT and remains active
  (probe proves COMMIT fails when the INSERT is omitted and rolls back to the
  original head). NO production apply-code change required.
- Actual non-no-op order: idempotency lookup -> auth/context verify -> reserve
  apply_command -> newRevisionId -> CAS(newHead WHERE expected) -> INSERT
  revision (parent = plan base) -> durable result -> commit.
- Actual no-op order: idempotency lookup -> auth/context verify -> reserve
  apply_command -> CAS(version bump only, head unchanged, WHERE expected) ->
  durable NO_OP result -> commit. NO_OP never writes a new revision id and
  never advances head.
- Concurrent writer test now uses separate DSLContext per thread (true
  concurrent connections): exactly 1 success + 1 stale + 1 child.
- FK probes: (a) CAS-to-ghost-id without INSERT => COMMIT fails with
  fk_timeline_revision_ref_head violation, rollback restores head; (b)
  CAS-before-insert then INSERT => COMMIT succeeds, head points at new revision.

## EV2 — CREDENTIAL RESIDUE (PASS)
CREDENTIAL_RESIDUE_FINAL = 0 (real credentials). pfirr1RemediationCheck PASS.
Broad pattern scan found exactly 2 non-credential literals: documentation
example (password='change-me') and a secrets-scanner test fixture
(AKIAIO...MPLE with ellipsis). No secret values printed; no evidence leakage.

## Backend principles (unchanged)
RBC1/RBC2/RBC3 remain frozen. PostgreSQL conformance re-proven on real DB
(one-winner CAS, stale-head, durable + principal-bound idempotency, NO_OP
stale-head, transaction rollback, FK integrity). Domain leakage 0; JGit NO;
alternative backend NO. POSTGRESQL_VERSION = 16 (testcontainers).

## Verification
22 plan/IT tests PASS (real PostgreSQL, incl. 2 FK probes + true-concurrency
writer test); schema-governance suite PASS (V1+V2+V3); full suite 7120 GREEN
(0 failures/0 errors); drift 147/147; bootJar; pfirr1; Modulith.
Blockers = 0. Escalation = NONE.

## Finalization
HEAD_FK_AND_CAS_INSERT_ORDER_PROOF = PASS
CREDENTIAL_RESIDUE_FINAL_ZERO_PROOF = PASS
FINAL_EVIDENCE_VERIFICATION = PASS
OPERATION_PLAN_TRANSACTION_MODEL_V1_FINALIZATION = CLOSED
NEXT_ACTION = REVISION_COMMAND_MODEL_V1_DECISION_RECOVERY
