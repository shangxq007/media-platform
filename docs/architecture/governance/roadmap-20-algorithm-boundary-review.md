# ROADMAP #20 — Algorithm Boundary Review

Status: PASS
Date: 2026-08-19
Branch: agent/roadmap20-renderplan-decision-recovery
Base: 07de009205e0ee50cad06e5a324ce18f5c46b10d

Purpose: determine algorithm ownership so that no planning algorithm can
change canonical authored meaning, and no provider/runtime algorithm leaks
into canonical RenderPlan. Classification per ROADMAP_20 spec §4.1.

Classification categories:
A. canonical authored-domain algorithms (owned by the authored semantic domain)
B. render-planning algorithms (owned by RenderPlan/RenderGraph)
C. provider/runtime algorithms (owned by provider/runtime layer)
D. optimization algorithms (owned by a future optimizer layer)
E. graph scheduling algorithms (owned by the graph mechanics layer)

Frozen principle this review enforces:
RENDERPLAN_AND_RENDERGRAPH_ARE_DERIVED_EXECUTION_STATE_NOT_AUTHORED_DOMAIN_AUTHORITY_V1
RENDER_CONSUMES_AUTHORED_SEMANTICS; RENDER_DOES_NOT_REDEFINE_AUTHORED_SEMANTICS

---

## 1. Algorithm classification table

| ALGORITHM | CLASS | SEMANTIC_OWNER | INPUT | OUTPUT | DETERMINISM_REQUIREMENT | CAN_CHANGE_CANONICAL_MEANING |
|---|---|---|---|---|---|---|
| temporal mapping evaluation | A | Timeline domain (TemporalMapping: ConstantRate exact rational rate + PlaybackDirection; Freeze exact source position) | TemporalMapping + MediaClip + source range | local→source time correspondence (exact rational) | exact, deterministic | NO |
| trim / source-window evaluation | A | Timeline domain (MediaClip.sourceRange / TimeRange; MediaStreamSourceBinding.sourceRange single authority) | MediaClip + binding sourceRange | exact source window | exact, deterministic | NO |
| transition overlap resolution | A | Timeline domain (TransitionInstance alignment/temporal policy invariants; cross-track prohibited) | TransitionInstance + endpoint clips | transition temporal placement (overlap/insert) | deterministic | NO |
| effect ordering | A | Timeline/Effect domain (EffectInstance applicationRange + authored ordering; diff/merge preserves authored order) | Timeline clip effect list | authored effect order | deterministic | NO |
| audio routing flattening | A | Audio domain (AudioMix: master bus + routes + gain/mute/balance + bounded DSP chain; AudioMixCanonicalSemantics) | AudioMix | canonical audio routing semantics | deterministic | NO |
| subtitle active interval selection | A | TimedText domain (TextElement start/duration; active cue determination) | TextElement + timeline time | active cue intervals | deterministic | NO |
| render extent propagation | B | Render planning (RenderExtent foundation) | RenderRequest extent + authored timeline ranges | per-node required sample windows (exact) | deterministic | NO (consumes authored semantics; never rewrites authored ranges) |
| dependency expansion | B | Render planning (typed dependency model) | RenderPlan + resolution state | typed dependency closure | deterministic | NO |
| materialization decision | B | Render planning (Materialization phase) | canonical authored + resolved source semantics | provider-neutral render requirements/nodes | deterministic | NO |
| required sample window evaluation | B | Render planning (consumes TemporalMapping; does not reimplement it) | TemporalMapping + RenderExtent | sample windows to request | exact, deterministic | NO |
| capability requirement derivation | B | Render planning (consumes EffectDefinition.requiredCapabilities etc.; CapabilityResolutionService exists in app layer) | canonical semantics → capability requirements | typed CapabilityRequirement set | deterministic | NO |
| source resolution state evaluation | B | Render planning (RENDER_SOURCE_RESOLUTION_STATE_V1) | binding + availability probes | RESOLVED/PENDING/FAILED/BLOCKED/UNAVAILABLE | deterministic given probe inputs | NO |
| graph topological ordering | E | Graph mechanics (platform-algorithms:graph GraphAlgorithms.topologicalOrder; deterministic Kahn) | DirectedGraphView | sealed TopologicalOrderResult | deterministic (same graph + same node order → same result) | NO |
| cycle detection | E | Graph mechanics (GraphAlgorithms.detectCycles) | DirectedGraphView | CycleDetectionResult | deterministic | NO |
| deterministic traversal / canonicalization ordering | E | Graph mechanics + canonicalization owner (typed node identity ordering) | RenderGraph + node identity | canonical node/edge ordering | deterministic | NO |
| graph validation | E + B | Graph mechanics (structural) + Render planning (semantic/source-state rules; fail closed) | RenderGraph + resolution state | validation result / diagnostics | deterministic | NO |
| common-subexpression elimination | D | Future optimizer layer (explicitly deferred in #20) | RenderGraph + cost model | optimized graph | n/a (deferred) | NO |
| node fusion | D | Future optimizer layer (deferred) | RenderGraph | fused nodes | n/a (deferred) | NO |
| render caching | D (hook only) | Future cache infrastructure; #20 freezes only cache identity hook (RENDER_CACHE_IDENTITY_HOOK_V1) | fingerprint inputs | cache key | deterministic key | NO |
| chunk segmentation | D (hook) | Future segmenter (IncrementalRenderPlan/SegmentPolicy legacy concepts exist; re-derive on typed identity later) | RenderGraph + extent | segments | n/a (deferred) | NO |
| task partitioning | C | Provider/runtime layer (later; worker fabric #22) | execution requirements | tasks | n/a (later) | NO |
| resource scheduling | C | Provider/runtime layer (later) | execution requirements + runtime state | resource assignments | n/a (later) | NO |
| GPU dispatch grouping | C | Provider/runtime layer (later; Vulkan/WebGPU/wgpu) | execution requirements | dispatch groups | n/a (later) | NO |
| provider selection policy | C (consumes external resolution input) | Provider/runtime layer; RenderPlan represents capability requirements only, never provider names (RenderProviderSelectionPolicy exists in infrastructure) | capability requirements + availability + entitlement + policy + quota | bound provider | deterministic given inputs; NOT canonical | NO |
| decode/encode parameter selection | C | Provider/runtime layer (FFmpegCommandFactory etc. stay in infrastructure) | execution requirements + provider constraints | provider command params | n/a (later) | NO |

## 2. Ownership rules frozen by this review

R1. Every algorithm in class A stays with its authored semantic owner. The
    Render planner never re-derives temporal mapping, source windows,
    transition placement, effect order, audio routing, or cue selection; it
    CONSUMES their results (or asks the owner for a bounded evaluation).
R2. Class B algorithms are the only algorithms RenderPlan/RenderGraph may
    own. Their inputs are canonical authored + resolved immutable source
    semantics; their outputs are provider-neutral requirements. None may
    write back to authored state.
R3. Class C algorithms belong to the provider/runtime layer. None may appear
    inside canonical RenderPlan/RenderGraph types (no provider commands, no
    provider names, no GPU/worker identifiers).
R4. Class D algorithms are deferred; #20 freezes only identity/extension
    hooks (cache key inputs, node identity stability) so they are not
    foreclosed.
R5. Class E algorithms are pure mechanics provided by
    platform-algorithms:graph (already deterministic, domain-agnostic).
    RenderGraph supplies typed node/edge identity + validation on top; it
    does not reimplement topology.
R6. ANY algorithm whose evaluation could change canonical meaning (class A
    mis-owned by a planner/provider) is a contract violation and triggers
    ARCHITECTURE_ESCALATION_REQUIRED (spec §49 trigger 1/2).

## 3. Existing-repository ownership check

- Temporal mapping evaluation: already authored-owned
  (timeline.semantics.temporal; exact rational; TemporalAudioExecutionGuard
  fails closed) — no leak.
- Transition overlap: authored-owned (TransitionInstance invariants) — no
  planner copy exists; RenderPlan must consume TransitionInstance semantics.
- Effect ordering: authored-owned (EffectInstance applicationRange +
  authored order preserved through diff/merge) — planner must treat effect
  order as input.
- Audio routing: authored-owned (AudioMix canonical in audio-module;
  AudioMixFfmpegAdapter is an infrastructure consumer, not a redefinition).
- Current render pipeline is provider-inclusive (ProviderBindingCompiler,
  RenderExecutionPlan steps with providerName fields, RenderProfile codec
  strings, FFmpeg command builders in RenderExecutionStepExecutor). Those
  are class C concerns living above an unclaimed class-B layer — the #20
  gap. The new canonical layer must sit between authored semantics and that
  provider stack.
- Ad-hoc topological sorts in render-module (RenderPlanIr, PipelineDagTopology,
  RenderPlanPolicyGuard DFS, WorkflowCycleDetector) are class E
  reimplementations; the contract directs new canonical graph work to the
  kernel (see graph-backend review) without authorizing retirement of the
  legacy ones.

## 4. Verdict

ROADMAP20_ALGORITHM_BOUNDARY_REVIEW = PASS
- No algorithm that can change canonical meaning is assigned to RenderPlan.
- No provider/runtime algorithm is assigned to canonical RenderPlan.
- Class B is bounded and explicit.
- Class E is delegated to the deterministic graph kernel.
