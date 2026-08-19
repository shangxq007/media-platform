# ROADMAP20_CANONICAL_RENDERPLAN_RENDERGRAPH_BOUNDED_ARCHITECTURE_CONTRACT_V1

Status: FROZEN (decision recovery)
Date: 2026-08-19
Branch: agent/roadmap20-renderplan-decision-recovery
Base: 07de009205e0ee50cad06e5a324ce18f5c46b10d (tree 2b1c17ad7e958bbfe74a035fe0950307d18a2ffb)
Supersedes: nothing (first canonical RenderPlan/RenderGraph contract)
Companion documents:
- roadmap-20-repository-reality-report.md (COMPLETE)
- roadmap-20-algorithm-boundary-review.md (PASS)
- roadmap-20-graph-backend-adoption-review.md (PASS)
- roadmap-20-render-architecture-research-review.md (PASS)

Frozen principle (verbatim from task):
RENDERPLAN_AND_RENDERGRAPH_ARE_DERIVED_EXECUTION_STATE_NOT_AUTHORED_DOMAIN_AUTHORITY_V1
RENDER_CONSUMES_AUTHORED_SEMANTICS; RENDER_DOES_NOT_REDEFINE_AUTHORED_SEMANTICS

Conventions: "canonical authored state" = Timeline revision + TimelineSourceBinding +
TemporalMapping + Effect + AudioMix + TextElement + Artifact + color/image semantics.
Package base com.example.platform.

---

## Preamble — what this contract does and does not do

This contract freezes the architecture boundary for a canonical, deterministic,
provider-neutral RenderPlan and validated RenderGraph, and the smallest bounded
implementation slice. It is a decision-recovery artifact: DOCS-ONLY, zero
production code, zero migrations, zero provider work. It does not authorize
Roadmap #20 implementation (separate authorization required:
ROADMAP_20_CANONICAL_RENDERPLAN_RENDERGRAPH_BOUNDED_IMPLEMENTATION).

---

## 1. Frozen definitions (sections 5.1 / 5.2 / 5.3 of the task)

RENDERPLAN_AUTHORITY_BOUNDARY_V1:
RenderPlan = immutable, deterministic, derived, typed, provider-neutral plan
describing WHAT must be rendered/materialized from ONE immutable TimelineRevision
(+ a RenderRequest: exact RenderExtent + output requirements + capability context),
BEFORE provider-specific execution. It is inspectable, hashable/fingerprintable,
serializable-when-justified, and safe to validate/preview. It is NOT a Timeline
replacement, NOT an FFmpeg command list, NOT a Vulkan command buffer, NOT a
workflow definition, NOT a durable job execution record. It never creates
revisions. It consumes authored semantics as immutable inputs and never redefines
them.

RENDERGRAPH_DERIVED_DAG_V1:
RenderGraph = validated DAG projection of RenderPlan's execution-relevant
materialization steps and their dependencies. It owns execution dependency
topology, typed node dependency edges, acyclicity, and deterministic graph
identity/traversal/canonicalization. It does NOT own authored clip semantics,
semantic merge, Timeline revision identity, business entitlement, or provider
selection policy (provider selection is external resolution input, never
canonical plan content).

RENDERPLAN_RENDERGRAPH_SEPARATION_DECISION =
TWO_TYPED_LAYERS_ONE_PLANNING_PASS:
RenderPlan (declarative typed plan: nodes, requirements, ordering semantics) and
RenderGraph (validated dependency DAG projection of the plan) are distinct typed
layers with distinct identities (plan fingerprint vs graph canonical digest) and
distinct validation. They are produced in ONE deterministic planning pass by one
planner (C16), so the separation costs no ceremony while preserving the
semantic-vs-topology distinction required by validation (C23), caching identity
(C21), and incremental replanning (C22). Repository reality does not justify
collapsing them into one type: the plan is the inspectable semantic artifact;
the graph is the mechanical, canonicalized projection.

---

## 2. Decision contract

Each decision: DECISION / RATIONALE / REPOSITORY_EVIDENCE / ALTERNATIVES_REJECTED /
IMPLEMENTATION_IMPLICATION / ESCALATION_TRIGGER.

### C1 RenderPlan authority boundary
DECISION: As RENDERPLAN_AUTHORITY_BOUNDARY_V1 above. RenderPlan is derived
execution state, not authored domain authority. It references authored values by
typed value or pinned identity; it never re-defines them.
RATIONALE: The authored-semantics layer is complete and canonical (Timeline
revision, sealed TimelineSourceBinding, TemporalMapping, Effect, AudioMix,
TextElement, Artifact, color/image). The render-planning boundary is unclaimed —
nothing answers "what must be rendered" deterministically today.
REPOSITORY_EVIDENCE: Reality report §2 (authored types complete), §5 (current
render authority is provider-inclusive pipeline; no canonical plan type).
ALTERNATIVES_REJECTED: RenderPlan as provider command list (violates C18);
RenderPlan as durable job record (that is RenderJob, §6 of reality report);
RenderPlan as authored document (violates derived-state principle).
IMPLEMENTATION_IMPLICATION: New bounded render-planning domain package
(com.example.platform.render.domain.renderplan) with zero provider imports.
ESCALATION_TRIGGER: RenderPlan requires redefining Timeline authored semantics
(task §49.1).

### C2 RenderGraph authority boundary
DECISION: As RENDERGRAPH_DERIVED_DAG_V1 above. RenderGraph is a mechanical
projection with typed semantics; it never owns authored content or business
policy.
RATIONALE: Dependency topology is the only thing the graph adds beyond the plan;
owning anything else would duplicate an existing authority (Timeline, Effect,
AudioMix, TimedText, entitlement).
REPOSITORY_EVIDENCE: platform-algorithms:graph kernel exists; media-execution-plan
delegates to it; render-module's four ad-hoc DAG implementations are the accident
to avoid (reality report §6).
ALTERNATIVES_REJECTED: RenderGraph as workflow graph (C34); as scene graph
(deferred, C27); as artifact-byte owner (C15).
IMPLEMENTATION_IMPLICATION: Thin typed layer over DirectedGraphView; validation
fail-closed.
ESCALATION_TRIGGER: RenderGraph requires duplicating Effect/Audio/TimedText
canonical models (task §49.2).

### C3 RenderPlan/RenderGraph separation
DECISION: RENDERPLAN_RENDERGRAPH_SEPARATION = TWO_TYPED_LAYERS_ONE_PLANNING_PASS
(§1). Distinct types, one planner, one pass. Not collapsed; not ceremonial.
RATIONALE: A single type would conflate plan semantics with graph topology,
weakening validation and foreclosing incremental work; a two-phase pipeline
(separate materialization and planning passes) is ceremony the first slice does
not need (C16 documents the single-pass simplification).
REPOSITORY_EVIDENCE: The existing fragmentation into five plan concepts is the
problem this contract repairs; giving each layer one home prevents recurrence
(reality report §4.6).
ALTERNATIVES_REJECTED: single merged type; fully separate two-phase pipeline.
IMPLEMENTATION_IMPLICATION: RenderPlan + RenderGraph + RenderGraphBuilder in one
package; graph construction is a pure function of the plan + resolution context.
ESCALATION_TRIGGER: covered by C1/C2.

### C4 source resolution state model
DECISION: RENDER_SOURCE_RESOLUTION_STATE_V1 — five states, per render node,
derived at planning time, NEVER persisted into canonical authored state:

| STATE | MEANING | RETRYABILITY | CAN_RENDER | CAN_PLAN | DIAGNOSTIC_REQUIREMENTS |
|---|---|---|---|---|---|
| RESOLVED | binding's immutable source semantics fully resolvable to concrete content (artifact exists + reachable) | n/a (resolved) | YES | YES | none |
| PENDING | resolution in progress (async probe/fetch) | YES (re-poll) | NO | YES | SOURCE_RESOLUTION_PENDING |
| FAILED | resolution definitively failed (content missing / digest mismatch) | only via explicit re-attempt | NO | NO (for that node) | SOURCE_UNRESOLVED / SOURCE_DIGEST_MISMATCH |
| BLOCKED | resolvable but blocked by a condition/dependency (upstream not materialized, capability unavailable) | YES (dependency-driven) | NO | YES | DEPENDENCY_MISSING / CAPABILITY_UNAVAILABLE |
| UNAVAILABLE | resource temporarily unavailable (remote storage/provider down) | YES (transient) | NO | YES | SOURCE_UNAVAILABLE |

Principle frozen: CANONICAL_VALID != CURRENTLY_RENDERABLE. Timeline revision
validity is independent of renderability (binding canonical-valid; artifact
exists historically; remote storage currently unavailable; provider unavailable;
dependency not materialized).
RATIONALE: Task §6. RenderJobStatus is execution lifecycle (9 states) — a
different concern; no source-resolution model exists today.
REPOSITORY_EVIDENCE: RenderJobStatus/RenderJobStateMachine (execution lifecycle);
MediaStreamSourceBinding pins immutable content (so validity and renderability
separate cleanly).
ALTERNATIVES_REJECTED: folding resolution state into RenderJobStatus (mixes
planning and execution lifecycle); folding into authored binding (breaks
immutability).
IMPLEMENTATION_IMPLICATION: typed RenderSourceResolutionState + per-node
resolution context consumed by planning and validation.
ESCALATION_TRIGGER: planning requires mutable-latest resolution to be
deterministic (task §49.7).

### C5 dependency model
DECISION: RENDER_DEPENDENCY_MODEL_V1 — typed dependency variants, sealed
(no universal untyped dependency bag):

- SOURCE_ARTIFACT — node depends directly on a pinned ArtifactId + ContentDigest
  (the immutable source content; artifact-first edge is legal because sources
  are pinned immutable content).
- DECODED_FRAMES — node depends on a prior decode node's output (typed input
  binding).
- EFFECT_INPUT — node depends on an effect input node.
- TRANSITION_DUAL_INPUT — transition node depends on BOTH endpoint clip nodes.
- AUDIO_INPUT — audio node depends on a clip audio source node.
- SUBTITLE_RASTER — timed-text node depends on rasterization input.
- INTERMEDIATE_ARTIFACT — node depends on an intermediate output expectation
  (logical/planned identity, C15).
- FINAL_OUTPUT — output node declares the final artifact expectation.

Dependencies are node-to-node edges. A node MAY hold a direct SOURCE_ARTIFACT
pinned reference. Capability requirements are NODE properties (C17), never
edges. Unresolved dependencies are represented as edges to nodes in
PENDING/BLOCKED/UNAVAILABLE state (C4) — the graph remains constructible;
execution/validation gate on state. Dependencies are immutable records.
Diagnostics propagate: a failed dependency marks dependents BLOCKED (or FAILED
when irrecoverable) with typed diagnostics (C24); never silent skip.
RATIONALE: Task §7; typed variants only where actually used (task §33).
REPOSITORY_EVIDENCE: media-execution-plan ExecutionDependency +
ExecutionDependencyType{DATA,CONTROL} + typed InputRole/OutputRole precedent;
AudioMixInput (trackId, clipId) typed routing; RenderPlanIr EdgeType{DATA,CONTROL}
(legacy, string-ish).
ALTERNATIVES_REJECTED: universal untyped edge bag; edges as provider names.
IMPLEMENTATION_IMPLICATION: sealed RenderDependency variants; graph validation
checks endpoint existence + variant/node-kind compatibility.
ESCALATION_TRIGGER: none specific.

### C6 RenderNode identity
DECISION: RENDER_NODE_IDENTITY_V1 —
NODE_SEMANTIC_IDENTITY = deterministic typed RenderNodeId derived from: plan
identity (revision id + request fingerprint), semantic role (node kind),
component path (trackId/clipId/effectInstanceId/textElementId/audioMixInput),
operation kind, and relevant canonical input fingerprints (source binding
digest, effect semanticFingerprint). NO random UUID. Same immutable semantic
inputs → same semantic node identity.
EXECUTION_ATTEMPT_IDENTITY = separate (assigned by the runtime layer at
execution; RenderJob/execution record concern; NEVER in canonical plan).
Do not over-promise content-addressability for unstable provider/runtime
properties (availability, timings) — they never enter node identity.
RATIONALE: Task §8. Deterministic identity underpins caching (C21), graph
equality/canonicalization (C8), reproducibility, and incremental replanning (C22).
REPOSITORY_EVIDENCE: stable-identity discipline throughout authored layer
(trackId/clipId/TextElementId never positional); EffectCanonicalSemantics
semanticFingerprint; legacy RenderPlanIr String plan-<jobId> id is the
anti-pattern.
IMPLEMENTATION_IMPLICATION: RenderNodeId as Comparable record with stable
toString (also satisfies the graph kernel's natural-order determinism, C30).
ESCALATION_TRIGGER: none specific.

### C7 RenderPlan identity / fingerprint
DECISION: RENDERPLAN_IDENTITY_AND_FINGERPRINT_V1 — BOTH, distinct:
- RenderPlanId = logical instance identity (correlation/traceability; e.g.
  revisionId + request id; not persisted as authority).
- RenderPlanFingerprint = deterministic SHA-256 digest over the canonical
  serialization of plan semantics (format version, revision identity, request
  and extent, node semantic identities, dependency structure, typed capability
  and output requirements).
Do NOT reuse TimelineRevisionId as RenderPlan identity; do NOT reuse Timeline
content hash as RenderPlan hash (plan includes request/extent/capability context
absent from the timeline). Fingerprint EXCLUDES provider names, timestamps,
resolution state, execution attempts.
RATIONALE: Task §9.
REPOSITORY_EVIDENCE: TimelineContentDigester, OperationPlanDigest
(domain-separated), ExecutionPlanDigestCalculator patterns.
ALTERNATIVES_REJECTED: only plan id (no equality basis); only fingerprint (no
traceability).
IMPLEMENTATION_IMPLICATION: RenderPlanFingerprintCalculator; hash-participation
inputs enumerated explicitly (roadmap-19 discipline).
ESCALATION_TRIGGER: none.

### C8 graph canonicalization
DECISION: DETERMINISTIC_RENDERGRAPH_CANONICALIZATION = YES.
Authority owner: the canonical RenderGraph serializer/canonicalizer in the
render-planning domain. Rules: nodes ordered by RenderNodeId (Comparable);
edges ordered by (source id, target id, dependency variant); serialization
ordering fixed per format version; canonical parameter encoding = the canonical
encoders of consumed value types (MediaTime num/den, exact rationals, enums by
name — never double, never Map iteration); null/absence rules explicit (absent
omitted vs null rejected, following the repo's no-nullable-god-field discipline);
format versioning per C25. NEVER rely on HashMap iteration, object identity,
random node order, or provider traversal order.
RATIONALE: Task §10. Required for fingerprint equality, graph diffing, and
incremental planning.
REPOSITORY_EVIDENCE: CanonicalSerializer/TimelineContentDigester determinism
discipline; kernel determinism contract; ExecutionPlanCanonicalSerializer.
ALTERNATIVES_REJECTED: canonicalization by insertion order; by provider order.
IMPLEMENTATION_IMPLICATION: explicit ordering functions + determinism tests.
ESCALATION_TRIGGER: none.

### C9 RenderExtent
DECISION: RENDER_EXTENT_FOUNDATION_V1 — RenderExtent is execution
request/planning semantics, NOT authored Timeline semantics. V1 freezes the
temporal extent: exact MediaTime start/end with HALF-OPEN [start, end)
interval semantics (documented convention; end exclusive), plus an exact
FrameRate frame-conversion boundary using MediaTime.toFrameExact (CNM1 exact
rational model — no floating point). Optional output-characteristics hook
(resolution/pixel format/color via color-image value types, C14) WITHOUT
over-generalizing (no image-region/audio-channel dimensions in V1). Partial
render: a request may target a sub-range; planning computes required sample
windows per node via TemporalMapping (C10), including transition-overlap
expansion per TransitionInstance semantics (consumed, not re-derived).
RenderExtent never rewrites authored ranges.
RATIONALE: Task §11. Repository already owns exact MediaTime/FrameRate and
TimeRange conventions.
REPOSITORY_EVIDENCE: shared.time.MediaTime/FrameRate; MediaClip.TimeRange;
legacy RenderProfile (codec-string fields — to avoid).
ALTERNATIVES_REJECTED: extent as authored timeline property; floating-point
extent; over-generalized multi-dimension extent in V1.
IMPLEMENTATION_IMPLICATION: RenderExtent record (start, end MediaTime +
FrameRate + output characteristics hook); validation C23.
ESCALATION_TRIGGER: none.

### C10 temporal evaluation boundary
DECISION: RENDER_TEMPORAL_EVALUATION_BOUNDARY_V1 — Timeline/TemporalMapping owns
source↔timeline semantic mapping (direction/rate authored meaning, exact
rational). Render owns: evaluating REQUIRED SAMPLE WINDOWS and materialization
request windows for a given RenderExtent, and dependency expansion — by
CONSUMING TemporalMapping/MediaClip semantics (class B consumption; redefining
the mapping is class A and forbidden). No floating-point semantic authority
where exact MediaTime exists.
RATIONALE: Task §12; algorithm boundary review (temporal mapping evaluation =
class A; sample-window evaluation = class B).
REPOSITORY_EVIDENCE: TemporalMapping sealed (ConstantRate exact rational +
PlaybackDirection / Freeze exact position); MediaTime exact; TemporalAudioExecutionGuard.
IMPLEMENTATION_IMPLICATION: sample-window evaluator in render-planning domain;
exact rational arithmetic; reverse/freeze handled per authored semantics.
ESCALATION_TRIGGER: planner needs to redefine mapping semantics (task §49.1).

### C11 Effect materialization boundary
DECISION: EFFECT_TO_RENDER_MATERIALIZATION_BOUNDARY_V1 — Effect domain owns
authored effect semantics. Render planning derives: (1) typed CapabilityRequirement
from EffectDefinition.requiredCapabilities + effect semantic fingerprint;
(2) a bounded render-operation requirement keyed to the frozen EffectCategory
vocabulary (TRANSFORM, CROP, OPACITY, BLEND_MODE, COLOR_ADJUSTMENT, GAUSSIAN_BLUR,
FADE, GAIN, PAN, EQUALIZER, COMPRESSOR, LIMITER); (3) a RenderPlan node with
typed parameters referencing the canonical EffectInstance. Canonical RenderPlan
NEVER compiles Effect → FFmpeg string; provider adapters (e.g. infrastructure
EffectFilterGraphBuilder) are the provider-side home. Provider adapters never
define canonical effect meaning.
RATIONALE: Task §13.
REPOSITORY_EVIDENCE: EffectInstance/EffectDefinition (requiredCapabilities,
parameterSchema); EffectCanonicalSemantics.semanticFingerprint; EffectCategory
frozen vocabulary; EffectFilterGraphBuilder in infrastructure (provider side).
ALTERNATIVES_REJECTED: effect→FFmpeg-string compilation in canonical plan;
provider-defined effect semantics.
IMPLEMENTATION_IMPLICATION: EffectMaterializationMapper (pure, typed) in
render-planning domain.
ESCALATION_TRIGGER: provider-specific commands necessary in canonical RenderPlan
(task §49.3).

### C12 AudioMix materialization boundary
DECISION: AUDIOMIX_TO_RENDER_MATERIALIZATION_BOUNDARY_V1 — AudioMix remains
authored-domain authority (audio-module). Render planning derives execution-plan
requirements: source audio dependency per AudioMixInput route (clip→audio),
typed gain/mute/balance/DSP node requirements (AudioGain/AudioMute/
StereoBalance/AudioDspNode), routing dependency, mix node (AudioMasterBus),
master output node. RenderPlan NEVER duplicates the AudioMix canonical model
(it consumes AudioMix semantics as input and carries typed requirement values).
Roadmap #15 deferred items (sends/returns, multichannel positioning, loudness
target details, broad DSP catalog) remain deferred unless a #20 hook requires
them — no opportunistic scope expansion.
RATIONALE: Task §14.
REPOSITORY_EVIDENCE: AudioMix/Route/Input/MasterBus/DspNode in audio-module;
AudioMixFfmpegAdapter is the provider-side consumer; AudioMixOperation exists in
media-execution-plan-module (execution op, later adapter target).
IMPLEMENTATION_IMPLICATION: AudioMaterializationMapper consuming AudioMix;
bounded DSP catalog (GAIN/EQ/COMPRESSOR/LIMITER) honored.
ESCALATION_TRIGGER: none.

### C13 TimedText materialization boundary
DECISION: TIMEDTEXT_TO_RENDER_MATERIALIZATION_BOUNDARY_V1 — TimedText authored
semantics remain in the TimedText domain (TextElement in timeline-module +
font-text value types). Render planning may derive: active cue selection per
RenderExtent (from TextElement start/duration — class B), layout/raster
requirement (TextFrame + StyledText + resolved font runs → raster requirement),
compositing dependency (timed-text node → composite input). RenderPlan is NOT
the TimedText style authority; it references styles and derives requirements.
Rasterization (ShapedGlyphRun) remains font-text execution domain.
RATIONALE: Task §15.
REPOSITORY_EVIDENCE: TextElement (timeline.canonical, with resolvedFontRuns);
StyledText/TextFrame/FontContentDigest/ShapedGlyphRun in font-text-module;
SubtitleBurnInOperation in media-execution-plan-module (execution op, later).
IMPLEMENTATION_IMPLICATION: TimedTextMaterializationMapper consuming TextElement
set + extent.
ESCALATION_TRIGGER: none.

### C14 Color/Image integration
DECISION: COLOR_IMAGE_RENDER_REQUIREMENT_BOUNDARY_V1 — RenderPlan represents
color transform requirements, pixel format/image characteristics, and output
characteristics as TYPED references to color-image-module value types
(ColorDescription incl. Parametric/ProfileBased variants, RasterSampleDescription,
TransferCharacteristic, SignalRange, StaticHdrMetadata, SourceVisualDescription),
never redefining them. Output requirements reference typed color semantics;
providers map them later. Roadmap #18 semantics are closed and consumed, not
re-opened.
RATIONALE: Task §16; frozen dependency direction color-image ← media ← timeline
← render-planning ← providers.
REPOSITORY_EVIDENCE: color-image-module exact type names (note: TransferCharacteristic,
SignalRange — naming differs from task prose); SourceVisualDescription canonical;
legacy RenderProfile codec strings to avoid.
IMPLEMENTATION_IMPLICATION: RenderOutputRequirement carries typed color/image
values from color-image-module types.
ESCALATION_TRIGGER: none.

### C15 Artifact reference model
DECISION: RENDER_ARTIFACT_REFERENCE_MODEL_V1 — three typed reference roles:
- SOURCE_ARTIFACT: pinned (ArtifactId + ContentDigest) reference to the immutable
  source content consumed (from MediaStreamSourceBinding); data-plane immutable
  authority.
- INTERMEDIATE_ARTIFACT_EXPECTATION: logical/planned identity for a to-be-produced
  intermediate (deterministic logical id derived from node identity + output
  role; becomes a persisted ArtifactId at execution registration — never
  pre-created).
- FINAL_ARTIFACT_EXPECTATION: expected final output roles (ArtifactKind
  RENDER_MASTER / DELIVERY_RENDITION) with typed output requirements.
RenderPlan/RenderGraph never own artifact bytes; NO universal Asset god object.
The name ArtifactReference is currently UNCLAIMED in the repository — a typed
RenderArtifactReference (or ArtifactId+ContentDigest pairs) may be introduced
without collision.
RATIONALE: Task §17.
REPOSITORY_EVIDENCE: ArtifactId/ContentDigest in shared-kernel;
MediaStreamSourceBinding carries pinned ArtifactId+ContentDigest; ArtifactKind
incl. RENDER_MASTER/DELIVERY_RENDITION; ArtifactPinService precedent;
ArtifactReference does not exist (audit 2).
ALTERNATIVES_REJECTED: universal Asset superclass; RenderGraph owning bytes.
IMPLEMENTATION_IMPLICATION: typed reference records; no byte ownership.
ESCALATION_TRIGGER: none.

### C16 materialization phase
DECISION: RENDER_MATERIALIZATION_PHASE_V1 — Materialization = transforming
canonical authored semantics + resolved immutable source semantics into
provider-neutral render requirements (nodes + typed requirements). It is NOT
"execute the render". Pipeline: authored semantics → resolve → materialize →
plan → graph → execute. Initial implementation: materialization and planning are
ONE phase (the planner materializes requirements and constructs plan + graph in
one deterministic pass), expressed as SEPARATE pure functions (materializer →
planner → graph builder) so a later split is mechanical, not architectural.
RATIONALE: Task §18; avoids ceremony while keeping the semantic pipeline explicit.
REPOSITORY_EVIDENCE: existing multi-stage compile pipeline precedent
(TimelineNormalizationService → ... → RenderExecutionPlanCompiler);
RenderExecutionPlan javadoc "v0: All steps are planning placeholders with
executionReady=false" + RenderExecutionStep javadoc "planning placeholder.
Does not execute" (compile.executionplan).
IMPLEMENTATION_IMPLICATION: pure functions with explicit inputs/outputs; single
planner entry.
ESCALATION_TRIGGER: none.

### C17 capability requirements
DECISION: RENDER_CAPABILITY_REQUIREMENT_INTEGRATION_V1 — RenderPlan expresses
typed CapabilityRequirement with a bounded provider-neutral vocabulary derived
from canonical semantics (decode.h264, composite.image, effect.blur,
rasterize.timedtext, mix.audio, ...); NEVER provider names, plugin names, or
subscription tiers. Effective access remains capability existence ∩ runtime
availability ∩ entitlement ∩ policy ∩ quota, evaluated by the APP layer
(CapabilityResolutionService, EffectEntitlementPort, entitlement-module) at
execution planning; subscription/business policy NEVER becomes RenderPlan
authored semantics.
RATIONALE: Task §19.
REPOSITORY_EVIDENCE: EffectDefinition.requiredCapabilities;
ExecutionCapabilityRequirement (media-execution-plan-module) as value-object
precedent; CapabilityResolutionService; EffectEntitlementPort.validateEffectAccess;
entitlement-module policy types.
IMPLEMENTATION_IMPLICATION: typed CapabilityRequirement record + bounded
vocabulary; planner derives from canonical semantics; app layer evaluates
effective access.
ESCALATION_TRIGGER: existing Capability model cannot express render requirements
without embedding provider/business policy (task §49.5).

### C18 provider neutrality
DECISION: CANONICAL_RENDERPLAN_IS_PROVIDER_NEUTRAL_V1 = PASS. RenderPlan/
RenderGraph embed NO FFmpeg CLI syntax, Vulkan handles, WebGPU resources, CUDA
identifiers, OpenCue job IDs, worker IDs. Typed provider-neutral execution
requirements ARE allowed (capability requirements, resource classes — C19).
Provider commands are NOT. Provider-specific planning belongs to the provider
layer (later); mapping to execution happens at the boundary (RenderExecutionPlan /
MediaExecutionPlan / provider adapters).
RATIONALE: Task §20.
REPOSITORY_EVIDENCE: RenderPlanIr ToolType{FFMPEG,MLT,REMOTION} + String
"plan-<jobId>" = the anti-pattern; RenderExecutionStep carries providerName
(execution layer — allowed there); FFmpegCommandFactory is infrastructure.
IMPLEMENTATION_IMPLICATION: drift-gate rule: zero provider imports in the
canonical render-planning package.
ESCALATION_TRIGGER: task §49.3.

### C19 execution requirements hooks
DECISION: EXECUTION_REQUIREMENTS_HOOK_V1 — minimal typed extensibility point on
render nodes/outputs: a typed ExecutionRequirement value set — GPU
(NONE/OPTIONAL), memory class, compute class, network, privacy/sandbox intent,
determinism class, QoS/cost intent, locality — V1 freezes a bounded subset as
HOOK-ONLY (declared, not consumed): GPU, memory class, determinism class,
sandbox/intent flag. NO Map<String,Object>. The value-object shapes already
exist in media-execution-plan-module (GpuRequirement, MemoryClass, CpuClass,
NetworkRequirement, TemporaryStorageClass, ExecutionCapabilityRequirement) and
are REUSED (same types or same shapes at the adapter boundary), not reinvented.
RATIONALE: Task §21.
REPOSITORY_EVIDENCE: execution-module resource-requirement types (orphan module,
patterns validated).
IMPLEMENTATION_IMPLICATION: typed ExecutionRequirement set on nodes; zero
consumers in the #20 slice.
ESCALATION_TRIGGER: none.

### C20 determinism model
DECISION: RENDER_DETERMINISM_MODEL_V1 — three separate concepts:
- SEMANTIC_DETERMINISM: authored semantics are immutable exact values (already
  true).
- PLAN_DETERMINISM: YES — same authored revision + same planning inputs
  (request/extent/capability context) → same RenderPlan and same RenderGraph
  (fingerprint equality). Bounded exceptions: none in the slice; any future
  input that is not immutable must be declared in fingerprint inputs.
- EXECUTION_BITWISE_DETERMINISM: NOT_ASSUMED — same RenderPlan + different
  provider/runtime may or may not produce bit-identical output; an
  execution-layer property, declared per step later (ExecutionDeterminism
  precedent).
Do not conflate.
RATIONALE: Task §22.
REPOSITORY_EVIDENCE: ExecutionDeterminism{DETERMINISTIC, CONDITIONALLY_DETERMINISTIC,
NON_DETERMINISTIC} precedent; deterministic digest discipline.
IMPLEMENTATION_IMPLICATION: plan-determinism proven by fingerprint-equality
tests (same inputs → same fingerprint; changed inputs → different).
ESCALATION_TRIGGER: task §49.7.

### C21 cache hook
DECISION: RENDER_CACHE_IDENTITY_HOOK_V1 — no cache infrastructure in #20.
Freeze: cacheable layers (later): source resolution, decode, effect
materialization, intermediate render, final output. Cache key inputs = node
semantic identity (C6) + node requirement fingerprint + dependency output
identities — all deterministic. Invalidation: immutable inputs ⇒ key change ⇒
invalid; NO mutable-latest key. Semantic relationship: cache key ⊇ plan
fingerprint + node identity; caching is an execution-layer concern, never part
of canonical plan semantics.
RATIONALE: Task §23.
REPOSITORY_EVIDENCE: RenderPlanIr input/output-hash + cacheable flag (legacy
precedent); ExecutionCacheKey; RenderRequestFingerprint/RenderDeduplicationService.
IMPLEMENTATION_IMPLICATION: documented key-input contract only; no cache code.
ESCALATION_TRIGGER: none.

### C22 incremental replanning hook
DECISION: INCREMENTAL_RENDER_REPLANNING_HOOK_V1 — a small Timeline revision
change SHOULD result in a bounded RenderGraph change. #20 does NOT implement an
incremental planner; it preserves the preconditions: stable semantic node
identities (C6), typed dependencies (C5), deterministic graph structure (C8),
plan fingerprint (C7). TimelineRenderImpact levels (NONE..FULL_RERENDER) remain
vocabulary; legacy IncrementalRenderPlan/DirtyScope/SegmentPolicy/ReusableArtifact
are re-derivable on typed identity later and are NOT reused as-is.
RATIONALE: Task §24.
REPOSITORY_EVIDENCE: TimelineRenderImpactLevel; legacy incremental types
(domain.planning).
IMPLEMENTATION_IMPLICATION: identity/topology choices only.
ESCALATION_TRIGGER: none.

### C23 graph validation
DECISION: RENDERGRAPH_VALIDATION_V1 — fail-closed rules: unique node identity;
all edge endpoints exist; no cycles (kernel detectCycles); valid dependency
types (sealed variant allowed for the node kind); no self-edge; deterministic
validation diagnostics (stable codes); source-state consistency (a RESOLVED node
claiming resolved dependencies that are not resolved is invalid; unresolved
dependencies imply PENDING/BLOCKED/UNAVAILABLE); capability requirements
structurally valid (known capability id, sane bounds). Validation is pure and
deterministic; structural validation delegates to the kernel; semantic
validation lives in the render-planning domain.
RATIONALE: Task §25.
REPOSITORY_EVIDENCE: MediaExecutionPlanValidator (kernel delegation);
TimelineMediaSemanticsValidator (O(V+E), stable error codes); RenderPlanPolicyGuard.
IMPLEMENTATION_IMPLICATION: RenderGraphValidator; deterministic diagnostic
ordering.
ESCALATION_TRIGGER: none.

### C24 diagnostics
DECISION: RENDER_PLANNING_DIAGNOSTIC_MODEL_V1 — bounded typed diagnostic
categories (enum + typed context record): SOURCE_UNRESOLVED, SOURCE_UNAVAILABLE,
DEPENDENCY_MISSING, CAPABILITY_UNAVAILABLE, GRAPH_CYCLE, INVALID_RENDER_EXTENT,
MATERIALIZATION_FAILED, PLANNING_UNSUPPORTED (+ SOURCE_DIGEST_MISMATCH,
SOURCE_RESOLUTION_PENDING). CANONICAL_INVALID (authored state invalid — the
planner never produces it; that is authored-layer validation) is SEPARATE from
CURRENTLY_UNRENDERABLE (resolution/availability/capability — planner
diagnostics). No arbitrary string exceptions across the architecture; typed
diagnostics with entity/parameter context.
RATIONALE: Task §26.
REPOSITORY_EVIDENCE: TimelineError (19 codes), PlanErrorCode (19 codes),
OperationErrorCode, RenderExecutionPlanFailureReason precedents.
IMPLEMENTATION_IMPLICATION: RenderPlanningDiagnostic record (code, entity,
context, severity).
ESCALATION_TRIGGER: none.

### C25 lifecycle / versioning
DECISION: RENDERPLAN_LIFECYCLE_AND_VERSIONING_V1 — RenderPlans are
ephemeral/derived: persisted only as OPTIONAL reproducibility artifacts
(schema-versioned, non-authoritative). RenderGraph persistence = NONE (always
rebuildable from revision + request). Authored revision = canonical durable
authority (already true). Regeneration: always possible from revision + request.
Old plans: must remain INSPECTABLE if persisted (versioned format); need NOT
remain executable (execution always re-plans from the pinned revision — RenderJob
pins the REVISION, never the plan). Plan/graph carry a format version from day
one.
RATIONALE: Task §27 + §38.
REPOSITORY_EVIDENCE: RenderJobRevisionPinningService (revision-pinned execution
precedent); schema-versioned canonical types everywhere.
IMPLEMENTATION_IMPLICATION: formatVersion fields; optional persistence later as
reproducibility artifacts only.
ESCALATION_TRIGGER: none.

### C26 provenance hook
DECISION: RENDERPLAN_PROVENANCE_HOOK_V1 — RenderPlan can explain: input Timeline
revision id, source bindings (pinned artifact ids/digests), materialized
requirements, planner/format version, capability resolution consumed, and
(eventually) provider/execution linkage (execution attempt identity). Provenance
is explanatory/reproducibility metadata; NEVER canonical authored authority.
Reuse precedent: artifact-module ProvenanceEdge/ProvenanceGraphProjection at the
execution/artifact layer; plan carries a typed provenance record (not Map).
RATIONALE: Task §28.
REPOSITORY_EVIDENCE: artifact-module provenance types; RenderJob trace_id;
RenderCorrelationContext.
IMPLEMENTATION_IMPLICATION: typed RenderPlanProvenance record; linkage hooks
only.
ESCALATION_TRIGGER: none.

### C27 OpenUSD extension hook
DECISION: OPENUSD_RENDERPLAN_EXTENSION_HOOK_V1 — no OpenUSD implementation.
RenderPlan does not assume all content is flat video clips forever. The sealed
TimelineSourceBinding root already reserves future source kinds (S17). Hooks:
source-kind extensibility (C28); per-source-kind materializer dispatch;
scene-derived render requirements arrive as future source-kind semantics.
NO universal Asset superclass.
RATIONALE: Task §29.
REPOSITORY_EVIDENCE: TimelineSourceBinding sealed permits MediaStreamSourceBinding
with sourceKind() discriminator — designed for extension.
IMPLEMENTATION_IMPLICATION: nothing in slice; materializer dispatch keyed on
sourceKind().
ESCALATION_TRIGGER: none.

### C28 future source kinds
DECISION: RENDER_SOURCE_KIND_EXTENSIBILITY_V1 = PASS — RenderPlan consumes the
sealed TimelineSourceBinding root and dispatches on sourceKind(); it never
assumes MediaStreamSourceBinding only. V1 slice supports MediaStreamSourceBinding
(the only existing variant); future SceneSourceBinding / GeneratedSourceBinding /
ProceduralSourceBinding (new sealed permits) require no RenderPlan fundamental
change.
RATIONALE: Task §30.
REPOSITORY_EVIDENCE: TimelineSourceBinding javadoc (S17 future kinds);
sourceKind() canonical discriminator participates in serialization/hash.
IMPLEMENTATION_IMPLICATION: materializer dispatch keyed on sourceKind(); no
MediaStream-only instanceof logic outside the dispatcher.
ESCALATION_TRIGGER: current TimelineSourceBinding cannot express the source
semantics required for the first render slice without breaking its frozen
contract (task §49.4) — audit shows it CAN (pinned artifact + digest + exact
range).

### C29 first bounded implementation slice
DECISION: ROADMAP20_FIRST_BOUNDED_IMPLEMENTATION_SLICE_V1 —
INPUT: one canonical TimelineRevision + RenderRequest (exact RenderExtent +
output requirement + capability context).
SUPPORTS: MediaStreamSourceBinding; exact temporal extent; simple video clip
sources; simple audio (AudioMix routes with gain/mute/balance); basic effect
requirement (frozen EffectCategory vocabulary); timed-text hook (nodes derived;
rasterization not executed); output requirement.
OUTPUT: deterministic provider-neutral RenderPlan + validated RenderGraph DAG.
NO GPU execution, NO provider execution, NO worker dispatch, NO RenderJob
integration (execution wiring is a later milestone).
Serves the real product loop: SourceWork → Adaptation → Screenplay → ShotPlan →
Assets → OperationPlan → Timeline → Audio → basic Effect → Revision → Render
(first small end-to-end authored revision → render plan loop).
RATIONALE: Task §42/§43; no large spectacle to hide a broken small loop.
REPOSITORY_EVIDENCE: authored layer complete (reality report §2);
RenderJobRevisionPinningService ready for later wiring.
IMPLEMENTATION_IMPLICATION: new bounded render-planning package (pure domain) +
determinism/validation tests.
ESCALATION_TRIGGER: none.

### C30 graph backend decision
DECISION: ADOPT_REPOSITORY_GRAPH_KERNEL — platform-algorithms:graph
(com.example.platform.graph) is the mechanical backend for RenderGraph;
RenderGraph is a thin typed projection layer (typed node identity + typed
dependencies + fail-closed validation + deterministic canonicalization +
fingerprint) over DirectedGraphView. NO JGraphT, NO Guava graph, NO graph
database, NO new graph module. Revision-DAG mechanics stay with RevisionGraphService
over the ordered parent-edge table (different concern).
RATIONALE: Graph-backend adoption review §4: the kernel already provides every
mechanical primitive (deterministic cycle detection, topological order,
dependency queries, immutability, sealed results) and is already consumed by
sibling modules; zero new dependency risk.
REPOSITORY_EVIDENCE: kernel API; media-execution-plan delegation precedent;
render-module's four ad-hoc DAG implementations as the anti-pattern.
IMPLEMENTATION_IMPLICATION: RenderGraph wraps kernel views; RenderNodeId record
satisfies the kernel's natural-order determinism; custom-comparator overload is a
future kernel extension hook, not a #20 requirement.
ESCALATION_TRIGGER: a graph backend becomes semantic authority rather than
mechanics (task §49.6).

### C31 algorithm ownership boundary
DECISION: per roadmap-20-algorithm-boundary-review.md (PASS) — class A algorithms
(authored: temporal mapping evaluation, trim/source-window, transition overlap,
effect ordering, audio routing flattening, subtitle interval selection) stay with
their authored owners; class B (render planning: extent propagation, dependency
expansion, materialization decisions, sample-window evaluation, capability
derivation, source-state evaluation) are the only planning-owned algorithms;
class C (provider/runtime: decode/encode parameter selection, task partitioning,
resource scheduling, GPU dispatch grouping, provider selection) lives in the
provider layer; class D (optimizer: CSE, node fusion, render caching, chunk
segmentation) is deferred with identity hooks; class E (mechanics: topo order,
cycle detection, canonicalization ordering) is the graph kernel's.
RATIONALE: task §4.1; every class-A algorithm mis-owned by a planner would change
canonical meaning.
REPOSITORY_EVIDENCE: review table; authored owners exist for every class-A item.
IMPLEMENTATION_IMPLICATION: package + drift-gate enforcement.
ESCALATION_TRIGGER: task §49.1/§49.2.

### C32 persistence strategy
DECISION: RenderPlan persistence = OPTIONAL (reproducibility artifact only,
schema-versioned, non-authoritative). RenderGraph persistence = NONE (rebuildable).
PostgreSQL remains the canonical relational store for AUTHORED state (revision,
artifact, pins) — unchanged; NO new migration in the #20 slice. Derived state
remains rebuildable unless explicit reproducibility justification.
RATIONALE: task §38.
REPOSITORY_EVIDENCE: revision-pinned RenderJob precedent; typed-schema-module
jOOQ discipline (every schema change re-breaks the 4 governance tests — avoided
by zero migrations).
IMPLEMENTATION_IMPLICATION: none in slice (format version reserved).
ESCALATION_TRIGGER: none.

### C33 scale assumptions
DECISION: ROADMAP20_SCALE_ASSUMPTIONS_V1 — initial RenderGraph: hundreds to low
thousands of nodes; single Timeline render request; single-machine planning; no
distributed scheduler; no graph DB. Complexity targets: O(N+E) for DAG
validation/traversal where possible (kernel Kahn algorithms are O(N+E));
canonicalization O(N log N + E log E) for typed ordering (intentional, documented);
fingerprint O(N+E) hashing. Any intentionally more expensive operation must be
documented.
RATIONALE: task §40/§41.
REPOSITORY_EVIDENCE: kernel O(N+E); TimelineDiffEngine O(T+C) precedent.
IMPLEMENTATION_IMPLICATION: documented complexity budget in package docs.
ESCALATION_TRIGGER: none.

### C34 workflow separation
DECISION: RENDERGRAPH_IS_NOT_WORKFLOW_GRAPH_V1 — WORKFLOW OWNS PROCESS; TIMELINE
OWNS MEDIA COMPOSITION. RenderGraph represents render execution dependencies,
not durable business process orchestration. Workflow (workflow-module
RenderExecutionPort + Temporal workflows) may invoke render(plan/request) but
never owns RenderGraph semantics.
RATIONALE: task §36.
REPOSITORY_EVIDENCE: RenderExecutionPort + Local/TemporalRenderExecutionAdapter;
UserWorkflowDefinition node vocabulary (ACTION/APPROVAL/...) — separate.
IMPLEMENTATION_IMPLICATION: zero workflow imports in render-planning domain;
port boundary preserved.
ESCALATION_TRIGGER: none.

### C35 OperationPlan separation
DECISION: RENDERPLAN_IS_READ_DERIVED_FROM_CANONICAL_STATE_V1 — the OperationPlan
transaction model (request → resolve → plan → validate → preview → authorize →
atomic apply → new revision) is WRITE-side; RenderPlan is READ-derived from an
existing immutable revision and NEVER creates revisions. RenderPlan ≠
OperationPlan (operation-module OperationPlan applies authored edits; RenderPlan
derives render requirements). The naming overlap (render-module
OperationPlanApplyService) is noted; no behavior change in #20.
RATIONALE: task §35.
REPOSITORY_EVIDENCE: operation-module OperationPlan/OperationPlanner/ApplyResult
(candidate timeline + atomic apply); RenderJobRevisionPinningService
(revision-pinned render).
IMPLEMENTATION_IMPLICATION: render-planning domain never references
operation/plan apply machinery.
ESCALATION_TRIGGER: none.

### C36 module home + dependency direction (repository-reality addition)
DECISION: Canonical RenderPlan/RenderGraph domain types live in render-module
under a NEW package com.example.platform.render.domain.renderplan (avoids legacy
domain.plan FFmpegLibassBasic*, domain.planning IncrementalRenderPlan*,
infrastructure.renderplan RenderPlanIr*). A later dedicated module extraction is
allowed if Modulith/dependency analysis demands it; the package boundary is the
contract now. Dependency direction (frozen): color-image-module ← media-module ←
timeline-module ← render-planning ← (execution/provider layers). render-planning
depends on shared-kernel, color-image-module, media-module (identity/stream
types), timeline-module (authored semantics), audio-module, font-text-module,
artifact-module (identity/digest only — no persistence dependency), and
platform-algorithms:graph (mechanics). It does NOT depend on operation-module
plan-apply machinery, workflow-module, entitlement-module, or any provider
package.
RATIONALE: Reality report shows render-planning types scattered across legacy
packages with provider coupling; a single bounded home with frozen dependency
direction prevents recurrence.
REPOSITORY_EVIDENCE: module dependency audit (audit 2 §4); Modulith debt-register
convention.
IMPLEMENTATION_IMPLICATION: package + build.gradle.kts dependency set;
Modulith tests updated when implemented.
ESCALATION_TRIGGER: none.

### C37 GraphQL/MCP boundary (repository-reality addition)
DECISION: GraphQL/MCP may later QUERY RenderPlan, RenderGraph, diagnostics,
progress — they never become RenderPlan authority. Existing MCP render-plan
endpoints (McpMediaToolsController getRenderPlan/generateRenderPlan) remain
facades over the planner; no MCP/GraphQL changes in #20.
RATIONALE: task §37.
REPOSITORY_EVIDENCE: McpMediaToolsController endpoints exist today.
IMPLEMENTATION_IMPLICATION: none in slice; facades adapt to the canonical
planner later.
ESCALATION_TRIGGER: none.

### C38 trust/sandbox hook (repository-reality addition)
DECISION: TRUST_PERMISSION_SANDBOX_V1 hook preserved — RenderPlan may express
requirements (privacy class, sandbox intent via C19) but does NOT decide
authorization; the planner consumes already-authorized/effective capability
context where necessary. No worker sandbox implementation in #20.
RATIONALE: task §39.
REPOSITORY_EVIDENCE: EffectEntitlementPort/entitlement-module; RenderPlanPolicyGuard
security-aware planning precedent.
IMPLEMENTATION_IMPLICATION: authorization stays in app layer; plan carries
requirements only.
ESCALATION_TRIGGER: none.

---

## 3. Required minimum type model (task §31 classification)

| Proposed type | Classification |
|---|---|
| RenderPlan | REQUIRED_NOW |
| RenderPlanId | REQUIRED_NOW (logical instance identity) |
| RenderPlanFingerprint | REQUIRED_NOW (deterministic digest) |
| RenderRequest | REQUIRED_NOW (extent + output requirement + capability context) |
| RenderExtent | REQUIRED_NOW (temporal, exact) |
| RenderNode | REQUIRED_NOW |
| RenderNodeId | REQUIRED_NOW (semantic identity) |
| RenderNodeKind | REQUIRED_NOW (bounded V1 kinds, C-node-kind model below) |
| RenderDependency | REQUIRED_NOW (sealed variants, C5) |
| RenderOutputRequirement | REQUIRED_NOW (typed; color/image values) |
| RenderCapabilityRequirement | REQUIRED_NOW (bounded vocabulary) |
| RenderExecutionRequirement | HOOK_ONLY (C19) |
| RenderPlanningDiagnostic | REQUIRED_NOW (C24) |
| RenderSourceResolutionState | REQUIRED_NOW (C4) |
| RenderGraph | REQUIRED_NOW |
| RenderGraphFingerprint/CanonicalForm | REQUIRED_NOW (C8) |
| RenderGraphValidator | REQUIRED_NOW (C23) |
| RenderPlanProvenance | HOOK_ONLY (C26) |
| RenderArtifactReference | REQUIRED_NOW (typed, C15) |
| RenderMaterializer / RenderPlanner / RenderGraphBuilder | REQUIRED_NOW (pure functions, C16) |

No framework-first design: every REQUIRED_NOW type maps to a frozen decision
above; nothing is added for ceremony.

Render node kind model (task §32) — bounded V1 kinds, extension strategy =
sealed interface (new kinds are new permits, never arbitrary strings):
SOURCE, DECODE, TRANSFORM, EFFECT, TRANSITION, AUDIO_PROCESS, AUDIO_MIX,
TIMED_TEXT, COMPOSITE, COLOR_TRANSFORM, MUX, OUTPUT.
(These mirror the example set; the sealed type + permit extension strategy is
the contract — the exact V1 permit set is fixed at implementation within the
slice.)

Graph edge model (task §33): V1 uses DATA_DEPENDENCY semantics via the typed
RenderDependency variants (C5). TEMPORAL/CONTROL categories are NOT invented
without use; a single typed dependency variant per edge, no untyped bag.

Ordering vs dependency (task §34): dependency (graph edges) vs stable
deterministic ordering (canonicalization, C8) vs authored semantic order
(effect ordering — authored, class A) are three distinct concepts. RenderGraph
edge ordering represents execution dependency only; authored order is consumed
as input, never re-encoded as list position in the graph unless it IS a
dependency.

---

## 4. Escalation assessment (task §49)

1. RenderPlan redefining Timeline authored semantics — NO (C1/C10/C31).
2. RenderGraph duplicating Effect/Audio/TimedText models — NO (C2/C11/C12/C13).
3. Provider-specific commands in canonical RenderPlan — NO (C18).
4. TimelineSourceBinding unable to express first-slice source semantics — NO
   (audit shows pinned ArtifactId+ContentDigest+exact range sufficient; C28).
5. Capability model cannot express render requirements without provider/business
   policy — NO (C17; ExecutionCapabilityRequirement + EffectDefinition
   requiredCapabilities precedent).
6. Graph backend becoming semantic authority — NO (C30; kernel is
   domain-ignored mechanics).
7. RenderPlan not deterministic from immutable inputs without mutable-latest —
   NO (C4/C20; all inputs immutable).
8. Roadmap #20 revising Checkpoint A premises — NO (Checkpoint A authored layer
   is consumed as-is; checkpoint-a: publish independent PASS remains base).

ARCHITECTURE_ESCALATION_REQUIRED = NO

## 5. Implementation readiness

ROADMAP20_ALGORITHM_BOUNDARY_REVIEW = PASS
ROADMAP20_GRAPH_BACKEND_ADOPTION_REVIEW = PASS
ROADMAP20_RENDER_ARCHITECTURE_RESEARCH_REVIEW = PASS
ROADMAP20_REPOSITORY_REALITY_REPORT = COMPLETE
ROADMAP20_CANONICAL_RENDERPLAN_RENDERGRAPH_BOUNDED_ARCHITECTURE_CONTRACT_V1 = FROZEN
FIRST_BOUNDED_IMPLEMENTATION_SLICE = FROZEN
MATERIAL_BLOCKERS = 0
ARCHITECTURE_ESCALATION_REQUIRED = NO

READY_FOR_ROADMAP20_IMPLEMENTATION = YES
ROADMAP20_IMPLEMENTATION_STARTED = NO
NEXT_ACTION = ROADMAP_20_CANONICAL_RENDERPLAN_RENDERGRAPH_BOUNDED_IMPLEMENTATION
(separate authorization required — do NOT begin implementation from this commit)
