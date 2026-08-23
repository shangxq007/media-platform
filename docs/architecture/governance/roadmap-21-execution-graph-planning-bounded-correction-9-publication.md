# Roadmap #21 Execution Graph Planning — Bounded Correction 9 Publication

STATUS=PENDING_CHATGPT_FINAL_REVIEW

## 1. Baseline / Topology Evidence

| Field | Value |
|---|---|
| Repository | shangxq007/media-platform |
| CANONICAL_MAIN_SHA | cf8c3abcf9fb2d0ad064246735714a4ac032ca81 |
| CANONICAL_MAIN_UNCHANGED | YES |
| BRANCH | agent/roadmap21-execution-graph-decision-recovery |
| C8_IMPLEMENTATION_SHA | 0210bc2ac6cdbd8bab0d483898e7fbdb51ba0021 |
| C8_PUBLICATION_SHA | 39a79a7f6ac10f485ac09cc69c565d9927215330 |
| C9_START_FROM | 39a79a7f6ac10f485ac09cc69c565d9927215330 (exact) |
| C9_IMPLEMENTATION_SHA | 52b5b75a912ada20211870f17c6ec9946bb0a247 |
| C9_IMPLEMENTATION_TREE | 44e544f618fd364cc4d20563a7f7c537d7f7b118 |
| C9_IMPLEMENTATION_PARENT | 39a79a7f (exact C8 publication) |
| merge-base(branch, origin/main) | cf8c3abc (== canonical main) |
| HISTORY_REWRITE / AMEND / REBASE / SQUASH / FORCE_PUSH | NO |
| WORKTREE_CLEAN_AT_FREEZE | YES |

## 2. Correction 8 Final Review — Honest Governance Record

ROADMAP_21_CORRECTION_8_FINAL_REVIEW=FAIL
DIRECT_C7_BLOCKERS_RESOLVED=YES
NEW_ARCHITECTURE_BLOCKER=LOGICAL_EDGE_IDENTITY_INJECTIVITY (resolved by C9)

**Model routing compliance (truthful record):**
C8_MODEL_ROUTING_COMPLIANCE=FAIL
C8_ARTIFACT_REUSE_AFTER_INDEPENDENT_REVIEW=ALLOWED
C8 used CODEX_RUNTIME_MODEL=gpt-5.5, which did not comply with the frozen
Codex routing governance. C8 implementation was NOT rewritten; the truthful
non-compliance is recorded here, and C9 runs the frozen route.

C9_REQUESTED_MODEL=gpt-5.6-sol
C9_OBSERVED_MODEL=gpt-5.6-sol
C9_REQUESTED_REASONING=high
C9_OBSERVED_REASONING=high
C9_MODEL_ROUTING_COMPLIANCE=PASS

**Codex POC counters (integers, no masking):**
CODEX_FALSE_PASS_CLAIM_COUNT=0
CODEX_FIRST_PASS_PHASE_ACCEPTANCE_COUNT=5
(C8-A, C8-B, C8-C, C8-E, C8-TEST first-pass accepted; C8-D required 1
correction packet for stale guard text; C8-RED accepted 16/16 in one session;
C8-REVIEW advisory clean.)

## 3. C9-A — Edge Identity Injectivity (LOGICAL_EDGE_IDENTITY_V1)

### Defect fixed
`LogicalExecutionGraphBuilder` previously derived:
```
ExecutionEdgeId("le-" + producerRenderNodeId + "-" + consumerRenderNodeId)
```
which collides for two valid #20 RenderDependencyEdges sharing endpoints but
carrying different typed RenderDependency semantics (e.g. DecodedFrames vs
EffectInput on the same P=Decode → C=Effect pair). #20 RenderGraphValidator
permits this topology; the identity collision was forbidden.

### New derivation (LogicalEdgeIdentity)
```
LOGICAL_EDGE_IDENTITY_V1 frame:
  producerRenderNodeId
  consumerRenderNodeId
  dependency = Canonical.dependency(...)   (typed canonical semantics)
→ CanonicalWriter structurally-framed stream (UTF-8 byte-length-prefixed)
→ SHA-256 → ExecutionEdgeId("le-" + <64-hex-sha256>)
```
- Injective: complete typed edge semantics included; structural framing makes
  the key collision-free even for hostile delimiters in ids/payloads.
- Deterministic: same semantic edge → same id (proven C9-T01 across two plans).
- Edge-local: excludes traversal position, insertion order, ExecutionPlanId,
  createdAt, correlation, runtime availability, provider/worker/device.
- NOT equal to LogicalExecutionGraphDigest nor PhysicalExecutionPlanDigest
  (proven C9-T01).

## 4. Duplicate Edge-ID Fail-Closed

`LogicalPhysicalPlanner.validateRefsResolve` now collects every
`ExecutionEdgeId`; on duplicate it throws:
- ExecutionPlanningException
- reason=INVALID_LOGICAL_GRAPH
- typed context=DuplicateIdentityContext
- identityType=executionEdgeId

No silent acceptance. Defense-in-depth: correct construction already yields
distinct ids for distinct valid edge semantics.

## 5. Same-Endpoint Validated Graph Proofs (Roadmap21Correction9Test)

Real #20-valid graph: P=Decode, C=Effect; E1=P→C DecodedFrames; E2=P→C
EffectInput. Built with RenderGraphBuilder, validated with RenderGraphValidator,
VALIDATION_VALID=YES asserted BEFORE #21 planning.

C9-T01 (identity):
EDGE_COUNT=2, DEPENDENCY_VARIANT_COUNT=2, EXECUTION_EDGE_ID_COUNT=2,
DISTINCT_EXECUTION_EDGE_ID_COUNT=2; deterministic across plans;
ExecutionEdgeId != logical digest; ExecutionEdgeId != physical digest.

C9-T02 (permutation [E1,E2] vs [E2,E1]):
LOGICAL_MODEL_EQUAL=YES, LOGICAL_EDGE_IDS_EQUAL=YES,
LOGICAL_DIGEST_EQUAL=YES, PHYSICAL_MODEL_EQUAL=YES,
EXECUTION_INPUT_IDS_EQUAL=YES, PHYSICAL_DIGEST_EQUAL=YES;
multiplicity remains 2 (both models and physical inputs).

C9-T03 (fail-closed): duplicate ExecutionEdgeId fixture →
ExecutionPlanningException, INVALID_LOGICAL_GRAPH, typed
DuplicateIdentityContext("executionEdgeId", <id>).

## 6. Zero Guards (mechanically enforced, Roadmap21PlanningGuardTest)

- ENDPOINT_ONLY_EXECUTION_EDGE_ID_DERIVATION_COUNT=0
- DUPLICATE_EXECUTION_EDGE_ID_ACCEPTANCE_COUNT=0
- TRAVERSAL_POSITION_EDGE_IDENTITY_COUNT=0
- RAW_EDGE_IDENTITY_DELIMITER_GRAMMAR_COUNT=0
- All prior #21/C8 guards retained and passing.

## 7. RED Mutation Evidence (R-C9-01..04)

| Family | Mutation | Red test | Actual red |
|---|---|---|---|
| R-C9-01 | restore endpoint-only "le-"+p+"-"+c | C9-T01 | INVALID_LOGICAL_GRAPH duplicate execution edge identity |
| R-C9-02 | remove dependency field from identity | C9-T01 | duplicate execution edge identity (collision returns) |
| R-C9-03 | disable duplicate edge-id validation | C9-T03 | Expected ExecutionPlanningException, nothing thrown |
| R-C9-04 | traversal-position prefix in identity | C9-T02 | LOGICAL_MODEL_EQUAL failed (le-0/le-1 vs le-2/le-3) |

Each: EXPECTED_RED=ACTUAL_RED (real failure), RESTORED=YES (file SHA-256 back
to baseline), POST_RESTORE_GREEN=PASS. Hermes independently verified
restoration: all 5 changed/added files byte-identical to pre-RED baseline;
authoritative Gradle rerun GREEN (197 Roadmap21 tests).

## 8. Regression / C8 Lock

All Correction 8 accepted areas re-ran green (no regression):
injective physical input sort key; ExecutionRequirement normalization;
logical typed model normalization; physical typed model normalization;
single public execution planning entry; real validated P1/P2→C multi-edge
permutation; semantic mutation digest sensitivity; T2; pruning;
REUSE exact seven; #22 boundary.

## 9. Targeted Tests

PER_SUITE_COUNTS (all failures=0 errors=0):
Roadmap21ContractBehaviorTest 26, Correction4 14, Correction5 12,
Correction6 8, Correction7 11, Correction8 33, Correction9 3,
DigestMutation 11, GraphClosure 11, IoAndCanonical 17, PlanningGuard 41,
EntryResidualGuard 4, ExecutionEntryBoundary 5,
MaterializerCoverageIntegration 3, TimedTextOverflowFailClosedIntegration 2.
TOTAL_TARGETED_TESTS=197
TARGETED_FAILURES=0, TARGETED_ERRORS=0
COMPILE_JAVA=PASS (--rerun-tasks), COMPILE_TEST_JAVA=PASS (--rerun-tasks)

## 10. Full Suite / Gates

FULL_SUITE_TESTS=7783
FULL_SUITE_FAILURES=0
FULL_SUITE_ERRORS=0
FULL_SUITE_SKIPPED=43
MODULE_COUNT=177 actionable tasks (serial --max-workers=1, podman-hermetic)

DRIFT_GATE=PASS (git diff --check clean; worktree clean at freeze)
ARCHITECTURE_GATE=PASS (verifyGcr2ArtifactAuthority, verifyC1Cnm1RedGates,
verifyTimelineEffectTransitionCanonicalization, verifyC20RenderPlanBoundaryGuard)
PFIRR1_REMEDIATION_CHECK=PASS
BOOTJAR=PASS
CI_EQUIVALENT=PASS (jooqFoundationCheck family)
MODULITH_GATE=PASS (ModularityTest 1/1, ModulithDocumentationGenerationTest 1/1)

HERMES_REPORTED_FCV=GREEN
INDEPENDENT_GITHUB_CI_STATUS=NONE

## 11. Candidate Freeze

CORRECTION_9_IMPLEMENTATION_SHA=52b5b75a912ada20211870f17c6ec9946bb0a247
CORRECTION_9_IMPLEMENTATION_TREE=44e544f618fd364cc4d20563a7f7c537d7f7b118
FCV_BUILD_INPUT_SHA=52b5b75a912ada20211870f17c6ec9946bb0a247
FCV_BUILD_INPUT_TREE=44e544f618fd364cc4d20563a7f7c537d7f7b118
FCV_INPUT_EQUALS_FINAL_CANDIDATE=YES
POST_FCV_SOURCE_CHANGES=0
POST_FCV_TEST_CHANGES=0
POST_FCV_BUILD_CHANGES=0

## 12. Governance State

ROADMAP_21_CORRECTION_9_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED

NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_9_FINAL_REVIEW
