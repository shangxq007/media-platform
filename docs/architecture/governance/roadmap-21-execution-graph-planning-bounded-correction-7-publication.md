# ROADMAP #21 — BOUNDED CORRECTION 7 PUBLICATION (FINAL DETERMINISM CLOSURE)

STATUS=PENDING_CHATGPT_FINAL_REVIEW (publication cannot self-declare closure)

## BASELINE

CANONICAL_MAIN_SHA=cf8c3abcf9fb2d0ad064246735714a4ac032ca81
CANONICAL_MAIN_TREE=1f706a336f01615d2c9e6e81a1cc05edd8e2ff42
CORRECTION_6_IMPLEMENTATION_SHA=4048667eed1ab610201264821883c8831b6e7079
CORRECTION_6_PUBLICATION_SHA=4ba590224d2c60ee01c7f558d5b1d6e281c8f4c4
CORRECTION_7_BASE_SHA=4ba590224d2c60ee01c7f558d5b1d6e281c8f4c4
CORRECTION_7_BASE_TREE=0b4224b707eaad47b1d731a07fa8c68a01b53d94
BRANCH=agent/roadmap21-execution-graph-decision-recovery
WORKTREE=CLEAN

## BLOCKERS CLOSED

BLOCKER_1 PHYSICAL_PLANNING_NON_SEMANTIC_EDGE_ORDER_LEAKS_INTO_INPUT_IDENTITY — CLOSED:
- Positional SourceArtifact ↔ edge zip REMOVED (PhysicalPlannerV1). Each
  SourceArtifact → independent InputBinding (root/pinned, producer null); each
  incoming edge → independent InputBinding (sourceArtifact null). No invented
  cross-association (§6.2/6.3 authority rule honored).
- ExecutionInputId ordinals assigned AFTER canonical sort of each independent
  record class (source artifacts by Canonical.sourceArtifact; edges by
  producer + dependency canonical key) — never pre-normalization traversal
  position. Multiplicity preserved (canonical sort + deterministic ordinal).
- Real multi-edge test (C7-T01..T04/T17): P1→C + P2→C, [E1,E2] vs [E2,E1]
  genuinely reversed (assertNotEquals on edge[0]); #20 canonical encoding equal;
  logical digest equal; normalized physical input semantic content equal
  (model-level, not only digest); physical digest equal; input-id multiset
  identical. C6-T03's single-edge false positive replaced.

BLOCKER_2 LOGICAL_AND_PHYSICAL_NESTED_NON_SEMANTIC_COLLECTION_ORDER_NOT_FULLY_CANONICALIZED — CLOSED:
- Logical: artifactReferences / capabilityRequirements / outputRequirements /
  materializationRequirements canonical-sorted (matching #20 codec
  sortedEncodings); executionRequirements classified NON_SEMANTIC (#20 List
  carries no positional authority) and sorted.
- Physical: OutputDeclaration nested collections (outputRequirements /
  materializationRequirements / intermediateArtifactExpectations /
  finalArtifactExpectations) + unit capabilityRequirementRefs /
  executionIntentRefs canonical-sorted; outer input/output/dependency sorting
  retained.

GUARDED ENTRY STRUCTURAL ENFORCEMENT (required hardening, not a blocker):
- LogicalPhysicalPlanner reduced to package-private (class + plan method);
  ExecutionPlanningEntry remains the single public production entry.
- PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0
  (repository-wide src/main scan).
- PUBLIC_LOGICAL_PHYSICAL_LOW_LEVEL_ENTRY_COUNT=0 (visibility guard).

## CANONICALIZATION AUDIT TABLE

COLLECTION | UPSTREAM_AUTHORITY | ORDER_SEMANTICS | NORMALIZATION | TEST_EVIDENCE
logical.nodes | #21 structural partition | NON_SEMANTIC | CANONICAL_SORT (by logicalNodeId) | C7-T02 (edge perm), C6-T08
logical.edges | #20 codec sorts edge encodings | NON_SEMANTIC | CANONICAL_SORT (full framed canonical) | C7-T01/T02
logical.pruning.eliminatedNodes | set membership | NON_SEMANTIC | CANONICAL_SORT (structural list) | C6-T06/T08
logical.node.artifactReferences | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T05/T13
logical.node.capabilityRequirements | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T06/T14
logical.node.executionRequirements | #20 List, no positional authority | NON_SEMANTIC | CANONICAL_SORT | C7-T11
logical.node.outputRequirements | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T07/T15
logical.node.materializationRequirements | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T08/T16
physical.units | #21 structural partition | NON_SEMANTIC | CANONICAL_SORT (by stepId) | C7-T04
physical.unit.typedInputs | independent input records (C7-A) | NON_SEMANTIC | CANONICAL_SORT | C7-T03/T17/T18
physical.unit.typedOutputs | derived from #20 sorted collections | NON_SEMANTIC | CANONICAL_SORT | C7-T09/T10
physical.unit.typedDependencies | logical edge set (non-semantic) | NON_SEMANTIC | CANONICAL_SORT | C7-T03
physical.output.outputRequirements | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T07 (physical)
physical.output.materializationRequirements | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T08 (physical)
physical.output.intermediateArtifactExpectations | derived from #20 sorted artifacts | NON_SEMANTIC | CANONICAL_SORT | C7-T09
physical.output.finalArtifactExpectations | derived from #20 sorted artifacts | NON_SEMANTIC | CANONICAL_SORT | C7-T10
physical.unit.capabilityRequirementRefs | #20 codec sortedEncodings | NON_SEMANTIC | CANONICAL_SORT | C7-T06 (physical)
physical.unit.executionIntentRefs | follows executionRequirements (C7-B) | NON_SEMANTIC | CANONICAL_SORT | C7-T11 (physical via same rule)
No row UNKNOWN. Every classification cites upstream authority in source comments (ORDER_SEMANTICS=...).

## PHYSICAL INPUT NORMALIZATION DESIGN

OLD_POSITIONAL_SOURCE_EDGE_ZIP_REMOVED=YES
INPUT_BINDING_MODEL=independent records (artifact-bound input XOR edge-bound input)
INPUT_ID_DERIVATION_RULE=stable node prefix + canonical-sorted ordinal per record class
INPUT_ID_USES_PRENORMALIZATION_INDEX=NO
SOURCE_ARTIFACT_EDGE_ASSOCIATION_INVENTED=NO
DUPLICATE_MULTIPLICITY_PRESERVED=YES (canonical sort + ordinal keeps duplicates distinct)
MULTI_EDGE_TEST_ACTUALLY_REVERSES_ORDER=YES (2 producers, 1 consumer, assertNotEquals edge[0])
MULTI_EDGE_PHYSICAL_CONTENT_EQUAL=YES (C7-T03 model-level)
MULTI_EDGE_PHYSICAL_DIGEST_EQUAL=YES (C7-T04)
MULTI_EDGE_INPUT_IDS_EQUAL=YES (C7-T17)

## GUARDED ENTRY

PUBLIC_SUPPORTED_ENTRY=ExecutionPlanningEntry (only PLANNABLE)
LOW_LEVEL_PLANNER_VISIBILITY=package-private (class + plan method)
REPOSITORY_WIDE_DIRECT_CALLERS=0 (scan of media-execution-plan-module/src/main)
PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0
PUBLIC_LOGICAL_PHYSICAL_LOW_LEVEL_ENTRY_COUNT=0

## REUSE REGRESSION

REUSE_AS_CANONICAL_EXPECTED=7 ACTUAL=7 EXACT=7 DRIFT=0 (frozen 99aa4162 definitions untouched; ExecutionInputId type unchanged — only value assignment normalized)

## MODULE BOUNDARY

ROADMAP_21_PRODUCTION_FONT_TEXT_DEPENDENCY_COUNT=0
ROADMAP_21_PRODUCTION_FONTRATIONAL_REFERENCE_COUNT=0
TEST_ONLY_FONT_TEXT_DEPENDENCY=1 (T2 bridge fixtures)
TEST_ONLY_TIMELINE_DEPENDENCY=1 (EMR fixture types; #21 production never depends on timeline)

## CLEAN FORWARD / ZERO GUARDS

All prior zero guards green + new C7 guards:
POSITIONAL_SOURCE_ARTIFACT_EDGE_ZIP_COUNT=0 (bidirectional regex on planner source)
RAW_INCOMING_EDGE_ORDER_TO_INPUT_ID_COUNT=0
PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0
PUBLIC_LOGICAL_PHYSICAL_LOW_LEVEL_ENTRY_COUNT=0
noPositionalArtifactEdgeZipInPlanner / logicalNestedCollectionsCanonicalSorted /
physicalNestedCollectionsCanonicalSorted / plannerVisibilityPackagePrivate

## RED EVIDENCE (mutate → fail → restore → green)

RED_MUTATION_FAMILIES=13, FAIL_DETECTED=13, POST_RESTORE_GREEN=YES:
R-C7-01 edge-order inputId → C7-T17/T03 FAILED=3
R-C7-02 positional zip → guard FAILED=2 (guard regex hardened bidirectional + widened)
R-C7-03 logical artifact sort → C7-T05 FAILED=3
R-C7-04 logical capability sort → C7-T06 FAILED=3
R-C7-05 logical output sort → C7-T07 FAILED=3
R-C7-06 logical materialization sort → C7-T08 FAILED=3
R-C7-07 physical output sort → C7-T07(physical) FAILED=3
R-C7-08 physical materialization sort → C7-T08(physical) FAILED=3
R-C7-09 intermediate/final sort → C7-T09/T10 FAILED=3
R-C7-11 planner public → visibility guard FAILED=3
R-C7-12 gate removed → boundary regression FAILED=2
R-C7-13 dependency payload omitted → C7-T12 FAILED=3
R-C7-14 planId in digest → C7-T19 FAILED=3
(R-C7-10 capabilityRefs sort removal: covered by C7-T06 physical assertion +
R-C7-04/07/08/09 same-family nested-sort removals)

## TESTS

CONTRACT_BEHAVIOR=26 GRAPH_CLOSURE=11 DIGEST=11 IO_CANONICAL=17 CORRECTION4=14
CORRECTION5=12 CORRECTION6=8 CORRECTION7=11 GUARD=37
TOTAL_TARGETED_TESTS=147 TARGETED_FAILURES=0
RENDER INTEGRATION: ExecutionEntryBoundary=5 TimedTextOverflow=2
MaterializerCoverage=3 EntryResidualGuard=4 — all 0 failures

## FULL SUITE / GATES

FULL_SUITE_TESTS=7743 FAILURES=0 ERRORS=0 SKIPPED=43 MODULE_COUNT=40
DRIFT_GATE=PASS ARCHITECTURE_GATE=PASS (verifyGcr2ArtifactAuthority,
verifyC1Cnm1RedGates, jooqFoundationCheck,
verifyTimelineEffectTransitionCanonicalization, verifyC20RenderPlanBoundaryGuard)
PFIRR1_REMEDIATION_CHECK=PASS BOOTJAR=PASS CI_EQUIVALENT=PASS (full serial,
DOCKER_HOST podman, --max-workers=1)
MODULITH_GATE=N/A (no ApplicationModules.verify exists in repository)
HERMES_REPORTED_FCV=GREEN
INDEPENDENT_GITHUB_CI_STATUS=NONE

## SHAS

CORRECTION_7_IMPLEMENTATION_SHA=c56635950d101da2c3259aaf9ec6c93b45a7528c
CORRECTION_7_IMPLEMENTATION_TREE=bbc8ff81de04bf65b30baf4ac435d5ad57be283a
FINAL_CANDIDATE_SHA=c56635950d101da2c3259aaf9ec6c93b45a7528c
FINAL_CANDIDATE_TREE=bbc8ff81de04bf65b30baf4ac435d5ad57be283a
FCV_BUILD_INPUT_SHA=c56635950d101da2c3259aaf9ec6c93b45a7528c
FCV_BUILD_INPUT_TREE=bbc8ff81de04bf65b30baf4ac435d5ad57be283a
FCV_INPUT_EQUALS_FINAL_CANDIDATE=YES
PUBLICATION_PARENT_SHA=c56635950d101da2c3259aaf9ec6c93b45a7528c
PUBLICATION_SHA=(docs-only, appended after FCV)

## GOVERNANCE

BLOCKERS=0 (Hermes assessment)
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21_CORRECTION_7_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_7_FINAL_REVIEW
