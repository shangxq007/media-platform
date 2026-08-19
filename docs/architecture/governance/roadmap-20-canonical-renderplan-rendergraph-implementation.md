# ROADMAP #20 — Canonical RenderPlan/RenderGraph Implementation Publication

Status: PUBLISHED (docs-only, direct child of the frozen implementation candidate)
Date: 2026-08-19
Branch: agent/roadmap20-renderplan

## 1. Identity chain

ROADMAP20_IMPLEMENTATION_BASE =
d5755e8261a7459b0292bcf9f0f8bd664b3225bc
(refinement commit; tree 34d7b056a1e10ac981765ca213d12eb7cefc9a7f)

IMPLEMENTATION_BRANCH =
agent/roadmap20-renderplan

ROADMAP20_IMPLEMENTATION_SHA =
887f0c06a22648828ad0b03e340f0b268f9eca6f

ROADMAP20_IMPLEMENTATION_TREE =
2aa99beb9a9b00d77273b110326ed3746b22793d

ROADMAP20_IMPLEMENTATION_PARENT =
d5755e8261a7459b0292bcf9f0f8bd664b3225bc

ROADMAP20_PUBLICATION_SHA =
<filled-at-commit>

ROADMAP20_PUBLICATION_TREE =
<filled-at-commit>

ROADMAP20_PUBLICATION_PARENT =
887f0c06a22648828ad0b03e340f0b268f9eca6f

## 2. Scope (implementation diff vs base)

- PRODUCTION: 45 new files under
  com.example.platform.render.domain.renderplan (+ .graph subpackage) —
  canonical logical RenderPlan / RenderGraph domain (contract C1-C38 +
  post-decision refinement; logical layer only).
- TEST: 9 new files under render-module test renderplan package
  (8 test classes + TestPlans fixture).
- BUILD/GUARD: render-module/build.gradle.kts (+ color-image-module,
  + platform-algorithms:graph, + verifyC20RenderPlanBoundaryGuard);
  root build.gradle.kts (guard wired into jooqFoundationCheck);
  platform-app ModularityTest allowlist; docs/modulith-debt-register.md.
- DOCS: this publication.

No provider code, no DB migration, no schema change, no legacy retirement,
no physical/optimizer/worker/GPU implementation.

## 3. Implemented type model (logical layer)

RenderPlan, RenderPlanId, RenderPlanStatus (PLANNABLE/UNRENDERABLE/
PREPARATION_REQUIRED), RenderPlanFingerprint(+Calculator), RenderRequest(+Id),
RenderExtent (exact MediaTime half-open [start,end) + FrameRate),
RenderOutputRequirement(+Role), RenderNode, RenderNodeId (deterministic,
no UUID, revision-context-free), RenderNodeKind (sealed, 12 permits),
RenderCapabilityRequirement(+Id bounded vocabulary), RenderArtifactReference
(sealed SourceArtifact/IntermediateArtifactExpectation/FinalArtifactExpectation),
RenderSourceResolutionState (RESOLVED/PENDING/FAILED/BLOCKED/UNAVAILABLE),
RenderPlanningDiagnostic(+Code, 10 codes), RenderDependency (sealed
DecodedFrames/EffectInput/AudioInput/SubtitleRaster), RenderDependencyEdge
(producer->consumer, data-flow direction), RenderExecutionRequirement (hook),
RenderPlanProvenance (hook), RenderComponentPath(+Kind), RenderSampleWindow,
TimelineRevisionReference, RenderPlanningInput (+ effect-definition catalog),
RenderMaterializer/DefaultRenderMaterializer, RenderPlanner/DefaultRenderPlanner,
RenderPlanCanonicalCodec (deterministic canonical encoding + SHA-256),
RenderGraph, RenderGraphFingerprint, RenderGraphBuilder, RenderGraphValidator,
RenderGraphBuildResult, RenderGraphValidationResult.

## 4. FCV results

### 4.1 Targeted Roadmap #20 tests (renderplan package)

All 8 test classes, pure JVM, no Spring/DB/Testcontainers.

RENDERPLAN_UNIT_TESTS = 35 / 0 failures
RENDERGRAPH_UNIT_TESTS = 14 (canonicalization 4 + kernel delegation 3 + validation negative 7) / 0
DETERMINISM_TESTS = 8 / 0
CANONICALIZATION_TESTS = 4 / 0
GRAPH_KERNEL_DELEGATION_TESTS = 3 / 0
SOURCE_BINDING_TESTS = 3 (E2E pin/digest assertions) / 0
EXACT_TIME_TESTS = 4 / 0
CAPABILITY_BOUNDARY_TESTS = 5 / 0
PROVIDER_NEUTRALITY_GUARD = PASS (verifyC20RenderPlanBoundaryGuard)
FIRST_BOUNDED_PLANNING_E2E = PASS (3 tests: firstBoundedSliceEndToEnd, outputNodeReferencesRequestOutputs, decodeNodeHasExactSourceBindingDigest)

### 4.2 Affected module suites (all 0 failures / 0 errors)

RENDER_MODULE_TESTS = 2792 / 0 / 0 / 19 skipped
TIMELINE_MODULE_TESTS = 771 / 0 / 0 / 0
AUDIO_MODULE_TESTS = 22 / 0 / 0 / 0
ARTIFACT_MODULE_TESTS = 127 / 0 / 0 / 0
COLOR_IMAGE_MODULE_TESTS = 20 / 0 / 0 / 0
FONT_TEXT_MODULE_TESTS = 11 / 0 / 0 / 0
GRAPH_KERNEL_TESTS = 102 / 0 / 0 / 0
REAL_OR_RELEVANT_INTEGRATION_GATES = PASS (timeline/artifact suites include PostgreSQL Testcontainers ITs; full suite includes platform-app Spring-context tests)

### 4.3 Repository gates

ALL_REPOSITORY_VERIFY_TASKS = PASS (jooqFoundationCheck incl. verifyC20RenderPlanBoundaryGuard, verifyPfirr1AuthenticationAuthority, pfirr1RemediationCheck)
MODULITH = PASS (platform-app ModularityTest; debt register + allowlist updated; generated diagrams updated for the two new edges)
BOOTJAR = PASS
PFIRR1_REMEDIATION_CHECK = PASS
GIT_DIFF_CHECK = PASS

### 4.4 Full suite (HERMES_REPORTED_FCV, run on frozen candidate 887f0c06)

FULL_SUITE = 7483 tests / 0 failures / 0 errors / 43 skipped (946 test classes, all modules; ./gradlew test, BUILD SUCCESSFUL in 17m26s)

## 5. Architecture assertions (required by task §46)

RENDERPLAN_IS_LOGICAL_PROVIDER_NEUTRAL = PASS
RENDERGRAPH_IS_LOGICAL_DERIVED_DAG = PASS
RENDERPLAN_CREATES_REVISIONS = NO
RENDERGRAPH_IS_WORKFLOW = NO
PROVIDER_IMPORTS_IN_CANONICAL_PLANNING = 0
WORKER_DEVICE_IDENTITIES_IN_LOGICAL_PLAN = 0
MEDIA_EXECUTION_PLAN_MODULE_NEW_CONSUMERS = 0
CUSTOM_RENDER_TOPOLOGY_IMPLEMENTATION_ADDED = 0
GRAPH_KERNEL_DELEGATION = PASS
LOGICAL_FINGERPRINT_DEPENDS_ON_RUNTIME_AVAILABILITY = NO
LOGICAL_FINGERPRINT_DEPENDS_ON_PROVIDER_PRICE = NO
LOGICAL_FINGERPRINT_DEPENDS_ON_WORKER_DEVICE = NO
OPTIMIZER_IMPLEMENTED_IN_ROADMAP20 = NO
PHYSICAL_RENDERPLAN_IMPLEMENTED_IN_ROADMAP20 = NO
ARCHITECTURE_ESCALATION_REQUIRED = NO

## 6. Known deferrals / migrations

LEGACY_RENDER_PLAN_MIGRATION = DEFERRED
(no legacy type retired; RenderPlanIr / FFmpegLibassBasicRenderPlan /
RenderJobPlan / RenderExecutionPlan remain untouched)

PHYSICAL_LAYER = DEFERRED (#22)
OPTIMIZER = DEFERRED (#22)
PROVIDER/WORKER/DEVICE BINDING = DEFERRED (#22/#23)

## 7. Material blockers / escalation

MATERIAL_BLOCKERS = 0
ARCHITECTURE_ESCALATION_REQUIRED = NO
HERMES_FCV = PASS
ROADMAP20_STATUS = READY_FOR_CHATGPT_INDEPENDENT_FINAL_REVIEW
NEXT_ACTION = CHATGPT_INDEPENDENT_FINAL_REVIEW
