# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED CORRECTION 3 PUBLICATION

STATUS=PENDING_CHATGPT_FINAL_REVIEW (publication cannot self-declare closure)

PREVIOUS_CORRECTION_2_IMPLEMENTATION_SHA=49e402ad43eda9561ed63783610d47b419c1c128
PREVIOUS_CORRECTION_2_PUBLICATION_SHA=12da0a8b5b69a92be394c1f41ea3872839bd6baa
PREVIOUS_CHATGPT_REVIEW=FAIL (ROADMAP_21_CORRECTION_2_FINAL_REVIEW=CORRECTION_REQUIRED)
PREVIOUS_BLOCKERS=B1 extent-pruning graph closure / B2 REUSE_AS_CANONICAL not fully honored /
                 B3 CLEAN FORWARD compatibility surface / B4 digest canonical completeness

C12_C13_ARCH_CORRECTION_SHA=7fd27899ada16ecd61057de9352a0a8ff9744e2f
C12_C13_SELECTED_OPTION=A REMAINS FROZEN
CORRECTION_3_BASE_SHA=12da0a8b5b69a92be394c1f41ea3872839bd6baa

## B1 — GRAPH-CLOSED EXTENT PRUNING (CLOSED)

RenderNodeKind coverage disposition (repository-reality-derived, verified by
real-materializer integration tests):
- DECODE        = clip.timelineRange (authored timeline coords) — coverage set
- EFFECT        = effect applicationRange when authored, else clip.timelineRange — coverage set
- AUDIO_PROCESS = routed clip timeline range (clipId → clip range) — coverage set
- AUDIO_MIX     = aggregate, no single interval — null (never pruned)
- TIMED_TEXT    = #20 TextElement carries FontRational start/duration only (no
                  timeline MediaTime) — null (never pruned; no invented interval)
- COMPOSITE     = aggregate — null
- OUTPUT        = full-extent sink — null

Graph closure: eliminated-producer edges are removed under typed
DISJOINT_COVERAGE evidence (legal — input proven irrelevant); this is NOT
ALL_PRODUCERS_ELIMINATED node pruning (FORBIDDEN, guard +
R-C3-02). Every surviving edge endpoint survives (tested T-C1..T-C13).

PRODUCTION_MATERIALIZER_EXTENT_TEST=PASS (3 integration tests in render-module
obtain the graph from the REAL DefaultRenderMaterializer)

## B2 — REUSE_AS_CANONICAL EXACT COMPLIANCE (CLOSED)

- ExecutionPlanId — strong independent identity, explicit planner input,
  NEVER fingerprint/digest-derived (guard + R-C3-14)
- ExecutionPlanSchemaVersion — restored frozen int-value semantics (V1=1,
  value>=1; major/minor redesign removed — guard + R-C3-05)
- ExecutionEdgeId — LogicalDependencyEdge.edgeId is now typed ExecutionEdgeId
  (no String shadow — guard + R-C3-04)
- ExecutionInputId / ExecutionOutputId — strong typed on InputBinding /
  OutputDeclaration; ExecutionProvider uses List<ExecutionOutputId>
- ExecutionStepId — typed on PhysicalPlanUnit
- ExecutionCreationContext — restored frozen 7-field provenance shape
  (requestedByUserId/requestedByTenantId/requestPurpose/Instant createdAt/
  traceId/parentPlanId/comment) — provenance-only, excluded from digest
  (guard + R-C3-15)

REUSE_AS_CANONICAL_COUNT_EXPECTED=7 ACTUAL=7

## B3 — CLEAN FORWARD (CLOSED)

RenderNode compatibility constructor + backwards-compatible factory DELETED;
all internal callers migrated to the canonical 11-arg constructor with
explicit executionCoverage semantics (materializer 7 sites + render tests 9 +
mep tests). Old caller count 0; old definition count 0.
RENDER_NODE_COMPATIBILITY_CONSTRUCTOR_COUNT=0 (guard + R-C3-06)
RENDER_NODE_BACKWARDS_COMPATIBLE_FACTORY_COUNT=0

## B4 — CANONICAL / DIGEST COMPLETENESS (CLOSED)

Explicit full-value encoding (no presence-only tokens, no Object.toString
semantic authority — OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0):
- ColorDescription: PARAMETRIC (primaries incl. WellKnown/Custom
  chromaticities, transfer, matrix, range) / PROFILE_BASED (format + digest)
  — BT709→BT2020 mutation changes digest (R-C3-08)
- RasterSampleDescription: family/organization/bitDepth/chromaSubsampling/
  chromaLocation/alpha — 8→10 bit mutation changes digest (R-C3-09)
- EffectMaterializationRequirement: category/parameters/ids/version/enabled/
  applicationRange/automationBindings/temporalBehavior/target — explicit
  (R-C3-07)
- AudioInput: trackId+clipId explicit (R-C3-10)
- ContractVersionRange: min.major.minor-max.major.minor explicit
- Physical digest consumes ACTUAL formatVersion + schemaVersion + planFingerprint
  + extent (R-C3-11/12); ExecutionPlanId/provenance excluded (X01-X03)

## EVIDENCE (mechanical)

CONTRACT_BEHAVIOR_TESTS=26
TEMPORAL_GRAPH_CLOSURE_TESTS=11
DIGEST_TESTS=11
IO_CANONICAL_TESTS=17
GUARD_TESTS=21
TOTAL_TARGETED_TESTS=86
TARGETED_FAILURES=0
MATERIALIZER_INTEGRATION_TESTS=3 (render-module)

RED_MUTATIONS_TOTAL=15 (R-C3-01..15: window-vs-extent, APE pruning, dangling
producer, edgeId String, schema major/minor, compat ctor, mat toString,
color presence, bitDepth, audio payload, format/schema omitted, fingerprint id,
provenance in digest)
RED_MUTATIONS_FAIL_DETECTED=15
RED_RESTORED_GREEN=YES

FULL_SUITE_TESTS=7675 FAILURES=0 ERRORS=0 SKIPPED=43 MODULES=40
GATES: verifyGcr2ArtifactAuthority PASS, pfirr1RemediationCheck PASS,
verifyC1Cnm1RedGates PASS, jooqFoundationCheck PASS,
verifyTimelineEffectTransitionCanonicalization PASS,
verifyC20RenderPlanBoundaryGuard PASS, bootJar PASS
MODULITH_GATE=N/A (no ApplicationModules.verify in repository — verified)

## SHAs

CORRECTION_3_IMPLEMENTATION_SHA=ebdc6262c97742a79cfbaeb28d9f4ffc9025814f
CORRECTION_3_IMPLEMENTATION_TREE=d176ca24ea3bad5c840059480912530797228aef
FINAL_CANDIDATE_SHA=ebdc6262c97742a79cfbaeb28d9f4ffc9025814f
FINAL_CANDIDATE_TREE=d176ca24ea3bad5c840059480912530797228aef
FCV_BUILD_INPUT_SHA=ebdc6262c97742a79cfbaeb28d9f4ffc9025814f
FCV_BUILD_INPUT_TREE=d176ca24ea3bad5c840059480912530797228aef
PUBLICATION_PARENT_SHA=ebdc6262c97742a79cfbaeb28d9f4ffc9025814f
PUBLICATION_SHA=(docs-only, appended after FCV)

## GOVERNANCE

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE (Option A remains sound; no authority conflict)
ROADMAP_21_CORRECTION_3_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_3_FINAL_REVIEW
