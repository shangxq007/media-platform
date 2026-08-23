# Roadmap #21 Execution Graph Planning — Bounded Correction 8 Publication

STATUS=PENDING_CHATGPT_FINAL_REVIEW

## 1. Baseline / Topology Evidence

| Field | Value |
|---|---|
| Repository | shangxq007/media-platform |
| CANONICAL_MAIN_SHA | cf8c3abcf9fb2d0ad064246735714a4ac032ca81 |
| CANONICAL_MAIN_TREE | 1f706a336f01615d2c9e6e81a1cc05edd8e2ff42 |
| CANONICAL_MAIN_UNCHANGED | YES |
| BRANCH | agent/roadmap21-execution-graph-decision-recovery |
| CORRECTION_8_BASE_SHA | 9394fcc3bf59f5707ac1c63c83b73ab8d90f25c8 |
| CORRECTION_8_BASE_TREE | acbd21fe516f29f0bf8445c0f23277e769d46317 |
| CORRECTION_7_IMPLEMENTATION_SHA | c56635950d101da2c3259aaf9ec6c93b45a7528c |
| IMPLEMENTATION_SHA | 0210bc2ac6cdbd8bab0d483898e7fbdb51ba0021 |
| IMPLEMENTATION_TREE | 1cac4ad08809a2e166e3e55ded7bee1e3792e71f |
| IMPLEMENTATION_PARENT | 9394fcc3bf59f5707ac1c63c83b73ab8d90f25c8 (exact C7 publication) |
| merge-base(branch, origin/main) | cf8c3abc (== canonical main) |
| ahead from main | 24 |
| behind main | 0 |
| HISTORY_REWRITE / AMEND / REBASE / SQUASH / FORCE_PUSH | NO |
| WORKTREE_CLEAN_AT_FREEZE | YES |

## 2. Execution Governance Provenance

PRIMARY_CODING_EXECUTOR=CODEX
CODEX_EXECUTION_MODE=LOCAL_CLI
CODEX_RUNTIME_MODEL=gpt-5.5
CODEX_CLIENT_VERSION=codex-cli 0.148.0

CODEX_IMPLEMENTATION_PHASES=C8-A, C8-B, C8-C, C8-D, C8-E, C8-TEST, C8-RED
CODEX_SESSION_COUNT=8 (implementation) + 1 (independent review)
CODEX_CORRECTION_TASK_COUNT=1 (C8-D-CORRECTION-1: stale guard text updated after unified PendingInput refactor)
CODEX_ARCHITECTURE_ESCALATION_COUNT=0

HERMES_PRODUCTION_CODE_AUTHORING_COUNT=0
HERMES_TEST_CODE_AUTHORING_COUNT=0
HERMES_BUILD_CODE_AUTHORING_COUNT=0
DEEPSEEK_TASK_COUNT=0

## 3. C8-A — Injective Physical Input Sort Key

### Design
`PhysicalPlannerV1.edgeCanonical` replaced the raw delimiter tuple
`producerLogicalNodeId + "\u0001" + Canonical.dependency(...)` with a
`CanonicalWriter` full-stream structurally-framed record:

```
EDGE_INPUT
  producerLogicalNodeId
  producerRenderNodeId
  consumerLogicalNodeId
  consumerRenderNodeId
  dependency (typed canonical payload)
```

`CanonicalWriter` frames every variable-length scalar as UTF-8
`<byteLength>:<bytes>` — semantic content cannot affect structure, so the key
is injective even when node ids or dependency payloads contain `\u0001`,
`|`, `\n`, or arbitrary Unicode.

### Adversarial Collision Proof (C8-T01/T02)
Constructed two semantically distinct edge records that collide under the OLD
grammar:

- Edge A: producer `"P"`, dependency `AudioInput(track="t", clip="x\u0001DECODED_FRAMES")`
- Edge B: producer `"P\u0001AUDIO_INPUT|1:t|16:x"`, dependency `DecodedFrames`

Verified: OLD_RAW_KEY(A) == OLD_RAW_KEY(B) (collision reproduced), while
NEW_FRAMED_KEY(A) != NEW_FRAMED_KEY(B) (injective).

Permutations [A,B] vs [B,A] produce identical normalized input model,
identical ExecutionInputIds, and identical physical digest (C8-T03/T04/T05).

## 4. C8-B — ExecutionRequirement Normalization

`ExecutionRequirement` compact constructor now normalizes both reference lists
by complete canonical semantics (not raw insertion order):

- capabilityRequirementRefs → sorted by `Canonical.capability(ref.declaration())`
- executionIntentRefs → sorted by `Canonical.executionIntent(ref.declaration())`
- multiplicity preserved via `List.copyOf` (no dedup)

Proven:
- plan node order permutation → equal (C8-T06)
- capability insertion order permutation → equal (C8-T07)
- execution-intent insertion order permutation → equal (C8-T08)

## 5. C8-C — Logical Model Normalization

Introduced `PlanningCanonicalOrder` (package-private, pure, no state, reuses
existing canonical encoders — no second serializer authority). Applied in the
`LogicalExecutionGraph` / `LogicalExecutionNode` / `PruningEvidence`
constructors:

| Model field | Sort key |
|---|---|
| LogicalExecutionNode.artifactReferences | Canonical.artifact |
| LogicalExecutionNode.capabilityRequirements | Canonical.capability |
| LogicalExecutionNode.executionRequirements | Canonical.executionIntent |
| LogicalExecutionNode.outputRequirements | Canonical.outputRequirement |
| LogicalExecutionNode.materializationRequirements | Canonical.materialization |
| LogicalExecutionGraph.nodes | logicalNodeId (unique per V1) |
| LogicalExecutionGraph.edges | CanonicalWriter LOGICAL_EDGE (edgeId + producer/consumer ids + RenderNodeIds + dependency canonical) |
| PruningEvidence.eliminatedNodes | CanonicalWriter ELIMINATED_NODE (logical/source id + windows + reason) |

Typed values in, typed values out — no serialization round-trip, no string
substitution. Multiplicity preserved.

Proven: C8-T09..T15 (typed list permutations + node order + validated edge
permutation).

## 6. C8-D — Physical Model Normalization

Applied through the same `PlanningCanonicalOrder` in `PhysicalExecutionPlan`,
`PhysicalPlanUnit`, `OutputDeclaration` constructors and in
`PhysicalPlannerV1.plan`:

- Inputs: unified `PendingInput` model — source artifacts and incoming edges
  are collected, sorted ONCE by `inputBindingSemanticKey` (CanonicalWriter
  frame: consumer/producer ids, step ids, RenderNodeIds, dependency canonical,
  optional source-artifact canonical, optional sample window), THEN
  `ExecutionInputId` ordinals are assigned from that normalized order.
- OutputDeclaration: outputRequirements / materializationRequirements /
  intermediateArtifactExpectations / finalArtifactExpectations all
  canonical-sorted (same policy as ExecutionRequirement — one normalization
  authority, not two).
- PhysicalPlanUnit: typedInputs / typedOutputs / typedDependencies /
  capabilityRequirementRefs / executionIntentRefs canonical-sorted.
- PhysicalExecutionPlan.units: canonical-sorted by stepId/logicalNodeId;
  ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT preserved (no N:M).

Proven: C8-T16..T22. `physicalSemanticProjection` helper (formatVersion,
schemaVersion, planFingerprint, units, propagatedExtent — excluding planId and
digest) used for semantic equality.

## 7. MODEL NORMALIZATION AUDIT TABLE

| MODEL_FIELD | UPSTREAM_ORDER_AUTHORITY | ORDER_SEMANTICS | MODEL_NORMALIZATION | DIGEST_NORMALIZATION | TEST_EVIDENCE |
|---|---|---|---|---|---|
| ExecutionRequirement.capabilityRequirementRefs | #21 derived projection | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T07 |
| ExecutionRequirement.executionIntentRefs | #21 derived projection | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T08 |
| LogicalExecutionGraph.nodes | #21 builder | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T14 |
| LogicalExecutionGraph.edges | #21 builder | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T15 |
| PruningEvidence.eliminatedNodes | #21 pruning | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T15 suite |
| LogicalExecutionNode.artifactReferences | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T09 |
| LogicalExecutionNode.capabilityRequirements | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T10 |
| LogicalExecutionNode.executionRequirements | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T11 |
| LogicalExecutionNode.outputRequirements | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T12 |
| LogicalExecutionNode.materializationRequirements | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T13 |
| PhysicalExecutionPlan.units | #21 planner | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T22 |
| PhysicalPlanUnit.typedInputs | #21 planner | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T03/T04 |
| PhysicalPlanUnit.typedOutputs | #21 planner | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T16 (fixture) |
| PhysicalPlanUnit.typedDependencies | #21 planner | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T21 |
| PhysicalPlanUnit.capabilityRequirementRefs | #21 planner | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T19 |
| PhysicalPlanUnit.executionIntentRefs | #21 planner | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T20 |
| OutputDeclaration.outputRequirements | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T16 |
| OutputDeclaration.materializationRequirements | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T17 |
| OutputDeclaration.intermediateArtifactExpectations | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T18 |
| OutputDeclaration.finalArtifactExpectations | #20 RenderNode | NON_SEMANTIC | CANONICAL_TYPED_SORT | CANONICAL_SORT | C8-T18 |

UNKNOWN_ROWS=0

## 8. C8-E — True Single Public Execution-Planning Entry

- PUBLIC_SUPPORTED_ENTRY=ExecutionPlanningEntry
- PUBLIC_RESULT_TYPE=ExecutionPlanningEntry.PlanningResult (record: executionRequirement, logicalExecutionGraph, physicalExecutionPlan)
- LOGICAL_GRAPH_BUILDER_VISIBILITY=package-private
- PHYSICAL_PLANNER_VISIBILITY=package-private
- LOGICAL_PHYSICAL_PLANNER_VISIBILITY=package-private (unchanged)

Counts (C8-T29, source-level + compile ripple proof):
- PUBLIC_LOGICAL_GRAPH_BUILDER_ENTRY_COUNT=0
- PUBLIC_PHYSICAL_PLANNER_ENTRY_COUNT=0
- PUBLIC_LOGICAL_PHYSICAL_PLANNER_ENTRY_COUNT=0
- PUBLIC_EXECUTION_PLANNING_ENTRY_COUNT=1

Repository-wide caller scan (C8-T30): all `**/src/main/java/**/*.java`,
excluding `/.git/`, `/.worktrees/`, `/build/`, generated sources.
- PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0
- PRODUCTION_DIRECT_LOGICAL_GRAPH_BUILDER_CALLERS_OUTSIDE_INTERNAL_CHAIN=0
- PRODUCTION_DIRECT_PHYSICAL_PLANNER_CALLERS_OUTSIDE_INTERNAL_CHAIN=0

The `LogicalPhysicalPlanner.PlanningResult` internal type leak was eliminated;
the public entry now owns its result carrier.

Fail-closed (C8-T31): DefaultRenderPlanner → UNRENDERABLE → entry rejects →
no logical/physical result. Positive regression (C8-T32): valid TIMED_TEXT
reaches #21 through the public entry.

## 9. Validated Multi-Edge Proof (C8-T15 / C8-T33)

Topology P1=Decode, P2=Decode, C=Effect with E1=P1→C and E2=P2→C
(DecodedFrames) is built with the real #20 `RenderGraphBuilder` and validated
by the real #20 `RenderGraphValidator` — `validation.valid()` asserted BEFORE
any permutation proof. Edge permutation genuinely differs in source order;
normalized logical edge model, physical model, input ids, logical digest and
physical digest are all identical.

VALIDATED_MULTI_EDGE_GRAPH=YES

## 10. Semantic Mutation Evidence (C8-T24..T27)

Real semantic mutations (NOT permutations):

| Family | Mutation | Logical digest | Physical digest |
|---|---|---|---|
| C8-T24 capability | id / contract range / alternatives | changes | changes |
| C8-T25 output | role / color (BT709→BT2020) / raster (8→10bit) | changes | changes |
| C8-T26 materialization | payload amount 0.50→0.75 | changes | changes |
| C8-T27 dependency | payload (left→right) + variant (AudioInput→EffectInput) | changes | changes |

C8-T28: ExecutionPlanId excluded — different plan ids, same physical semantic
model, same physical digest.

## 11. Evidence Honesty Correction

Correction 7 publication used invented C7-Txx labels for some assertions.
Correction 8 publication cites actual test method names:
`oldEdgeSortKeyCollisionReproduced`, `newEdgeSortKeyInjectiveForAdversarialRecords`,
`adversarialEdgePermutation*`, `executionRequirement*OrderNormalized`,
`logical*ModelNormalized`, `physical*ModelNormalized`,
`fullModelAndDigestDeterminismAcrossNonSemanticPermutations`,
`capabilitySemanticMutationsChangeLogicalAndPhysicalDigests`,
`outputSemanticMutationsChangeLogicalAndPhysicalDigests`,
`materializationSemanticMutationChangesLogicalAndPhysicalDigests`,
`dependencySemanticMutationsChangeLogicalAndPhysicalDigests`,
`executionPlanIdExcludedFromPhysicalSemanticDigest`,
`validatedMultiEdgeGraphPassesValidationBeforePermutationProof`,
`onlyExecutionPlanningEntryIsPublicPlanningEntry`,
`repositoryWideProductionBypassCallersZero`,
`defaultRenderPlannerUnrenderableRejectedFailClosed`.

ORDER INVARIANCE TEST and SEMANTIC MUTATION TEST are explicitly distinguished.

## 12. REUSE Regression

REUSE_AS_CANONICAL_EXPECTED=7
REUSE_AS_CANONICAL_ACTUAL=7
REUSE_AS_CANONICAL_EXACT=7
REUSE_AS_CANONICAL_DRIFT=0
(ExecutionPlanId, ExecutionPlanSchemaVersion, ExecutionEdgeId,
ExecutionInputId, ExecutionOutputId, ExecutionStepId,
ExecutionCreationContext — all unchanged; only ExecutionInputId ASSIGNMENT
mechanics changed per frozen contract)

## 13. Module Boundary

ROADMAP_21_PRODUCTION_FONT_TEXT_DEPENDENCY_COUNT=0
ROADMAP_21_PRODUCTION_FONTRATIONAL_REFERENCE_COUNT=0
TEST_ONLY_FONT_TEXT_DEPENDENCY=allowed (unchanged)
PROVIDER_BINDING_COUNT=0
WORKER_BINDING_COUNT=0
DEVICE_BINDING_COUNT=0
MUTABLE_RUNTIME_READ_COUNT=0

## 14. CLEAN FORWARD / Zero Guards

New C8 guards (all enforced by Roadmap21PlanningGuardTest /
Roadmap21Correction8Test):
- RAW_SEMANTIC_TUPLE_SORT_KEY_DELIMITER_COUNT=0
- NON_NORMALIZED_EXECUTION_REQUIREMENT_REF_ORDER_COUNT=0
- NON_NORMALIZED_LOGICAL_NON_SEMANTIC_LIST_COUNT=0
- NON_NORMALIZED_PHYSICAL_NON_SEMANTIC_LIST_COUNT=0
- PUBLIC_LOGICAL_GRAPH_BUILDER_ENTRY_COUNT=0
- PUBLIC_PHYSICAL_PLANNER_ENTRY_COUNT=0
- PUBLIC_LOGICAL_PHYSICAL_PLANNER_ENTRY_COUNT=0
- PRODUCTION_DIRECT_LOGICAL_GRAPH_BUILDER_CALLERS_OUTSIDE_INTERNAL_CHAIN=0
- PRODUCTION_DIRECT_PHYSICAL_PLANNER_CALLERS_OUTSIDE_INTERNAL_CHAIN=0
- PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0
- PUBLIC_EXECUTION_PLANNING_ENTRY_COUNT=1

All prior Roadmap #21 zero guards retained and passing (shadow execution
capability/operation authorities, timeline→execution compiler, execution step
kind independence, generic dependency authority, render dependency variant
loss, execution determinism independence, invented resource requirements,
runtime failure policy, execution plan dual authority, compatibility wrapper,
I/O shadow authorities, mutable availability, provider/worker/device binding,
mutable runtime reads, sample-window-vs-extent comparisons,
all-producers-eliminated, font-text production deps, positional
source-artifact edge zip, logical digest raw string joins).

## 15. RED Mutation Evidence (R-C8-01..16)

16/16 families, each: MUTATION → EXPECTED_RED → ACTUAL_RED (real failure) →
RESTORED → POST_RESTORE_GREEN (rerun PASS). Full per-family table in
codex-log evidence; representative:

| Family | Mutation | Red test | Actual red |
|---|---|---|---|
| R-C8-01 | restore raw \u0001 edge key | C8-T02 | collision expected-not-equal |
| R-C8-02 | remove source-artifact model sort | C8-T09 | reversed artifacts |
| R-C8-03 | remove ER capability sort | C8-T07 | reversed capabilities |
| R-C8-04 | remove ER intent sort | C8-T08 | reversed intents |
| R-C8-05 | ER node traversal order | C8-T06 | node-order leak |
| R-C8-06 | remove logical capability sort | C8-T10 | reversed |
| R-C8-07 | remove logical output sort | C8-T12 | reversed |
| R-C8-08 | remove logical materialization sort | C8-T13 | reversed |
| R-C8-09 | remove logical edge model sort | C8-T15 | reversed edges |
| R-C8-10 | remove OutputDeclaration sorts | C8-T16 | reversed |
| R-C8-11 | remove PhysicalPlanUnit ref sorts | C8-T19 | reversed refs |
| R-C8-12 | builder public again | C8-T29 | guard failed |
| R-C8-13 | planner public again | C8-T29 | guard failed |
| R-C8-14 | bypass caller in another module | C8-T30 | caller=1 |
| R-C8-15 | remove PLANNABLE gate | C8-T31 | no exception |
| R-C8-16 | planId in digest | C8-T28 | digest differed |

Post-RED restoration verified by Hermes: SHA-256 of all 12 changed/added files
identical to pre-RED baseline; authoritative Gradle rerun GREEN.

## 16. Targeted Tests

PER_SUITE_COUNTS (all failures=0 errors=0):
- Roadmap21ContractBehaviorTest: 26
- Roadmap21Correction4Test: 14
- Roadmap21Correction5Test: 12
- Roadmap21Correction6Test: 8
- Roadmap21Correction7Test: 11
- Roadmap21Correction8Test: 33
- Roadmap21DigestMutationTest: 11
- Roadmap21GraphClosureTest: 11
- Roadmap21IoAndCanonicalTest: 17
- Roadmap21PlanningGuardTest: 37
- Roadmap21EntryResidualGuardTest: 4
- Roadmap21ExecutionEntryBoundaryTest: 5
- Roadmap21MaterializerCoverageIntegrationTest: 3
- Roadmap21TimedTextOverflowFailClosedIntegrationTest: 2

TOTAL_TARGETED_TESTS=194
TARGETED_FAILURES=0
TARGETED_ERRORS=0
COMPILE_JAVA=PASS (--rerun-tasks)
COMPILE_TEST_JAVA=PASS (--rerun-tasks)

## 17. Full Suite / Gates

FULL_SUITE_TESTS=7776
FULL_SUITE_FAILURES=0
FULL_SUITE_ERRORS=0
FULL_SUITE_SKIPPED=43
MODULE_COUNT=177 actionable tasks (serial, --max-workers=1,
DOCKER_HOST=podman-hermetic)

DRIFT_GATE=PASS (git diff --check clean; worktree clean at freeze)
ARCHITECTURE_GATE=PASS (verifyGcr2ArtifactAuthority, verifyC1Cnm1RedGates,
verifyTimelineEffectTransitionCanonicalization, verifyC20RenderPlanBoundaryGuard)
PFIRR1_REMEDIATION_CHECK=PASS (verifyPfirr1AuthenticationAuthority)
BOOTJAR=PASS (platform-app, remote-render-worker, sandbox-worker)
CI_EQUIVALENT=PASS (jooqFoundationCheck family: verifyJooq* all PASS)
MODULITH_GATE=PASS (ModularityTest 1/1, ModulithDocumentationGenerationTest 1/1)

HERMES_REPORTED_FCV=GREEN
INDEPENDENT_GITHUB_CI_STATUS=NONE

## 18. Candidate Freeze

CORRECTION_8_IMPLEMENTATION_SHA=0210bc2ac6cdbd8bab0d483898e7fbdb51ba0021
CORRECTION_8_IMPLEMENTATION_TREE=1cac4ad08809a2e166e3e55ded7bee1e3792e71f
FCV_BUILD_INPUT_SHA=0210bc2ac6cdbd8bab0d483898e7fbdb51ba0021
FCV_BUILD_INPUT_TREE=1cac4ad08809a2e166e3e55ded7bee1e3792e71f
FCV_INPUT_EQUALS_FINAL_CANDIDATE=YES
POST_FCV_SOURCE_CHANGES=0
POST_FCV_TEST_CHANGES=0
POST_FCV_BUILD_CHANGES=0

## 19. Governance State

ROADMAP_21_CORRECTION_8_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED

NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_8_FINAL_REVIEW
