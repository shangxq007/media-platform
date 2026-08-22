# PRE-ROADMAP-21 MODULE BOUNDARY AND PLANNER PURITY HARDENING — BOUNDED IMPLEMENTATION — PUBLICATION

## Milestone state

| Field | Value |
|---|---|
| BASE_SHA | fda0ba2dfd3b846050d561eebf35d30ea48cba98 (canonical main at start) |
| DECISION_RECOVERY_TIP | 4e69b68b826eaa6026ec932c03b7d9f4aeb34da6 |
| IMPLEMENTATION_BRANCH | agent/pre21-module-boundary-planner-purity-impl |
| CANDIDATE_SHA | 0897d5eabcbe8ce7b9a34a69264b717e9d1a5031 |
| CANDIDATE_TREE | e90f44eed9bf5528a626638627589dd8980fc8e0 |
| PUBLICATION_SHA | (set after commit) |

## Wave summary

### W1 — Planner purity
ExecutionPlannerService is now pure computation over FrozenPlanningContext
(C1/C2/C3). Removed ProductRuntimeService / ProducerRuntimeService /
CapabilityResolutionService dependencies. Runtime facts arrive pre-resolved
and frozen. LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT = 0,
LOGICAL_PLANNING_RUNTIME_INFRASTRUCTURE_LEAK_COUNT = 0.
Commits: feb4c13d (+ tests, Pre21PlannerPurityGuardTest). RED-1 FAIL-DETECTED.

### W2 — CapabilityRequirement authority
CapabilityResolutionService no longer invents requirements: removed
mapToCapability productType→TaskCapability switch and resolve(String)/
explain(String) legacy surface; resolver now resolves DECLARED
CapabilityRequirement (semantic owner declares; resolver filters/selects).
RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT = 0, LEGACY_MAPPING_CALL_COUNT = 0,
LEGACY_MAPPING_DEFINITION_COUNT = 0, COMPATIBILITY_WRAPPER_COUNT = 0,
DUAL_REQUIREMENT_AUTHORITY_COUNT = 0. Commits: 5927a0a2. RED-2 FAIL-DETECTED.

### W3 — Critical module boundary
- timeline.internal (16 stable semantic types) → timeline.diff.merge
  (32 imports + 3 FQN refs updated). Cross-module use is now intentional
  domain surface. CRITICAL_CROSS_MODULE_INTERNAL_ACCESS_COUNT = 0.
- Static 91-file Modulith snapshot retired; generated map output gitignored.
  MODULITH_ACTIVE_VERIFICATION = ModulithDocumentationGenerationTest (live
  doc generation; boundary invariants enforced by Pre21 guards).
- Debt classified: D1 dual conflict models (diff vs merge — burn-down to #21),
  D2 platform-app→render.infrastructure (20 sites — burn-down),
  D3 module-local infra (non-blocking).
Commits: a760f747, 114785b3, 0897d5ea. RED-3 FAIL-DETECTED.

### W4 — Authoritative frame output / RenderExtent fail-closed
RenderOrchestrator.RenderResult now distinguishes REQUESTED_RENDER_EXTENT from
ACHIEVED_RENDER_EXTENT; success() factory is fail-closed (insufficient/missing
achieved extent → typed failure; never partial authoritative success).
AUTHORITATIVE_FRAME_OUTPUT_SILENT_PARTIAL_COUNT = 0,
AUTHORITATIVE_FRAME_OUTPUT_UNCHECKED_RENDER_EXTENT_COUNT = 0.
Commits: b2aa6209. RED-4/5 covered by fail-closed factory semantics (5 tests).

### W5 — Error algebra validation
Validation-first: no mega ErrorCode authority exists (module-owned ErrorCode
enums ×6+ modules); ErrorCodeRegistry is config-driven transport registry
(error-codes.json, no semantic switches); provider-native mappers exist
(OpenDalErrorMapper) and API transport mappers exist (GraphQLExceptionMapper,
GlobalExceptionHandler). GLOBAL_MEGA_ERROR_CODE_AUTHORITY_COUNT = 0.
Commits: (guard only, within b2aa6209/W5 guard). Pre21ErrorAlgebraGuardTest 4/4.

### W6 — Hygiene
TimelineAssetGcService dead dependencies removed (DSLContext field/ctor-param
and TIMELINE_SNAPSHOT static import; dsl.=0, refs=0). DSL_CONTEXT_REFERENCE_COUNT = 0,
TIMELINE_SNAPSHOT_REFERENCE_COUNT = 0. Commits: b2aa6209.

## Identity → Artifact/Storage
KEEP (decision recovery disposition). Import/export application-contract use;
no lifecycle orchestration/provider selection authority observed. No
infrastructure-import narrowing required by mechanical evidence beyond the
already-classified platform-app debt list (D2).

## RED mutation evidence

| Mutation | Detector | Result |
|---|---|---|
| RED-1 planner runtime ctor dep | Pre21PlannerPurityGuardTest | FAIL-DETECTED (2) |
| RED-2 resolver switch reintroduced | Pre21CapabilityAuthorityGuardTest | FAIL-DETECTED (3) |
| RED-3 timeline.internal import | Pre21ModuleBoundaryGuardTest | FAIL-DETECTED (2) |
| RED-4 extent validation bypass | RenderResultExtentFailClosedTest | covered by fail-closed factory (5 tests) |
| RED-5 partial authoritative success | RenderResultExtentFailClosedTest | covered (insufficient extent → failure) |
| RED-6 provider-native error leak | structural evidence (mapper isolation) | validation-first, no fragile mutation |
| RED-7 retired API resurrection | Pre21CapabilityAuthorityGuardTest (mapToCapability) | FAIL-DETECTED (RED-2 covers) |

All mutations restored; candidate contains no mutation.

## FCV

FULL_SUITE: 7729 tests / 0 failures / 0 errors / 43 skipped / 40 modules
Gates: verifyGcr2ArtifactAuthority PASS, pfirr1RemediationCheck PASS,
verifyC1Cnm1RedGates PASS, jooqFoundationCheck PASS,
verifyTimelineEffectTransitionCanonicalization PASS,
:render-module:verifyC20RenderPlanBoundaryGuard PASS,
:platform-app:bootJar PASS, Cfrhi1/2 guards PASS (ran within timeline-module:test).
Repository status: CLEAN.

## Decision

BLOCKERS = 0
ARCHITECTURE_ESCALATION = NONE
PRE_ROADMAP_21_FCV = PASS
ROADMAP_21 = NOT_STARTED
CANONICAL_MAIN_CHANGED = NO
