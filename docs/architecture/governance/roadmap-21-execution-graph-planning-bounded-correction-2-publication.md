# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED CORRECTION 2 PUBLICATION

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
PREVIOUS_CORRECTION_IMPLEMENTATION_SHA=6e493adacae37331d5f061be340b91eb798d03c7
PREVIOUS_CORRECTION_PUBLICATION_SHA=59120486430c268f70cbb56b81d57e42bfeeb507
PREVIOUS_CHATGPT_REVIEW=CORRECTION_REQUIRED
PREVIOUS_ARCHITECTURE_ESCALATION=C12_C13_TEMPORAL_COORDINATE_BOUNDARY

## PHASE A — C12/C13 ARCHITECTURE CORRECTION (FROZEN)

C12_C13_ARCH_CORRECTION_SHA=7fd27899ada16ecd61057de9352a0a8ff9744e2f
C12_C13_SELECTED_OPTION=A (typed #20 RenderExecutionCoverage, timeline domain)
RENDER_EXTENT_COORDINATE_DOMAIN=timeline / render request domain
RENDER_SAMPLE_WINDOW_COORDINATE_DOMAIN=source-media sampling domain
EXECUTION_COVERAGE_AUTHORITY=#20 RenderExecutionCoverage (renderplan domain;
set by DefaultRenderMaterializer from clip.timelineRange for DECODE/EFFECT)
PRUNING_AUTHORITY=#21 LogicalExecutionGraphBuilder (coverage vs extent, exact
rational; null coverage never pruned)
ALL_PRODUCERS_ELIMINATED_RULE=FORBIDDEN (removed)
TEMPORAL_MAPPING_AUTHORITY=UNCHANGED
ARCHITECTURE_ESCALATION_RESOLVED=YES (scoped to C12/C13)

## PHASE B — BLOCKERS B1-B4

B1 (temporal coordinate): pruning compares RenderExecutionCoverage vs
RenderExtent (same domain); RenderSampleWindow never compared —
DIRECT_RENDER_SAMPLE_WINDOW_VS_RENDER_EXTENT_COMPARISON_COUNT=0 (guard +
RED-01). 8 coordinate tests: nonZeroSourceOffsetDoesNotFalsePrune,
constantRateMappingUsesCorrectCoordinateDomain, reverseMappingDoesNotFalsePrune,
freezeMappingDoesNotFalsePrune, outOfCoverageNodePruned, overlappingCoveragePreserved,
extentBoundaryIsExact, invalidExtentFailsClosed. Extent validated fail-closed
(start<end, frameRate>0).

B2 (REUSE_AS_CANONICAL): 7 frozen types restored and USED — ExecutionPlanId
(explicit planner input, NEVER fingerprint-derived — guard + RED-04),
ExecutionPlanSchemaVersion (1.0), ExecutionEdgeId, ExecutionInputId (on
InputBinding), ExecutionOutputId (on OutputDeclaration), ExecutionStepId (on
PhysicalPlanUnit), ExecutionCreationContext (provenance-only). ExecutionProvider
restored to List<ExecutionOutputId>. REUSE_AS_CANONICAL_COUNT_ACTUAL=7.

B3 (typed IO direction): SourceArtifact ALWAYS projects to typed InputBinding
(content digest preserved — RED-05/RED-07), root inputs carry null dependency
variant; Intermediate/Final artifact expectations project to OutputDeclaration;
source artifact never an output (tested); ExecutionInputId/ExecutionOutputId typed.

B4 (canonical completeness): explicit Canonical encoder — sealed variant tags,
fixed field order, explicit scalars; dependency explicit encoding (FAIL_CLOSED
unknown — RED-10), artifacts explicit (artifactId + digest algorithm + value),
materialization non-empty payload fixtures (EffectMaterializationRequirement),
extent + sample-window frameRate included (RED-09), capability explicit with
sorted alternatives, digests include formatVersion + requestedExtent + coverage.
OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0.

## EVIDENCE (mechanical)

CONTRACT_BEHAVIOR_TESTS=26
DIGEST_TESTS=11
IO_CANONICAL_TESTS=11
GUARD_TESTS=16
TOTAL_TARGETED_TESTS=64
TARGETED_FAILURES=0

RED_MUTATIONS_TOTAL=12 (01 window-vs-extent, 03 planId String, 04 fingerprint
id, 05 source null, 07 artifact digest, 09 extent fps, 10 dep toString,
11 mat payload, 12 provider, 13 runtime read + hardened-guard re-runs)
RED_MUTATIONS_FAIL_DETECTED=12
RED_RESTORED_GREEN=YES

FULL_SUITE_TESTS=7650 FAILURES=0 ERRORS=0 SKIPPED=43 MODULES=40
GATES: verifyGcr2ArtifactAuthority PASS, pfirr1RemediationCheck PASS,
verifyC1Cnm1RedGates PASS, jooqFoundationCheck PASS,
verifyTimelineEffectTransitionCanonicalization PASS,
verifyC20RenderPlanBoundaryGuard PASS, bootJar PASS
MODULITH_GATE=N/A (no ApplicationModules.verify in repository)

CLEAN FORWARD: DELETE_SHADOW 23 / MIGRATE_REDESIGN 13 / DEFER_TO_22_PLUS 8
(7 retained unwired + ExecutionCacheKey removed) / REUSE_AS_CANONICAL 7
(restored) / REUSE_MECHANICS_ONLY 2 — ledger honored; no resurrection, no
wrappers. All zero-count guards PASS (shadow authorities, step-kind,
dependency, determinism, resource, failure-policy, dual authority, IO roles,
runtime reads, provider/worker/device binding, float time, free-text).

## SHAs

C12_C13_ARCH_CORRECTION_SHA=7fd27899ada16ecd61057de9352a0a8ff9744e2f
C12_C13_ARCH_CORRECTION_TREE=(see git)
NEW_CORRECTION_IMPLEMENTATION_SHA=49e402ad43eda9561ed63783610d47b419c1c128
FINAL_CANDIDATE_SHA=49e402ad43eda9561ed63783610d47b419c1c128
FINAL_CANDIDATE_TREE=7d720aad435d8e44890e9a37221ec7e88548270d
FCV_BUILD_INPUT_SHA=49e402ad43eda9561ed63783610d47b419c1c128
FCV_BUILD_INPUT_TREE=7d720aad435d8e44890e9a37221ec7e88548270d
PUBLICATION_SHA=(docs-only, appended after FCV)

## GOVERNANCE

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE (Phase A resolution complete)
ROADMAP_21_CORRECTION_2_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_2_FINAL_REVIEW
