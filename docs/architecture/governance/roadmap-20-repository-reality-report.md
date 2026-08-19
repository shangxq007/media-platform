# ROADMAP #20 — Repository Reality Report

Status: COMPLETE
Date: 2026-08-19
Branch: agent/roadmap20-renderplan-decision-recovery
Base: 07de009205e0ee50cad06e5a324ce18f5c46b10d (tree 2b1c17ad7e958bbfe74a035fe0950307d18a2ffb)
Scope: READ-ONLY audit. No production code modified.

Audit sources: three parallel read-only inventories (render execution core,
authored semantics, foundations + docs) plus first-hand verification of the
critical types (RenderJob model, RenderPlanIr/DagExecutionEngine, graph
kernel API, TimelineSourceBinding/TemporalMapping sealed variants, AudioMix,
TextElement, CapabilityDescriptor/ExecutionCapabilityRequirement,
media-execution-plan-module).

Package base: com.example.platform.

---

## 1. Summary of findings

1. There is NO canonical RenderPlan/RenderGraph type in the repository.
   Render planning is fragmented across at least five distinct
   representations (see 4.3) and at least four ad-hoc DAG/topological-sort
   implementations (see 6). The RenderPlan/RenderGraph boundary is UNCLAIMED.
2. The authored-semantics layer (Timeline revision, TimelineSourceBinding,
   TemporalMapping, Effect, AudioMix, TextElement, Artifact, MediaTime) is
   complete, canonical, immutable, and hash-participating. It is exactly the
   consumed-side the Roadmap #20 contract needs. No authored semantics need
   rework for the first slice.
3. The currently ACTIVE render pipeline is a provider-inclusive compile
   pipeline: TimelineNormalizationService → ArtifactGraphCompiler →
   CapabilityGraphCompiler → ProviderBindingCompiler →
   ProviderExecutionDocumentDraftCompiler → RenderExecutionPlanCompiler →
   RenderExecutionStepExecutor → LocalExecutionPlanRunner /
   PlanBasedTimelineRevisionRenderService (RenderJob lifecycle around it:
   RenderJobStatus/RenderJobStateMachine/RenderJobRepository).
4. A complete, typed, provider-neutral execution-plan module
   (media-execution-plan-module, 53 types: MediaExecutionPlan,
   MediaExecutionStep, typed operations Decode/Trim/Scale/Transcode/Compose/
   AudioMix/SubtitleBurnIn/Package/..., ExecutionResourceRequirement,
   GpuRequirement, ExecutionCapabilityRequirement, ExecutionDependency,
   ExecutionPlanDigest, ExecutionPlanCanonicalSerializer,
   MediaExecutionPlanValidator, MediaExecutionGraphProjection,
   TimelineToExecutionPlanCompiler) exists but is included in the build and
   consumed by NOBODY — orphan module. It is one of only two consumers of
   platform-algorithms:graph.
5. platform-algorithms:graph (com.example.platform.graph) is a pure,
   deterministic, domain-agnostic graph kernel (DirectedGraphView,
   GraphAlgorithms.detectCycles/topologicalOrder/reachability,
   TopologicalOrderResult/CycleDetectionResult/ReachabilityResult). render-
   module does NOT use it; media-execution-plan-module and workflow-module do.
6. RenderJob is a persisted execution lifecycle entity (render_job +
   render_job_lease/render_job_queue tables, jOOQ) with an execution-oriented
   state machine (QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING
   → COMPLETING → COMPLETED/FAILED/CANCELLED/REJECTED). It is execution
   lifecycle, NOT source-resolution state and NOT a plan.
7. Existing docs describe a legacy mutable RenderPlan record
   (core-editing-rendering-architecture.md §7) that does not match current
   code — doc drift; code is authoritative. platform-roadmap.md has no
   "#20"-numbered entry; roadmap-N docs live in docs/architecture/governance/.
8. Naming: RenderGraph, RenderDependency, RenderExtent are UNCLAIMED names.
   RenderStep and RenderRequest are concrete collisions (domain.RenderStep vs
   infrastructure.RenderStep; several nested RenderRequest records). RenderNode
   exists only nested inside RenderPlanIr. ArtifactReference does not exist
   (name unclaimed). OperationPlan domain types live in operation-module while
   an OperationPlanApplyService also exists in render-module/app/plan.

---

## 2. EXISTING_TYPES_TO_REUSE

Canonical authored semantics (consume, never redefine):

| Type | Package | Role in #20 |
|---|---|---|
| TimelineRevision | timeline.version | Immutable authored revision input for RenderPlan |
| TimelineDocument | timeline.canonical | Canonical document (tracks/clips/audioMix/textElements/relationships) |
| TimelineClipId / TimelineClip | timeline.canonical | Clip identity + persistence projection |
| MediaClip | timeline.semantics.clip | Typed clip temporal semantics (timelineRange/sourceRange/rate) |
| TimelineSourceBinding (sealed) | timeline.semantics.clip | Source-kind abstraction; permits MediaStreamSourceBinding; sourceKind() discriminator |
| MediaStreamSourceBinding | timeline.semantics.clip | Pinned source semantics (mediaAssetId, mediaStreamId, ArtifactId, ContentDigest, sourceRange) |
| TemporalMapping (sealed) | timeline.semantics.temporal | ConstantRate (exact rational rate + PlaybackDirection) / Freeze (exact source position) |
| PlaybackDirection | timeline.semantics.temporal | FORWARD/REVERSE |
| TransitionInstance | timeline.semantics.transition | Transition authored semantics (alignment/policy/duration) |
| EffectInstance / EffectInstance.EffectDefinition | timeline.semantics.effect | Effect authored semantics incl. requiredCapabilities, temporalBehavior |
| Automation | timeline.semantics.automation | HOLD/LINEAR keyframes |
| SemanticRelationship (Sync/Group) | timeline.semantics.relationship | Relationship semantics |
| TextElement / TextElementId | timeline.canonical | Timeline-owned timed-text element (styled text, frame, resolved font runs) |
| AudioMix / AudioRoute / AudioMixInput / AudioMasterBus | audio.domain.mix | Canonical audio mix authority (master bus + routes + gain/mute/balance + bounded DSP) |
| Artifact / ArtifactKind | artifact.domain | Data-plane content identity (RENDER_MASTER/DELIVERY_RENDITION kinds exist) |
| ArtifactId / ContentDigest | shared.identity / shared.digest | Content identity primitives (shared-kernel) |
| MediaTime / FrameRate | shared.time | Exact rational time/rate authorities |
| MediaAssetId / MediaStreamId / MediaStream | media.domain | Source media identity |
| SourceVisualDescription / ColorDescription / RasterSampleDescription / StaticHdrMetadata | colorimage | Color/image foundation (Roadmap #18) |
| StyledText / TextStyle / TextFrame / ResolvedFontRun / FontContentDigest / ShapedGlyphRun | fonttext.* | Text foundation (Roadmap #19) |
| TimelineContentDigester / CanonicalSerializer | timeline.canonical / timeline.semantics.serialization | Deterministic canonical JSON + SHA-256 patterns |
| TimelineRenderImpact / TimelineRenderImpactLevel | timeline.diff | Render-impact vocabulary (NONE..FULL_RERENDER) — currently vocabulary-only |
| OperationErrorCode | operation.operation | CAPABILITY_UNAVAILABLE/POLICY_DENIED precedent for diagnostics |
| PlanErrorCode | operation.plan | Typed planning-error precedent |

Render-side (reuse as seams, not as canonical plan authority):

| Type | Package | Role in #20 |
|---|---|---|
| DirectedGraphView / GraphAlgorithms / TopologicalOrderResult / CycleDetectionResult | platform-algorithms:graph | Deterministic graph kernel (cycle detection, topological order, reachability) |
| RenderJob / RenderJobStatus / RenderJobStateMachine / RenderJobRepository | render.domain / render.infrastructure | Execution lifecycle around a pinned TimelineRevision (RenderJobRevisionPinningService) |
| RenderJobRevisionPinningService | render.app.timeline | Pins render_job → TimelineRevision; canonical backends set |
| CapabilityCatalogService / CapabilityResolutionService | render.app.capability / render.app.planner | Capability → producer → backend resolution |
| RenderPlanPolicyGuard / RenderPlanPolicyResult / RenderPlanPolicyViolationType | render.app.timeline.compile / render.domain.compile.executionplan | Planning policy guard (RAW_COMMAND_EXPOSED etc.) — security-aware planning precedent |
| RenderRequestFingerprint / RenderDeduplicationService | render.app.timeline.compile | Request fingerprint + dedup precedent |
| ExecutionCapabilityRequirement | execution.domain (media-execution-plan-module) | Provider-neutral capability requirement value object (capabilityId + version + features + privacy/region) |
| ExecutionResourceRequirement / GpuRequirement / MemoryClass / CpuClass / NetworkRequirement | execution.domain (media-execution-plan-module) | Typed resource requirement value objects — C19 hook material |
| MediaExecutionPlanValidator / MediaExecutionGraphProjection / ExecutionPlanCanonicalSerializer / ExecutionPlanDigestCalculator | execution.domain | Deterministic validation/serialization/digest patterns (module orphan but patterns reusable) |
| ArtifactPinService / ArtifactPinRepository | artifact.app / artifact.infrastructure | Immutable content pinning precedent |

## 3. EXISTING_TYPES_TO_RETIRE_OR_AVOID

| Type | Package | Why avoid / retire |
|---|---|---|
| RenderPlanIr (RenderNode/RenderEdge, NodeType/ToolType{FFMPEG,MLT,REMOTION}, EdgeType{DATA,CONTROL}) | render.infrastructure.renderplan | Provider-embedded IR (tool field), String planId "plan-"+jobId, Instant.now() created-at (non-deterministic), Map<String,Object> metadata, orphan (no consumers). Anti-pattern for canonical RenderPlan; execution IR at best. |
| DagExecutionEngine / ToolRouter / FFmpegTool / MLTTool / RemotionTool / RenderPlanBuilder / RenderPlanExecutionService / ArtifactCache | render.infrastructure.renderplan | Orphan execution machinery coupled to RenderPlanIr; RenderPlanBuilder still consumes domain.legacy.TimelineClip/TimelineTransition. |
| FFmpegLibassBasicRenderPlan* (24 types) | render.domain.plan | Provider-flavored POC plan (FFmpeg/libass vocabulary), only smoke harness + tests. Not the canonical model. |
| domain.RenderJobPlan + domain.RenderStep (BUILD_TIMELINE/FFMPEG_PROBE/FFMPEG_TRANSCODE/MLT_RENDER_TIMELINE/GPAC_PACKAGE_* types) | render.domain | Legacy steps-plan model; provider-flavored step types; superseded by the compile pipeline. |
| infrastructure.RenderStep (providerType/providerName/inputUri/outputUri/dependsOn) | render.infrastructure | Provider-oriented step record; collision with domain.RenderStep. |
| RenderProfile (resolution/codec/bitrate fields) | render.domain | Legacy profile record; output requirements must be typed, not codec-string fields. |
| domain.legacy package (TimelineClip/TimelineTransition/etc.) | render.domain.legacy | Explicit legacy; RenderPlanBuilder coupling must die with the IR cluster. |
| PipelineExecutionPlan / PipelineDagTopology / PipelineDagExecutorService / PipelineTask | render.app.planner | Hand-rolled server-side DAG pipeline; ad-hoc mechanics; candidates for replacement by canonical RenderGraph + graph kernel. |
| IncrementalRenderPlan / IncrementalTask / DirtyScope / SegmentPolicy / ReusableArtifact / ExternalRenderNode | render.domain.planning | Incremental concepts exist but are string-ish/ad-hoc; the canonical model must re-derive these on typed identity. |
| CapabilityDescriptor (producerId/backendId/backendType) | render.domain.capability | Producer/backend-centric capability descriptor; not the canonical CapabilityRequirement vocabulary. |

## 4. NAMING_COLLISIONS

1. RenderStep — domain.RenderStep (steps-in-RenderJobPlan) vs
   infrastructure.RenderStep (provider-oriented) vs
   RenderExecutionStep (compile.executionplan). Three distinct concepts.
2. RenderRequest — SimpleRenderProvider.RenderRequest,
   RemotionRenderer.RemotionRenderRequest, PromptController.RenderRequest,
   domain.PromptRenderRequest, domain.caption.CaptionTemplateRenderRequest,
   api.dto.TimelineRevisionRenderRequest, PublicSubtitleRenderRequest,
   RenderRequestFingerprint.
3. RenderNode — RenderPlanIr.RenderNode (nested) vs
   ExternalRenderNode (planning) vs ArtifactNode/LogicalCapabilityNode/
   ProviderBindingNode (compile, each with finalRenderNode()).
4. CapabilityDescriptor — render.domain.capability.CapabilityDescriptor vs
   render.infrastructure.providerruntime.capability.CapabilityDescriptor
   (duplicate name).
5. OperationPlanApplyService — render.app.plan.OperationPlanApplyService vs
   operation-module operation.plan.* domain types. Apply machinery split
   across modules.
6. RenderPlan — at least nine distinct concepts share the name (RenderJobPlan,
   RenderExecutionPlan, ExecutionPlan, IncrementalRenderPlan,
   ProviderRenderPlan, PipelineExecutionPlan, RenderPlanIr, RenderPlanner
   seam, RenderPlanService/RenderPlannerService/RenderPlanBridgeService).
7. ExecutionPlan — render.domain.planner.ExecutionPlan vs
   execution.domain.MediaExecutionPlan vs compile.executionplan.RenderExecutionPlan.
8. Unclaimed names (safe to define): RenderGraph, RenderDependency,
   RenderExtent, Materialization (as a type), ArtifactReference,
   RenderNodeId, RenderCapabilityRequirement, RenderPlanningDiagnostic.
9. SubmitRenderJobRequest exists twice (api.dto + app.dto).

## 5. CURRENT_RENDER_AUTHORITY

- Execution lifecycle authority: RenderJob (render_job table) +
  RenderJobStatus + RenderJobStateMachine + RenderJobRepository; pinned to a
  TimelineRevision via RenderJobRevisionPinningService
  (CANONICAL_BACKENDS = {ffmpeg, remotion, gpac, blender}).
- Active planning/execution pipeline (provider-inclusive):
  TimelineNormalizationService (NormalizedTimeline) →
  ArtifactGraphCompiler (ArtifactDependencyGraph, gated by ArtifactDagMode,
  default DISABLED) →
  CapabilityGraphCompiler (LogicalCapabilityGraph) →
  ProviderBindingCompiler (ProviderBindingPlan) →
  ProviderExecutionDocumentDraftCompiler →
  RenderExecutionPlanCompiler (RenderExecutionPlan/RenderExecutionStep,
  "planning placeholder, does not execute") →
  RenderExecutionStepExecutor → LocalExecutionPlanRunner /
  PlanBasedTimelineRevisionRenderService.
- Policy guard: RenderPlanPolicyGuard (DFS cycle detection, policy violation
  types incl. RAW_COMMAND_EXPOSED / STORAGE_INTERNALS_EXPOSED).
- Planner seams: infrastructure.RenderPlanner/DefaultRenderPlanner,
  app.planner.RenderPlannerService (PipelineExecutionPlan),
  app.timeline.IncrementalRenderPlanService/IncrementalRenderOrchestrationService.
- API: RenderController (/api, render-jobs submit/start/execution/
  incremental plan+submit/cache), TimelineRevisionController
  (POST /{revisionId}/render), TimelineGitV1Controller (POST /render-jobs),
  McpMediaToolsController (getRenderPlan/generateRenderPlan/
  generateIncrementalRenderPlan), remote worker controllers.
- Legacy POC/plan vocabularies (non-authoritative): FFmpegLibassBasicRenderPlan,
  domain.RenderJobPlan, RenderPlanIr.

Note: no single type answers "what must be rendered" in a canonical,
provider-neutral, immutable, deterministic way. That is the Roadmap #20 gap.

## 6. CURRENT_GRAPH_MECHANICS

- Canonical kernel: platform-algorithms:graph —
  DirectedGraphView<N> / BidirectionalGraphView<N> / GraphViews
  (adjacency + edges factories), GraphAlgorithms.detectCycles /
  topologicalOrder / reachableFrom / reachability / descendantsBounded /
  ancestorsBounded; sealed results TopologicalOrderResult.Ordered/
  CycleDetected, CycleDetectionResult, ReachabilityResult. Deterministic:
  "same graph + same natural node order → same result" (Kahn's algorithm,
  comparator by Object::toString).
- Consumers of the kernel: media-execution-plan-module
  (MediaExecutionGraphProjection, MediaExecutionPlanValidator) and
  workflow-module (UserWorkflowDefinitionValidator).
- render-module does NOT use the kernel — four+ ad-hoc implementations:
  RenderPlanIr.topologicalSort (DFS), DagExecutionEngine, PipelineDagTopology,
  RenderPlanPolicyGuard (DFS cycle detection), WorkflowCycleDetector (Kahn),
  WorkflowStepOrderResolver, ArtifactGraph (immutable DAG domain type).
- timeline-module: RelationProjections (temporal constraint network, not
  blanket DAG), TimelineDiffEngine (O(T+C), deterministic), RevisionGraphService
  (revision-graph parent traversal: readParents/isAncestor/findBestMergeBase),
  ArtifactDAGImpact (impact vocabulary).
- TimelineMergeEngine is the single media merge authority (not a graph
  library concern).

## 7. CURRENT_EXECUTION_SEAMS

- Provider interfaces: RenderProvider (infrastructure), typed provider
  hierarchy (MediaProcessingProvider→FFmpegRenderProviderInterface,
  CompositionRenderProvider→RemotionRenderProviderInterface,
  TimelineRenderProvider→MltRenderProviderInterface,
  PackagingProvider→GPACPackagingProviderInterface,
  ThreeDRenderProvider→BlenderRenderProviderInterface,
  OverlayProvider→LibassOverlayProviderInterface), RenderProviderRegistry/
  Router/Resolver/SelectionPolicy/FallbackPolicy.
- Backend seam: domain.execution.BackendCompiler →
  BackendExecutionSpec (LocalProcessExecutionSpec, BmfExecutionSpec,
  RemotionExecutionSpec) → ExecutionJob/Task/Command; infra compilers
  (BmfBackendCompiler, LocalProcessBackendCompiler, RemotionBackendCompiler).
- FFmpeg adapters (infrastructure.ffmpeg): FFmpegCommandFactory
  (command-string builder, authoritative), FFmpegRenderProvider,
  FFmpegProbeService, FfmpegDualInputOverlayService, AudioMixFfmpegAdapter;
  plus FFprobeMediaProbeAdapter, EffectFilterGraphBuilder,
  RenderExecutionStepExecutor (builds FFmpeg commands), TimelineRevisionRenderService,
  LocalFfmpegSmokeCommandBuilder, FFmpegWorkerRunner (worker).
- Worker/farm: render.worker (RenderWorkerExecutionService, FFmpegWorkerRunner),
  render.infrastructure.queue (RenderJobQueue, RenderWorkerService,
  SimpleRenderProvider, FFmpegSimpleProvider, JobLeaseRepository),
  render.infrastructure.farm (RenderJobLeaseService, StaleRenderJobLeaseCompensationService),
  remote-render-worker app (HTTP worker registry/heartbeat/job dispatch →
  render-module providers; no own FFmpeg logic).
- Workflow bridge: workflow-module RenderExecutionPort +
  Local/TemporalRenderExecutionAdapter + RenderWorkflow/RenderPipelineWorkflow
  (Temporal) — WORKFLOW OWNS PROCESS, invokes render through a port.
- Execution plan module: execution.domain.ExecutionProvider (provider seam)
  — orphan, unwired.
- Dedup/cache: RenderDeduplicationService + RenderRequestFingerprint,
  RenderIncrementalApiService, ArtifactCache (IR cluster, orphan),
  ExecutionCacheKey (execution module, orphan).

## 8. DOCUMENTATION REALITY

- docs/architecture/platform-roadmap.md: no "#20"-numbered entry; roadmap-N
  work tracked under docs/architecture/governance/roadmap-N-*.md.
- docs/architecture/governance/: roadmap-13 (media canonical v2),
  roadmap-14 (timeline v2), roadmap-15 (audio v2), roadmap-16 (capability
  version lifecycle), roadmap-17 (otio v2 asset-source boundary), roadmap-18
  (color-image foundation + cip2), roadmap-19 (font-text foundation,
  timedtext presentation foundation, completion, corrections), temporal-
  mapping-foundation-v1, semantic-relationship-selection-foundation-v1,
  operation-model-foundation-v1, operation-plan-transaction-model-v1,
  foundation-architecture-lock-v1, checkpoint-a-*.
- core-editing-rendering-architecture.md (§7) describes a legacy mutable
  RenderPlan record — DOC DRIFT vs current code; descriptive, not a frozen
  contract.
- platform-constitution-v1.md (frozen): Product Graph always DAG; Execution
  Task Graph always DAG; Planner is pure; "Planner redesign" = kernel
  evolution → ADR required.
- execution-planner.md: Artifact DAG indefinitely deferred (ADR-025);
  planner must not own FFmpeg/BMF/MLT/Remotion graphs or GPU scheduling.
- execution-environment.md: Backend (WHAT) orthogonal to Environment
  (WHERE/HOW); OpenCue is an Environment, not a Backend.
