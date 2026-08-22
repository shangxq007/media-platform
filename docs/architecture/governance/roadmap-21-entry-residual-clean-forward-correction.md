# ROADMAP-21 ENTRY RESIDUAL CLEAN-FORWARD CORRECTION — PUBLICATION

## Trigger

Third-party source-level audit (P0=0, P1=1) against canonical main f4441430:
P1 = TASK_HANDLER_SINGLE_AUTHORITY_VIOLATION (MockAsrTaskHandler /
MockProbeTaskHandler unconditional @Component log-only handlers coexisting
with real handlers; TaskHandlerRegistry silent put-overwrite; dispatcher
default-active → mock could become semantic execution authority).

## Critical discovery during correction

Canonical main f4441430 **failed clean-tree compilation**
(`:timeline-module:compileJava --rerun-tasks` → 56 errors): the D1
dual-conflict-model debt (diff-engine TimelineConflict vs merge
TimelineConflict) was compile-level. All prior FCVs relied on stale Gradle
build caches (UP-TO-DATE on unchanged sources). This correction fixes the
compile defect as a mandatory prerequisite.

## Corrections

### C-01 Task handler single authority (P1, MANDATORY)
- DELETED production MockAsrTaskHandler / MockProbeTaskHandler (log-only
  fake success; no frozen runtime requirement retains them)
- TaskHandlerRegistry: `handlers.put(...)` → `putIfAbsent` + IllegalStateException
  on duplicate capability — ONE_TASK_CAPABILITY_ONE_HANDLER_AUTHORITY_V1;
  fail-closed, order-independent
- Tests: TaskHandlerRegistryTest 7/7 (single resolve, duplicate fail-closed
  both orders, missing capability null, size)
- Guard: Roadmap21EntryResidualGuardTest 4/4 (mock defs 0, exactly one
  ASR/PROBE handler, putIfAbsent required, no @Order authority)

### D1 Compile-level merge conflict model unification (BLOCKING prerequisite)
- TimelineMergeConflictDetector now produces merge semantic TimelineConflict
  (conflictId/entityRef/conflictType/resolutionRequired/message) with
  diff→merge conflict-type mapping; entityRef derived from canonical path
- TimelineMergeConflictAnalysis / TimelineMergePlanOperation /
  TimelineNonConflictingMergePlanner / detector tests unified on merge model
- Merge flow now has ONE conflict authority (merge semantic model)

### P2 residuals (independently re-verified)
- C-02 CompositeJwtDecoder: DELETE (0 refs, 0 bean)
- C-03 LegacyHmacJwtDecoder: DELETE (0 prod refs; test-only; EnabledAdminSecurityTest
  inlines jjwt decode; LegacyHmacJwtDecoderTest removed)
- C-04 RenderJobRepository.requeueExecutingJob/requeueFailedJob: DELETE (0 callers)
- C-05 EffectInstance 9-arg ctor: DELETE (0 prod callers, 2 test callers migrated
  to explicit-target form)
- C-06 AiGatewayService 2-arg ctor: DELETE (0 prod callers, 4 test callers migrated)
- C-07 RoleRepository.deleteUserRoleAssignment: RETAIN (TEST_ONLY_OR_TOOLING —
  sole production caller OidcDevBootstrapRunner is @Profile dev/local/test +
  oidc-dev-bootstrap.enabled default false; production unreachable)
- Quota-billing deprecated types: RETAIN_WITH_EVIDENCE (UCUO-ADR-009)

## Structural counts (final)

All retired surfaces = 0: production mock ASR/PROBE definitions, silent
duplicate overwrite, CompositeJwtDecoder, LegacyHmacJwtDecoder, legacy
requeue APIs, EffectInstance 9-arg ctor, AiGatewayService 2-arg ctor.
ASR_ACTIVE_HANDLER_COUNT=1, PROBE_ACTIVE_HANDLER_COUNT=1.
DUPLICATE_TASK_HANDLER_REGISTRATION=FAIL_CLOSED.
MISSING_HANDLER_BEHAVIOR=failTask (typed failure, never silent success).

## RED evidence

| ID | Mutation | Detector | FAILED | Restored |
|---|---|---|---|---|
| RED-R21E-01 | registry silent put-overwrite | TaskHandlerRegistryTest + Roadmap21EntryResidualGuardTest | 5 | YES |
| RED-R21E-02 | MockAsrTaskHandler resurrected | Roadmap21EntryResidualGuardTest + TaskHandlerRegistryTest | 2 | YES |

## Candidate / FCV

IMPLEMENTATION_SHA=d8f4d90b88a646a8927dc02aee936559b9c99e24
IMPLEMENTATION_TREE=422f65c3a7896cc4dde7eb1f345d80214981e3b6
FULL_SUITE=7749 tests / 0 failures / 0 errors / 43 skipped / 40 modules
GATES=PASS (verifyGcr2ArtifactAuthority, pfirr1RemediationCheck,
verifyC1Cnm1RedGates, jooqFoundationCheck,
verifyTimelineEffectTransitionCanonicalization,
verifyC20RenderPlanBoundaryGuard, PRE21 all guards, bootJar)
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
CANONICAL_MAIN_CHANGED=NO
ROADMAP_21_STARTED=NO
READY_FOR_INDEPENDENT_REVIEW=YES
