# ROADMAP #21 — BOUNDED CORRECTION 6 PUBLICATION (FINAL CLOSURE CORRECTION)

STATUS=PENDING_CHATGPT_FINAL_REVIEW (publication cannot self-declare closure)

## BASELINE

CANONICAL_MAIN_SHA=cf8c3abcf9fb2d0ad064246735714a4ac032ca81
CANONICAL_MAIN_TREE=1f706a336f01615d2c9e6e81a1cc05edd8e2ff42
CORRECTION_5_IMPLEMENTATION_SHA=69e2aba2fca08f1d1bd006c1cd5d3141128519d0
CORRECTION_5_PUBLICATION_SHA=f1f50f17fe2579effd0e383be7c5893c35105e8b
CORRECTION_6_BASE_SHA=f1f50f17fe2579effd0e383be7c5893c35105e8b
BRANCH=agent/roadmap21-execution-graph-decision-recovery
WORKTREE=CLEAN

## BLOCKERS CLOSED

BLOCKER_1 CANONICAL_DETERMINISM_AND_INJECTIVITY_CLOSURE — CLOSED:
- Sub-finding A (edge insertion order): logical edge collection now
  NON_SEMANTIC — complete framed canonical per edge → sort → framed list,
  citing upstream #20 RenderPlanCanonicalCodec.graphFingerprintCanonical
  (Collections.sort(edgeEncodings)). Physical typedInputs/typedOutputs/
  typedDependencies canonical-sorted with upstream citations (sortedEncodings
  on artifacts/capabilities/outputs). C6-T01/02/03: edge-permutation
  invariance of #20 fingerprint, logical digest, physical digest. C6-T04/05:
  dependency payload mutations still change both digests.
- Sub-finding B (pruning-evidence framing): eliminated-node set encoded
  STRUCTURALLY (tag + explicit list count + individually framed elements).
  String.join removed. C6-T06: ["a","b"] != ["a\nb"]; C6-T07 membership
  sensitivity; C6-T08 insertion-order determinism.

BLOCKER_2 TIMED_TEXT_FAIL_CLOSED_END_TO_END_BOUNDARY_PROOF — CLOSED:
- ExecutionPlanningEntry: single guarded production boundary — only
  PLANNABLE enters #21. DefaultRenderPlanner → UNRENDERABLE (start + end
  overflow) → entry rejects with typed
  ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE +
  sealed RenderStatusRejectedContext → NO LogicalExecutionGraph /
  NO PhysicalExecutionPlan. PREPARATION_REQUIRED also rejected. Valid
  TIMED_TEXT → PLANNABLE → logical + physical produced (positive control).
  PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0
  (guard walks media-execution-plan-module/src/main).

Non-blocking cleanup: PruningEvidence javadoc corrected — canonical wording
(RenderExecutionCoverage vs RenderExtent only; RenderSampleWindow excluded;
ALL_PRODUCERS_ELIMINATED forbidden). This publication supersedes the stale
implementation comment (prior publications untouched).

## CANONICAL ORDERING EVIDENCE

UPSTREAM_RENDERGRAPH_EDGE_ORDER_SEMANTICS=NON_SEMANTIC (#20 codec sorts)
LOGICAL_EDGE_ORDER_CLASSIFICATION=NON_SEMANTIC_CANONICAL_SORT
EDGE_PERMUTATION_LOGICAL_DIGEST_EQUAL=PASS (C6-T02)
EDGE_PERMUTATION_PHYSICAL_DIGEST_EQUAL=PASS (C6-T03)
PHYSICAL_INPUT_ORDER_CLASSIFICATION=NON_SEMANTIC_CANONICAL_SORT
PHYSICAL_OUTPUT_ORDER_CLASSIFICATION=NON_SEMANTIC_CANONICAL_SORT
PHYSICAL_DEPENDENCY_ORDER_CLASSIFICATION=NON_SEMANTIC_CANONICAL_SORT
(all with upstream source citations in code comments)

## PRUNING FRAMING EVIDENCE

ELIMINATED_NODE_ENCODING=STRUCTURAL (tag + count + framed elements)
STRING_JOIN_SEMANTIC_COLLECTION_COUNT=0
ELIMINATED_LIST_COUNT_FRAMING=YES
NEWLINE_COLLISION_TEST=PASS (C6-T06)
INSERTION_ORDER_DETERMINISM_TEST=PASS (C6-T08)

## END-TO-END FAIL-CLOSED EVIDENCE

EXECUTION_PLANNING_GUARDED_ENTRY=ExecutionPlanningEntry (only PLANNABLE)
DIRECT_PRODUCTION_BYPASS_CALLERS=0
START_OVERFLOW_PLANNING_STATUS=UNRENDERABLE (PLANNING_UNSUPPORTED diag)
START_OVERFLOW_ENTRY_REJECTED=YES
END_OVERFLOW_PLANNING_STATUS=UNRENDERABLE
END_OVERFLOW_ENTRY_REJECTED=YES
VALID_TIMED_TEXT_PLANNABLE=YES
VALID_TIMED_TEXT_REACHES_21=YES (logical + physical produced)
LOGICAL_RESULT_AFTER_OVERFLOW=NONE
PHYSICAL_RESULT_AFTER_OVERFLOW=NONE
PREPARATION_REQUIRED_REJECTED=YES

## DOCUMENTATION / GUARDS

STALE_SAMPLE_WINDOW_PRUNING_DOC_REMOVED=YES
ALL_PRODUCERS_ELIMINATED_DOC_REMOVED=YES
COVERAGE_VS_EXTENT_DOC_CANONICAL=YES

## REUSE REGRESSION

REUSE_AS_CANONICAL_EXPECTED=7 ACTUAL=7 EXACT=7 DRIFT=0 (frozen 99aa4162
definitions untouched — regression locked by contract-lock tests + guards)

## MODULE BOUNDARY

ROADMAP_21_PRODUCTION_FONT_TEXT_DEPENDENCY_COUNT=0
ROADMAP_21_PRODUCTION_FONTRATIONAL_REFERENCE_COUNT=0
TEST_ONLY_FONT_TEXT_DEPENDENCY=1 (T2 bridge unit fixtures)
(+ render-module testImplementation(media-execution-plan-module) test-only —
C6-C guarded-entry boundary tests; no production cycle)

## CLEAN FORWARD / ZERO GUARDS

All prior zero guards green (shadow authorities, provider/worker/device
bindings, sample-window-vs-extent, ALL_PRODUCERS_ELIMINATED, compat surfaces,
schema/creation-context frozen invariants, object-toString semantics,
unit-extent omission). New C6 guards: logicalEdgeOrderNonSemantic,
physicalDigestCollectionsCanonicalSorted, guardedEntryIsSoleProductionPath,
pruningEvidenceDocCanonical.

## RED EVIDENCE (mutate → fail → restore → green)

RED_MUTATION_FAMILIES=8 (+2 compile-enforced boundary)
R-C6-01 edge sort removed → C6-T02 FAILED=3 → restored
R-C6-03 eliminated join → C6-T06 FAILED=3 → restored
R-C6-04 eliminated omitted → C6-T07 FAILED=3 → restored
R-C6-05 gate removed → boundary test FAILED=2 → restored
R-C6-06 bypass caller → caller-count guard FAILED=3 → removed
R-C6-07 clamp → overflow integration FAILED=2 → restored
R-C6-09 font-text prod dep → boundary guard FAILED=2 → restored
R-C6-10 FontRational ref → mep production compile FAILED (dependency boundary
enforced at compile time; textual guard equivalent proven in R-C5-16)
R-C6-02: NOT APPLICABLE — no physical order leakage exists (C6-T03 proves
permutation invariance; no leak to remove)
R-C6-08: covered by positive-control test (C6-T13 valid path asserted)
POST-RESTORE GREEN=YES

## TESTS

REUSE_CONTRACT_TESTS=4, CANONICAL_TESTS=8, COLLISION_TESTS=7,
DIGEST_MUTATION_TESTS=11, IO_CANONICAL_TESTS=17, GRAPH_CLOSURE_TESTS=11,
CORRECTION_4_TESTS=14, CORRECTION_5_TESTS=12, CORRECTION_6_TESTS=8,
GUARD_TESTS=33
TOTAL_TARGETED_TESTS=132 TARGETED_FAILURES=0
RENDER INTEGRATION: ExecutionEntryBoundary=5, TimedTextOverflow=2,
MaterializerCoverage=3 — all 0 failures

## FULL SUITE / GATES

FULL_SUITE_TESTS=7728 FAILURES=0 ERRORS=0 SKIPPED=43 MODULE_COUNT=40
DRIFT_GATE=PASS ARCHITECTURE_GATE=PASS (verifyGcr2ArtifactAuthority,
verifyC1Cnm1RedGates, jooqFoundationCheck,
verifyTimelineEffectTransitionCanonicalization, verifyC20RenderPlanBoundaryGuard)
PFIRR1_REMEDIATION_CHECK=PASS BOOTJAR=PASS CI_EQUIVALENT=PASS (full serial,
DOCKER_HOST podman, --max-workers=1)
MODULITH_GATE=N/A (no ApplicationModules.verify exists in repository —
verified by inspection; not fabricated)

## SHAS

CORRECTION_6_IMPLEMENTATION_SHA=4048667eed1ab610201264821883c8831b6e7079
CORRECTION_6_IMPLEMENTATION_TREE=770be4c601905e5045e33ab0b73e47f84c6c5074
FCV_BUILD_INPUT_SHA=4048667eed1ab610201264821883c8831b6e7079
FCV_BUILD_INPUT_TREE=770be4c601905e5045e33ab0b73e47f84c6c5074
FCV_INPUT_EQUALS_FINAL_CANDIDATE=YES
PUBLICATION_PARENT_SHA=4048667eed1ab610201264821883c8831b6e7079
PUBLICATION_SHA=(docs-only, appended after FCV)

## GOVERNANCE

BLOCKERS=0 (Hermes assessment)
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21_CORRECTION_6_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_6_FINAL_REVIEW
