# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED CORRECTION PUBLICATION

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
PREVIOUS_IMPLEMENTATION_SHA=6e6cf06cb432bab67d59617c2132d12d386f2fa8
PREVIOUS_GUARD_FIX_SHA=dc0612bb5e11f8b85d771f8e15fda04c0b5932b1
PREVIOUS_PUBLICATION_SHA=23cf9fe541a5121a362968b80ff3e23354b84195
PREVIOUS_CHATGPT_REVIEW=CORRECTION_REQUIRED

## BLOCKERS CLOSED (A-K)

A. LOGICAL_TYPED_RENDER_NODE_KIND=YES — typed RenderNodeKind + RenderComponentPath + operationKey + typed artifact/capability/execution/output/materialization requirement lists + exact RenderSampleWindow on LogicalExecutionNode; no String kind, no string-keyed requirement refs.
B. EXECUTION_IO_TYPED_PROJECTION=YES — ExecutionIoProjection.InputBinding/OutputDeclaration typed immutable refs over exact #20 declarations; no roles, no storage URI, no AVAILABLE-state, no expectedProperties/retentionClass shadow.
C. RENDER_DEPENDENCY_VARIANT_LOSS=0 — LogicalDependencyEdge + digests carry full variant payload via explicit Canonical serialization (record toString would omit CapabilityRequirement.alternatives — canonical field-by-field).
D. TEMPORAL_EXACTNESS=PASS — RenderSampleWindow end-to-end (logical node + physical unit); exact rational MediaTime; FLOAT_TIME_AUTHORITY_COUNT=0 (guarded).
E. EXTENT_BASED_ELIMINATION=PASS — deterministic pure pruning (DISJOINT_WINDOW exact-rational disjointness + ALL_PRODUCERS_ELIMINATED transitive) with PruningEvidence; overlapping windows never pruned; deterministic (3 tests).
F. PHYSICAL_TYPED_INPUTS/OUTPUTS/DEPENDENCIES/TEMPORAL_WINDOW/RENDER_EXTENT/REQUIREMENT_REFS=YES — PhysicalPlanUnit fully typed; deterministic cacheability metadata declarative only; ExecutionPlanId + schema version integrated.
G. PLAN_IDENTITY_SCHEMA_STATUS=YES — ExecutionPlanId = identity (deterministic, != digest, tested); schema version 1.0.
H. LOGICAL_DIGEST_COMPLETE_CONTENT_COVERAGE=YES / PHYSICAL_DIGEST_COMPLETE_CONTENT_COVERAGE=YES — layer-complete via Canonical; 11 mutation-sensitivity tests (kind/opKey/contract-range/alternatives/output/window/variant/dependency-structure/extent) + provenance exclusion test; dual-channel (dep|+in|) variant coverage proven by RED-C5-D.
I. MACHINE_READABLE_FAILURE_CONTEXT=YES — sealed PlanningFailureContext (Cycle/MissingReference/FingerprintMismatch/ExtentViolation/IllegalPartition/DuplicateIdentity/UnsupportedConstruct); message non-authoritative; FREE_TEXT_SEMANTIC_BRANCH_COUNT=0.
J. ALL_REFS_RESOLVE_VALIDATION=YES — duplicate source id, dangling producer/consumer (source-graph + post-pruning), fingerprint mismatch, cycle: typed fail-closed (4 tests).
K. FAOF1_STATUS=REQUIRED_HOOKS — law ids on failure reasons; builder proof obligations; pure DFS + canonical digest mechanics; LEAN4/COQ/SMT runtime NO.

## EVIDENCE

NEW_CORRECTION_IMPLEMENTATION_SHA=6e493adacae37331d5f061be340b91eb798d03c7
NEW_CORRECTION_IMPLEMENTATION_TREE=0765fcc6df77f206328560342f222fa7100883c2
NEW_FCV_BUILD_INPUT_SHA=6e493adacae37331d5f061be340b91eb798d03c7
NEW_FCV_BUILD_INPUT_TREE=0765fcc6df77f206328560342f222fa7100883c2
FCV_BUILD_INPUT=committed candidate (worktree clean; module tests --rerun-tasks clean compile)

TARGETED_TESTS=41/41 PASS (Roadmap21ContractBehaviorTest 19 + Roadmap21DigestMutationTest 11 + Roadmap21PlanningGuardTest 11)
RED_MUTATIONS_TOTAL=11 (C1-C10 + C5-D dual-channel)
RED_MUTATIONS_FAIL_DETECTED=11
RED_RESTORED_GREEN=YES
FULL_SUITE_TESTS=7627 FAILURES=0 ERRORS=0 SKIPPED=43 MODULES=40
GATES: verifyGcr2ArtifactAuthority PASS, pfirr1RemediationCheck PASS,
verifyC1Cnm1RedGates PASS, jooqFoundationCheck PASS,
verifyTimelineEffectTransitionCanonicalization PASS,
verifyC20RenderPlanBoundaryGuard PASS, bootJar PASS
MODULITH_GATE=N/A (no ApplicationModules.verify in repository)

ZERO COUNTS (all mechanically verified):
SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0
SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0
DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0
EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY_COUNT=0
GENERIC_EXECUTION_DEPENDENCY_AUTHORITY_COUNT=0
RENDER_DEPENDENCY_VARIANT_LOSS_COUNT=0
EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY_COUNT=0
ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0 (planning package)
ROADMAP21_RUNTIME_FAILURE_POLICY_COUNT=0 (planning package)
EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0
EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0
EXECUTION_INPUT_ROLE_SHADOW_AUTHORITY_COUNT=0
EXECUTION_OUTPUT_ROLE_SHADOW_AUTHORITY_COUNT=0
EXECUTION_IO_UPSTREAM_SEMANTIC_REDECLARATION_COUNT=0 (typed refs, no re-declaration)
EXECUTION_INPUT_MUTABLE_AVAILABILITY_AUTHORITY_COUNT=0
PHYSICAL_PLANNER_PROVIDER_BINDING_COUNT=0
PHYSICAL_PLAN_WORKER_BINDING_COUNT=0
PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0
ROADMAP21_MUTABLE_RUNTIME_READ_COUNT=0
PLANNER_INVENTED_CAPABILITY_REQUIREMENT_COUNT=0
PLANNER_INVENTED_BARRIER_COUNT=0
FLOAT_TIME_AUTHORITY_COUNT=0
FREE_TEXT_EXTENT_AUTHORITY_COUNT=0
FREE_TEXT_SEMANTIC_BRANCH_COUNT=0

CLEAN FORWARD:
DELETE_SHADOW 23 executed; MIGRATE_REDESIGN 13 executed (typed IO projection,
typed node/unit, layer digests); DEFER_TO_22_PLUS 8 isolated (7 retained
unwired, ExecutionCacheKey removed — depended on deleted model); REUSE 7
executed (identity types migrated into plan records); REUSE_MECHANICS 2
executed (digest mechanics in layer digest types). No resurrection, no
wrappers, no aliases.

## GOVERNANCE

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
OPEN_BLOCKERS=0
ROADMAP_21_CORRECTION_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_FINAL_REVIEW
