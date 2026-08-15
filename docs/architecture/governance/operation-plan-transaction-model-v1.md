---
type: architecture-governance-record
milestone: OPTM
name: OPERATION_PLAN_TRANSACTION_MODEL_V1
status: CLOSED
date: 2026-08-15
authority: OPERATION_PLAN_TRANSACTION_BOUNDED_ARCHITECTURE_CONTRACT_V1 (DR PASS) + OPI1-OPI5
---

# OPERATION_PLAN_TRANSACTION_MODEL_V1

## Base / chain
- BASE = 0c0eda94 (Operation Model publication)
- DR = PASS (PT1-35/PP1-15/PR1-10 frozen)
- IMPLEMENTATION chain (linear, no amend):
  ce88fd0d (planner + DB CAS + idempotency + NO_OP + JSON patch retirement)
  -> 73781aa6 (schema-governance tests aligned to V2 migration)
  -> 07ebd0ee (TemporalMapping polymorphic canonical JSON)
- PUBLICATION = (see git log; parent 0c0eda94)

## OPI1-OPI5
OPI1 database-enforced CAS: conditional UPDATE timeline_revision_ref WHERE
expected head; affected rows==1; proven on real PostgreSQL (1 success / 1 stale
/ 1 child). OPI2 authorization binds plan digest + project/ref/principal.
OPI3 durable ApplyCommandId (apply_command table, unique PK, fingerprint,
replay same result). OPI4 semantic NO_OP (candidate hash == base => no
revision, no head change). OPI5 legacy JSON patch: zero public generic-patch
write endpoints; AI parser path non-production (no web endpoint); typed
TimelinePatchEngine retained mechanical.

## Model
OperationPlanner (15 frozen ops incl. delete sync/group consequences, trim
sync-anchor reject, set-rate exact duration + freeze-target reject), immutable
OperationPlan, OperationPlanDigest (SHA-256 domain-separated; excludes targetRef/
principal/auth), PlannedChange (sealed primary/secondary), OperationPlanPreview
(binds digest), AuthorizationDecision (binds plan + apply context), ApplyContext,
TargetRevisionRef, ApplyResult, typed PlanErrorCode. OperationPlanApplyService:
explicit jOOQ transaction (CAS + revision insert + durable result).

## Canonical completion
TimelineClip += temporalMapping (TM canonical projection, identity default,
polymorphic JSON); V2 migration (timeline_revision_ref + apply_command, head
initialized deterministically).

## Verification
OperationPlanTransactionTest 11 PASS; OperationPlanConcurrencyIT 4 PASS (real
PostgreSQL); drift 139/139 (21 OPTG); full suite 7113 GREEN (0/0); bootJar;
pfirr1 (clone); Modulith. Blockers = 0. Escalation = NONE.
CANONICAL_WRITE_BYPASS_COUNT = 0. NEXT_ACTION = REVISION_COMMAND_MODEL_V1_DR
(candidate; see final report).
