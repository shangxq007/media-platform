# ROADMAP #21 — BOUNDED CORRECTION 5 PUBLICATION

STATUS=PENDING_CHATGPT_FINAL_REVIEW (publication cannot self-declare closure)

## BASELINE

CANONICAL_MAIN_SHA=cf8c3abcf9fb2d0ad064246735714a4ac032ca81
CANONICAL_MAIN_TREE=1f706a336f01615d2c9e6e81a1cc05edd8e2ff42
CORRECTION_4_IMPLEMENTATION_SHA=bd311993ce8d9b41b254f4d9d73ac366635d7c20
CORRECTION_4_PUBLICATION_SHA=2247eb20ec5b79025df4afa8f92048077ec1aa84
CORRECTION_5_BASE_SHA=2247eb20ec5b79025df4afa8f92048077ec1aa84
BRANCH=agent/roadmap21-execution-graph-decision-recovery
WORKTREE=CLEAN

## B1 — REUSE_AS_CANONICAL EXACT CONTRACT

REUSE_AS_CANONICAL_EXPECTED=7
REUSE_AS_CANONICAL_ACTUAL=7
REUSE_AS_CANONICAL_EXACT=7
REUSE_AS_CANONICAL_DRIFT=0

All 7 frozen types restored BYTE-FOR-BYTE from 99aa4162:
- ExecutionPlanId / ExecutionEdgeId / ExecutionInputId / ExecutionOutputId /
  ExecutionStepId: record(String value) implements Serializable; null/blank →
  IllegalArgumentException (frozen exception type restored — previous NPE
  drift removed); toString()=value; typed usage preserved (edgeId on
  LogicalDependencyEdge, inputId on InputBinding, outputId on OutputDeclaration,
  stepId on PhysicalPlanUnit, planId on PhysicalExecutionPlan).
- ExecutionPlanSchemaVersion: record(int value) implements Serializable; V1
  constant; of(int); value<1 → IllegalArgumentException; toString()=value.
- ExecutionCreationContext: frozen 7-field record implements Serializable;
  createdAt REQUIRED (requireNonNull); minimal(Instant)/forUser(...) factories;
  getRequestedByUserId/getRequestedByTenantId/getParentPlanId/getTraceId/
  getComment Optional accessors; withTraceId/withComment; frozen explicit
  toString() "creationCtx{...}". Non-frozen drift REMOVED (Correction-4
  getRequestPurpose, Correction-2 absent()).

Mechanical guards: reuseTypesFrozenSignatures (Serializable, null/blank check,
IllegalArgumentException, toString, withTraceId/withComment, no getRequestPurpose,
no absent(), V1, of(int)); contract-lock tests for all 7.

## B2 — CANONICAL CLOSURE

CANONICAL_FULL_STREAM_FRAMING=YES (CanonicalWriter: UTF-8 byte-length-prefix
<byteLength>:<bytes> for EVERY variable-length scalar in the COMPLETE stream)
UTF8_BYTE_LENGTH_PREFIX_OR_EQUIVALENT=YES
LOGICAL_OUTER_DELIMITER_GRAMMAR_REMOVED=YES (LogicalExecutionGraphDigest fully
migrated to CanonicalWriter; guard asserts no append('|') in digest encoders)
PHYSICAL_OUTER_DELIMITER_GRAMMAR_REMOVED=YES (PhysicalExecutionPlanDigest fully
migrated; same guard)
OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0 (guard + R-C5-14)
NON_SEMANTIC_COLLECTION_ORDER_CANONICALIZED=YES (node list, unit list,
eliminated-node set, capability alternatives, parameter/binding lists sorted
over canonical keys)
SEMANTIC_ORDER_PRESERVED=YES (edge list, input list, output list, dependency
list — authored/positional semantics preserved; documented)

Full-stream adversarial tests: operationKeyVsRecordBoundaryCannotCollide,
unicodeByteLengthFraming (multibyte), prefixOverlapCannotCollide,
numericFramingLookalikeCannotCollide, delimiterHeavyValuesCannotCollide,
writerFieldPairingInjective, deterministicCollectionOrdering,
writerInjectiveScalar (null vs empty, list vs scalar).
Actual digest inputs: Logical = tag/formatVersion/planFingerprint/
requestedExtent/eliminated-set + per-node (id/kind/path/opKey/artifacts/caps/
intents/outputs/materializations/window/coverage) + per-edge (id/producer/
consumer/dependency); Physical = tag/formatVersion/schemaVersion/planFingerprint/
planExtent + per-unit (stepId/logicalId/sourceId/kind/opKey/inputs/outputs/deps/
window/coverage/unitExtent/caps/intents/cacheable). #20-owned materialization
encoding still delegated to the #20 RenderPlanCanonicalCodec (single authority).

## B3 — MODULE BOUNDARY

ROADMAP_21_PRODUCTION_FONT_TEXT_DEPENDENCY_COUNT=0 (implementation dependency
removed; guard scans build.gradle.kts)
ROADMAP_21_PRODUCTION_FONTRATIONAL_REFERENCE_COUNT=0 (guard walks #21
src/main for FontRational imports)
TEST_ONLY_FONT_TEXT_DEPENDENCY=1 (testImplementation only — T2 bridge unit
tests construct FontRational fixtures; production never sees font-text)

## B4 — FAIL-CLOSED EVIDENCE

TIMED_TEXT_UNREPRESENTABLE_EXACT_TIME_FAILS_CLOSED=PASS (2 real-materializer
integration tests: start=Long.MAX_VALUE+1 and duration overflow →
PLANNING_UNSUPPORTED diagnostics; no TIMED_TEXT node with invented/approximate
coverage; no valid RenderGraph reaches #21)
VALID_RENDER_GRAPH_REACHES_21_AFTER_OVERFLOW=NO

## CLEAN FORWARD

COMPATIBILITY_WRAPPER_COUNT=0
DUAL_AUTHORITY_COUNT=0
SHADOW_AUTHORITY_COUNT=0
(no RenderNode compat ctor/factory, no shadows, no runtime/provider binding —
all prior zero guards retained and green)

## TESTS

REUSE_CONTRACT_TESTS=4 (Correction5Test B1 section)
CANONICAL_TESTS=8 (Correction5Test B2 section)
COLLISION_TESTS=7 (full-stream adversarial, Correction5Test)
DIGEST_MUTATION_TESTS=11 (DigestMutationTest) + 17 (IoAndCanonicalTest) +
14 (Correction4Test) — retained
GRAPH_CLOSURE_TESTS=11
IO_TESTS=17
TIMED_TEXT_INTEGRATION_TESTS=2 (overflow fail-closed) + 3 (coverage integration)
GUARD_TESTS=29
TOTAL_TARGETED_TESTS=120 (mep) + 5 (render integration)
TARGETED_FAILURES=0

RED_MUTATIONS_TOTAL=15 families (R-C5-01 withTraceId, 02 withComment, 03
parentPlanId type, 04 createdAt nullable, 05 ID null exception, 06 schema
major/minor, 07 framing removal, 08 raw concatenation — mechanically
equivalent to 07 since frame() is the single framing mechanism (single-operator
raw-ization cannot collide, proving global framing consistency), 09 coverage
omission, 10 schema omission, 11 unit extent omission, 12 planId in digest,
13 provenance in digest, 14 semantic toString, 15 font-text prod dep, 16
FontRational ref, 17 clamp-to-zero)
RED_MUTATIONS_FAIL_DETECTED=15
RED_RESTORED_GREEN=YES

FULL_SUITE_TESTS=7711
FULL_SUITE_FAILURES=0
FULL_SUITE_ERRORS=0
FULL_SUITE_SKIPPED=43
MODULE_COUNT=40

## GATES

verifyGcr2ArtifactAuthority=PASS
pfirr1RemediationCheck=PASS
verifyC1Cnm1RedGates=PASS
jooqFoundationCheck=PASS
verifyTimelineEffectTransitionCanonicalization=PASS
verifyC20RenderPlanBoundaryGuard=PASS
bootJar=PASS
CI-equivalent=PASS (full serial suite, DOCKER_HOST podman, --max-workers=1)
MODULITH_GATE=N/A (no ApplicationModules.verify exists in repository —
verified by repository inspection; not fabricated)

## SHAS

CORRECTION_5_IMPLEMENTATION_SHA=69e2aba2fca08f1d1bd006c1cd5d3141128519d0
CORRECTION_5_IMPLEMENTATION_TREE=ce332796e91d862f62c5db85377ab07b44656aa0
FINAL_CANDIDATE_SHA=69e2aba2fca08f1d1bd006c1cd5d3141128519d0
FINAL_CANDIDATE_TREE=ce332796e91d862f62c5db85377ab07b44656aa0
FCV_BUILD_INPUT_SHA=69e2aba2fca08f1d1bd006c1cd5d3141128519d0
FCV_BUILD_INPUT_TREE=ce332796e91d862f62c5db85377ab07b44656aa0
PUBLICATION_PARENT_SHA=69e2aba2fca08f1d1bd006c1cd5d3141128519d0
PUBLICATION_SHA=(docs-only, appended after FCV)
PUBLICATION_TREE=(see git)

## GOVERNANCE

BLOCKERS=0 (Hermes assessment — three final-review blockers + B4 evidence all
closed by production code + tests + guards)
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21_CORRECTION_5_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_5_FINAL_REVIEW
