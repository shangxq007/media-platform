# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED IMPLEMENTATION PUBLICATION

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
BASE_SHA=99aa41624c01ae4965038cf5d450414d74955e68
BASE_TREE=24f9228ecd211c36d29675b3f1a8843ef72f61e7
CONTRACT=ROADMAP_21_EXECUTION_GRAPH_PLANNING_BOUNDED_ARCHITECTURE_CONTRACT_V1 (FROZEN)

## IMPLEMENTATION SUMMARY

PHASE 0 TYPE DISPOSITION (frozen 53-file ledger executed):
- DELETE_SHADOW 23: ExecutionCapabilityRequirement, MediaOperation hierarchy
  (16), GpuRequirement, TimelineToExecutionPlanCompiler, MediaBackendCompiler,
  ExecutionPlanErrorCode, ExecutionInputRole, ExecutionOutputRole
- DEFER_TO_22_PLUS 8: ExecutionResourceRequirement (de-GPU'd), CpuClass,
  MemoryClass, NetworkRequirement, TemporaryStorageClass,
  ExecutionStepFailurePolicy, ExecutionProvider (FROZEN); ExecutionCacheKey
  removed (depended on deleted model — #22 cache concept)
- MIGRATE_REDESIGN 13: superseded by new #21 model
- REUSE 7 + REUSE_MECHANICS 2: identity/schema/edge/input/output ids,
  ExecutionCreationContext (provenance-only); serializer/digest mechanics
  migrated into layer digest types

PHASE 1-5 NEW #21 CORE (com.example.platform.execution.planning):
- ExecutionRequirement: pure derived normalized projection of RenderPlan +
  RenderGraph declarations (CR-02); capability refs (no CapabilityId-only
  downgrade), execution-intent refs 1:1 from RenderExecutionRequirement;
  provenance-only identity excluded from semantic digest
- LogicalExecutionGraph: RENDER_NODE_TO_LOGICAL_NODE=1_TO_1; exact
  RenderDependencyEdge/RenderDependency variants preserved; no generic
  DATA/CONTROL/VALIDATION; no invented barrier
- LogicalExecutionGraphDigest / PhysicalExecutionPlanDigest (separate layers)
- PhysicalPlannerV1: ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT;
  FUSION/CHUNKING/N_TO_M/REWRITE/COST DEFERRED
- PhysicalExecutionPlan + PhysicalPlanUnit: provider-neutral; no
  provider/worker/device/queue/availability binding
- LogicalPhysicalPlanner: graph.planFingerprint == plan.fingerprint
  consistency gate (fail-closed); typed cycle detection
- ExecutionPlanningFailureReason: 9 active V1 classes (ILLEGAL_FUSION not
  active); ExecutionPlanningException typed carrier

PHASE 7 FAOF-1: deterministic DFS cycle detection + canonical digest
mechanics (pure); LEAN4/COQ runtime dependency NO; formal proof gate NO.

## EVIDENCE

TARGETED_TESTS: Roadmap21PlanningTest 7/7 + Roadmap21PlanningGuardTest 6/6
RED: RED-21A (shadow resurrection) FAIL-DETECTED 3; RED-21B (mutable runtime
read) FAIL-DETECTED 3; restored GREEN
FULL_SUITE: 7599 tests / 0 failures / 0 errors / 43 skipped / 40 modules
GATES: verifyGcr2ArtifactAuthority PASS, pfirr1RemediationCheck PASS,
verifyC1Cnm1RedGates PASS, jooqFoundationCheck PASS,
verifyTimelineEffectTransitionCanonicalization PASS,
verifyC20RenderPlanBoundaryGuard PASS, bootJar PASS
MODULITH_GATE=N/A (no ApplicationModules.verify; documentation generation only)
ZERO_COUNTS: SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0,
SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0,
DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0,
EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY_COUNT=0,
GENERIC_EXECUTION_DEPENDENCY_AUTHORITY_COUNT=0,
EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY_COUNT=0,
EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0,
EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0,
EXECUTION_INPUT_ROLE_SHADOW_AUTHORITY_COUNT=0,
EXECUTION_OUTPUT_ROLE_SHADOW_AUTHORITY_COUNT=0,
EXECUTION_IO_UPSTREAM_SEMANTIC_REDECLARATION_COUNT=0 (guard),
EXECUTION_INPUT_MUTABLE_AVAILABILITY_AUTHORITY_COUNT=0 (guard)
(DEFER_TO_22_PLUS resource types exist in domain package only; verified
excluded from planning package and semantic digests)

POST-IMPLEMENTATION GUARD FIX: Roadmap21EntryResidualGuardTest
worktree-context exclusion (rootIsWorktree-aware; same class as fda0ba2d).
Test-only; no production change.

## GOVERNANCE

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21_IMPLEMENTATION_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO (pending ChatGPT review)
NEXT_ACTION=CHATGPT_ROADMAP_21_IMPLEMENTATION_FINAL_REVIEW
