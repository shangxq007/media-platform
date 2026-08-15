---
type: architecture-governance-record
milestone: OPTM-PC
name: OPERATION_PLAN_TRANSACTION_POST_CLOSE_CORRECTION_V1
status: CLOSED
date: 2026-08-15
authority: OPERATION_PLAN_TRANSACTION_BOUNDED_ARCHITECTURE_CONTRACT_V1 + OPC1-OPC3 + RBC1-RBC3
---

# OPERATION_PLAN_TRANSACTION_POST_CLOSE_CORRECTION_V1

## Original implementation history (reconstructed, exact)
- INITIAL_IMPLEMENTATION_CANDIDATE = ce88fd0dc1c9523871cfb930167a8957bc13a9d9 (tree ff05ed9369aab45ba748e76696b8b363de0d940f)
- POST_CANDIDATE_CORRECTION_1 = 73781aa64b800f441c3941f0595d27d4b1a4b737 (tree 87e0c9d473f104b252c98a38cd156e78cefa8583) — schema-governance tests aligned to V2
- POST_CANDIDATE_CORRECTION_2 = 07ebd0ee6e314b652cc094f4e8e10a2a6d05e436 (tree b56d1523ffa014a1d1f86eea770c2ffd5dfeff96) — TemporalMapping polymorphic canonical JSON
- FINAL_ORIGINAL_FCV_CANDIDATE = 07ebd0ee (the exact implementation tip whose tree passed final FCV: 7113 GREEN, drift 139/139, bootJar, pfirr1)
- ORIGINAL_PUBLICATION = 056f8a964afe0bca89019ad0c75eb3f05a56a865 (tree 11ec7129affe9cba43e7d3ce9155264409c4437f)

## OPC1 — NO_OP still honors exact expected head
First-time NO_OP apply now performs the same database-enforced expected-head
validation (conditional UPDATE on timeline_revision_ref WHERE head = expected;
version bump only, head NOT advanced). 0 rows => STALE_TARGET_REF. A no-op
relative to R100 is not a no-op relative to a moved head R101. Completed
durable replays bypass the check and return the original result (historical
outcome preserved). Also fixed latent defect found during rework: normal apply
now CAS-advances head to the REAL new revision id (previously the conditional
UPDATE passed null for the new head). Pipeline order: CAS(newHead) first ->
insert revision -> durable result; loser rolls back before insert (no
revision_number uniqueness race).

## OPC2 — idempotency binds principal/context
fingerprint = sha256(planDigest | projectId | refId | expectedHead | principalRef).
Policy version intentionally excluded (completed command remains replayable as
its original historical result). Cross-principal / cross-target / cross-project
replay => IDEMPOTENCY_KEY_CONFLICT. PlanDigest unchanged (still excludes
principal/targetRef/authorization).

## OPC3 — final FCV candidate governance
IMPLEMENTATION_SHA for the milestone = FINAL_ORIGINAL_FCV_CANDIDATE (07ebd0ee),
NOT the initial candidate. All original SHAs preserved; old publication
untouched.

## RBC1-RBC3 — backend capability contract
RBC1: OperationPlan/application semantics depend on capabilities (durable
immutable revision persistence, exact revision read, authoritative head read,
expected-head conditional advancement, declared atomicity, durable ApplyCommandId
uniqueness, deterministic stale-head detection) — never on PostgreSQL identity.
RBC2: PostgreSQL adapter may keep jOOQ/migrations/constraints/conditional
UPDATE/READ COMMITTED/Testcontainers; PostgreSQL mechanics are infrastructure
-owned and translate to domain semantics (ADVANCED / STALE_TARGET_REF /
IDEMPOTENCY_CONFLICT / PERSISTENCE_FAILURE).
RBC3: each future backend must independently prove BC1-BC12 (exact revision
persistence/read, single-parent = plan base, one-winner concurrency, stale-head,
no force update, durable idempotency one-result, unknown-outcome retry no
duplicate, principal-context idempotency, no-op stale-head check, documented
failure atomicity, content-hash consistency, no merge-authority redefinition).
Current PostgreSQL conformance: all proven by OperationPlanConcurrencyIT
(real PostgreSQL 16): EXPECTED_HEAD_ONE_WINNER, STALE_HEAD, DURABLE_IDEMPOTENCY,
PRINCIPAL_CONTEXT_IDEMPOTENCY, NO_OP_STALE_HEAD, NORMAL_EDIT_PARENT,
TRANSACTION_ROLLBACK, CANONICAL_HASH_MATCH. ORPHAN_REVISION_POSSIBLE = NO is a
PostgreSQL conformance property, not universal law.

## Verification
Targeted: 11 unit + 9 IT (incl. NO_OP stale-head CASE A-D, cross-principal/
cross-ref idempotency, head-advance regression) all PASS on real PostgreSQL.
Drift 147/147 (+8 OPCG). Full suite GREEN (see FCV record). bootJar, pfirr1,
Modulith PASS. POSTGRESQL_DOMAIN_SEMANTIC_LEAK_COUNT = 0 (static scan).
JGIT_INTRODUCED = NO. ALTERNATIVE_BACKEND_IMPLEMENTED = NO.
AUTHORIZATION_EXPIRATION_HARDENING = DEFERRED (not required by bounded correction).
Blockers = 0. Escalation = NONE. NEXT_ACTION = REVISION_COMMAND_MODEL_V1_DECISION_RECOVERY.

## Correction FCV (recorded at publication)
POST_CLOSE_CORRECTION_SHA = 8587fb5206fb73ece76296c5b91b451fc548de4f
POST_CLOSE_CORRECTION_TREE = b03e91003d53028f828f114311d3e3345ff91ea4
FCV = PASS (OPC1/OPC2/OPC3, RBC1-3 frozen, PostgreSQL conformance, leakage 0,
full suite 7118/0/0, drift 147/147, Modulith, bootJar, pfirr1)
EVIDENCE_MANIFEST = e08153b790acc0917697e5cd24a68252d8a2c3b43122712649d74f7e2f8f0afc
