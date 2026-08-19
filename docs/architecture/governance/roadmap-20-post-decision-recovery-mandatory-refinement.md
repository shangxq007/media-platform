# ROADMAP20_POST_DECISION_RECOVERY_MANDATORY_REFINEMENT

Status: FROZEN (append-forward bounded architecture refinement)
Classification: NON_CONFLICTING_BOUNDED_REFINEMENT
Date: 2026-08-19
Branch: agent/roadmap20-renderplan-decision-recovery
Base: ace2a4ae6fd259579979c3a6bbc64af59ce2160b (tree 14e62226c0e5fa14b20570d7d75f9af6169660ae)
Parent contract: ROADMAP20_CANONICAL_RENDERPLAN_RENDERGRAPH_BOUNDED_ARCHITECTURE_CONTRACT_V1 (FROZEN, C1-C38)

THIS DOCUMENT DOES NOT REWRITE C1-C38. It appends bounded refinements that the
long-term planner architecture requires, freezes the Logical/Physical
distinction, extends the hook surface (optimization, statistics/explain,
preparation, physical layers), allocates future roadmap work, and records the
bounded adoption review of the orphan media-execution-plan-module.

Invariants preserved verbatim from the parent contract (NOT invalidated):
RenderPlan authority boundary (C1), RenderGraph authority boundary (C2),
graph kernel decision (C30), capability requirement boundary (C17),
provider neutrality (C18), source resolution model (C4), stable node identity
(C6), deterministic canonicalization (C8), workflow separation (C34),
OperationPlan separation (C35).

---

## 1. Long-term planner architecture (database-style pipeline)

### DATABASE_STYLE_RENDER_PLANNER_PIPELINE_V1

The long-term planning architecture is a staged, deterministic pipeline in the
tradition of database query planners (parse → bind → logical plan → rewrite →
physical candidate enumeration → cost-based selection → execution plan):

```
Canonical TimelineRevision
→ Semantic Materialization
→ Logical RenderPlan
→ Logical RenderGraph
→ Physical Candidate Enumeration
→ Constraint Filtering
→ Costing
→ Multi-objective Optimization
→ Physical RenderPlan
→ Physical RenderGraph
→ Execution Planning
→ Provider / Worker / Device Binding
→ Execution
```

Roadmap #20 bounded implementation implements ONLY:
Logical RenderPlan + Logical RenderGraph
(plus stable identity, typed requirements, and the physical/optimizer HOOKS
frozen in this refinement).

Physical plan types are CONTRACT/HOOK only in #20; no physical type is
implemented unless an absolutely minimal seam is required (none is required
for the first slice, C29).

### LOGICAL_RENDERPLAN_VS_PHYSICAL_RENDERPLAN_V1

- LogicalRenderPlan = the #20 canonical RenderPlan: provider-neutral WHAT,
  derived from one immutable revision + RenderRequest, deterministic,
  fingerprintable, executable-independent (C1).
- PhysicalRenderPlan = a FUTURE (#22) plan over selected physical
  implementations: which CapabilityImplementation executes which node, with
  what resource profile, under what constraint/objective policy. It is derived
  from the LogicalRenderPlan + PlanningContext; it is NEVER authored; it is
  NOT provider-neutral in the same sense (it names implementation choices,
  not provider commands).

### LOGICAL_RENDERGRAPH_VS_PHYSICAL_RENDERGRAPH_V1

- LogicalRenderGraph = the #20 canonical RenderGraph: validated DAG projection
  of LogicalRenderPlan dependencies (C2, C30).
- PhysicalRenderGraph = a FUTURE (#22) DAG over physical operators with typed
  Exchange/DataMovement/Materialization edges (section 10) and ExecutionIsland
  grouping (section 9). It is derived; it may re-order or fuse only within
  DURABLE_BARRIER limits (section 11) and CAPABILITY_CONFORMANCE limits
  (section 12); it NEVER changes authored WHAT.

### LOGICAL_PLAN_IDENTITY_INDEPENDENT_OF_EXECUTION_BINDING_V1

Logical RenderPlan identity and fingerprint (C7) are functions of authored
semantics + request only. They NEVER include execution binding (provider,
worker, device, prices, queue state). Same logical plan ⇒ same fingerprint
regardless of any physical/runtime state.

### PHYSICAL_IMPLEMENTATION_SELECTION_IS_DERIVED_V1

Physical implementation selection (which CapabilityImplementation, which
worker/device) is a DERIVED decision made by the future optimizer from the
logical plan + PlanningContext. It is transient planning output, never
canonical input, never authored, never persisted as authority.

### EXECUTION_BINDING_IS_TRANSIENT_RUNTIME_STATE_V1

Provider/worker/device binding is transient runtime state: it exists at
execution-planning and execution time, may differ between attempts of the SAME
logical plan, and is never part of logical plan identity, fingerprint, or
canonical storage. This is the execution-attempt identity concern of C6,
generalized to the whole binding.

## 2. Multi-objective optimization foundation

### MULTI_OBJECTIVE_RENDER_OPTIMIZATION_FOUNDATION_V1

The future optimizer selects physical implementations/orderings by
multi-objective optimization over a typed RENDER_COST_VECTOR, subject to typed
hard constraints, honoring an explicit optimization intent. Extensible future
objective dimensions (NOT encoded into #20 production types):

monetary cost, latency, quality/fidelity, CPU, GPU, memory, network, storage,
energy, cache affinity, reliability, determinism, privacy/data residency.

#20 freezes the architecture boundary only; no optimization code, no cost
vector fields in #20 production types.

### HARD_CONSTRAINT_VS_SOFT_OBJECTIVE_V1

Hard constraints and soft objectives MUST remain separate and typed:

HARD (must be satisfied; violation = no valid physical plan):
deadline, budget ceiling, region/residency, required fidelity, required
capability, security/sandbox, determinism requirement.

SOFT (optimized toward; trade-offs allowed):
minimize cost, minimize latency, maximize quality, maximize cache reuse,
minimize energy.

A soft objective can never be promoted implicitly to a hard constraint and
vice versa; the boundary is explicit policy (section 3, OPTIMIZATION_INTENT).

### RENDER_COST_VECTOR_V1

A typed vector of cost components (one entry per objective dimension that a
given physical plan participates in). Vectors are comparable under the
optimization intent's preference model only; there is no universal scalar
cost. Cost model implementations are separate from selection policy
(COST_MODEL_IS_SEPARATE_FROM_SELECTION_POLICY_V1).

### OPTIMIZATION_INTENT_IS_REQUEST_POLICY_NOT_CANONICAL_AUTHORITY_V1

Optimization intent (weights, preferences, hard-constraint bindings) arrives
with the RenderRequest as request policy. It is never authored canonical
Timeline semantics, never part of logical plan fingerprint (C7), and must not
be silently changed by telemetry.

### OPTIMIZER_MAY_CHANGE_HOW_NOT_WHAT_V1

The optimizer may change HOW a logical requirement is physically realized
(implementation, ordering, fusion, colocation, data movement) but NEVER WHAT
(authored semantics, logical requirements, outputs). Any optimizer behavior
that would alter WHAT is a contract violation (escalation trigger §49.1/49.2).

### SEMANTIC_APPROXIMATION_REQUIRES_EXPLICIT_AUTHORIZATION_V1

Semantic approximation (e.g. reduced fidelity, proxy substitution, lossy
intermediate) is FORBIDDEN unless explicitly authorized by request policy and
capability conformance (section 12). The optimizer never decides semantic
equivalence by heuristic.

## 3. Planning context / statistics / explain

### RENDER_PLANNING_CONTEXT_SNAPSHOT_V1

PlanningContext is TRANSIENT planning input — a snapshot of the environment at
planning time. It may eventually include: EffectiveCapabilities,
CapabilityImplementations, AvailableProviders, AvailableWorkers,
DeviceInventory, ArtifactLocality, CacheState, QueueDepth, Prices,
LatencyStatistics, ReliabilityStatistics, RegionPolicy, SecurityPolicy, Quota.

NONE of these become authored canonical Timeline semantics. PlanningContext is
explicitly EXCLUDED from logical plan fingerprint inputs (C7) — the fingerprint
covers authored + request inputs; the context is an execution-affecting input
whose identity is tracked separately (PLANNER_VERSION_AND_STATISTICS_PROVENANCE_V1).

### RENDER_STATISTICS_MODEL_HOOK_V1

Typed statistics hook (latency/reliability/cost histograms per capability
implementation, per region, per worker class) consumed by FUTURE costing.
#20: hook contract only; no statistics infrastructure.

### COST_MODEL_IS_SEPARATE_FROM_SELECTION_POLICY_V1

Cost model (how a physical plan maps to a cost vector) and selection policy
(how vectors are compared/traded under intent) are separate, independently
replaceable, typed components. Neither is canonical authority.

### RENDER_EXPLAIN_MODEL_V1

Future explain surface (inspectable, deterministic, non-authoritative):
EXPLAIN RENDER — logical plan structure + derivation steps;
EXPLAIN RENDER COST — cost vector breakdown per candidate;
EXPLAIN RENDER ALTERNATIVES — enumerated physical candidates + rejected
alternatives with reasons;
EXPLAIN RENDER VERBOSE — full planning trace;
EXPLAIN RENDER ANALYZE — telemetry-backed statistics feedback loop.

### RENDER_EXPLAIN_ALTERNATIVES_V1
### RENDER_EXPLAIN_COST_V1
### RENDER_EXPLAIN_ANALYZE_TELEMETRY_LOOP_V1

- EXPLAIN ALTERNATIVES: the optimizer records the candidate set, the
  constraints applied, and the rejection reasons per alternative (typed, not
  free text).
- EXPLAIN COST: cost vectors are explainable per component (typed
  components, not opaque numbers).
- EXPLAIN ANALYZE: execution telemetry MAY update statistics used by FUTURE
  planning. Telemetry MUST NOT learn or silently mutate authored semantics,
  logical plan content, or request policy. The loop is: plan → execute →
  telemetry → statistics → (future) planning. It is always opt-in via
  request policy.

### PLANNER_VERSION_AND_STATISTICS_PROVENANCE_V1

Planner version (format version) and statistics provenance (which telemetry
fed which statistics snapshot) are recorded in plan provenance (C26) and
PlanningContext identity. Any change in planner version or statistics
provenance may change PHYSICAL decisions but never LOGICAL plan identity.

## 4. DSL / plan-family boundary

### DSL_LOWERS_TO_TYPED_OPERATION_IR_NOT_RENDERPLAN_V1

DSL / UI / MCP / Agent / GraphQL / Canvas inputs lower FIRST to shared semantic
application/Operation IR (the existing typed operation model: OperationRequest
→ resolve → OperationInstance → OperationBatch; operation-module). They do NOT
directly generate RenderPlan, PhysicalRenderPlan, or provider commands.

### DSL_IS_NOT_PROVIDER_COMMAND_LANGUAGE_V1

No DSL is a provider command language. Provider commands remain
infrastructure-layer outputs of provider adapters only.

### PLAN_FAMILY_SEPARATION_V1

Freeze the plan family as DISJOINT families:

OperationPlan    (canonical mutation; operation-module; C35)
!= WorkflowDefinition / WorkflowRun   (durable process; workflow-module; C34)
!= LogicalRenderPlan   (render intent derivation; #20)
!= PhysicalRenderPlan  (physical realization; #22)
!= ExecutionPlan       (execution planning; #22, media-execution-plan-module
                        is the shape precedent)

Canonical mutation → OperationPlan
Durable process    → Workflow
Render intent      → RenderRequest → LogicalRenderPlan

## 5. Preparation requirement model

### WORKFLOW_OWNS_DURABLE_PROCESS_V1 (reinforces C34)
### RENDERGRAPH_OWNS_BOUNDED_RENDER_DEPENDENCY_V1 (reinforces C2/C5)

RenderGraph models bounded render dependencies ONLY. Long-duration or
durable processes are never placed inside RenderGraph.

### RENDER_PLANNER_MAY_EMIT_PREPARATION_REQUIREMENTS_V1

The planner may report per-node/per-request status:
PLANNABLE (logical plan + graph constructible and executable-ready given
current resolution state, C4),
UNRENDERABLE (hard failure; typed diagnostics, C24),
PREPARATION_REQUIRED (executable only after bounded preparation completes).

### PREPARATION_REQUIREMENT_IS_NOT_RENDER_EXECUTION_V1

Preparation is not render execution: it is work that must complete BEFORE
re-planning can yield an executable plan. Preparation never runs inside
RenderGraph nodes; it is represented as a typed PreparationRequirement on the
plan boundary.

### PREPARATION_COMPLETES_TO_ARTIFACT_OR_CANONICAL_INPUT_V1

Preparation completes by producing a durable Artifact (or other canonical
input) — proxy generation, remote content staging, AI asset generation, long
transcode, scene bake, simulation cache, long-running analysis.

### RENDER_REPLANS_AFTER_PREPARATION_V1

When preparation is required:

Planner → typed PreparationRequirement → Workflow (owns the durable process)
→ Artifact / required canonical input becomes available → RE-PLAN
(from the SAME immutable revision; the new plan is a new logical plan whose
fingerprint may include the new artifact pin, C7 — authored semantics
unchanged).

### RECIPE_IS_NOT_WORKFLOW_V1

A recipe (a bounded declarative description of HOW to produce an artifact from
inputs — e.g. a future canonical preparation recipe) is NOT a workflow:
workflow owns durable process orchestration; a recipe is a typed derivation
description consumed by preparation. Neither is a RenderGraph.

## 6. Capability → physical implementation boundary

### CAPABILITY_TO_PHYSICAL_IMPLEMENTATION_BOUNDARY_V1

Freeze the four-level distinction:

Capability                  = WHAT semantic ability exists (provider-neutral;
                              C17 vocabulary: decode.h264, composite.image, ...)
CapabilityImplementation    = WHICH implementation provides it (future #22;
                              typed implementation identity, conformance-tested)
ExecutionCapabilityProfile  = HOW the implementation may physically execute or
                              compose (future #22; resource classes, fusion
                              capability, determinism class)
PhysicalProperties          = representation/locality/memory/device/stream
                              properties at a physical boundary (future hook,
                              section 7)

NEVER inside canonical Capability contract (C17):
provider price, worker id, GPU device, same-process capability, runtime
locality.

## 7. Media physical property model hook

### MEDIA_PHYSICAL_PROPERTY_MODEL_HOOK_V1

HOOK/FUTURE PHYSICAL LAYER contract. Future physical properties may include
typed representations of: encoded/decoded representation, pixel/sample format,
color characteristics, resolution, memory domain, device domain, streaming
state, artifact locality, materialization state.

#20 does NOT build the physical property system; it only reserves the hook
(typed, no Map<String,Object>, no canonical participation).

## 8. Execution islands / fusion / colocation

### EXECUTION_ISLAND_FOUNDATION_V1

ExecutionIsland = a group of physical operations that may execute continuously
within the same implementation/process/worker/device without unnecessary
cross-boundary materialization, while preserving semantic equivalence.
Islands are a FUTURE (#22) physical-graph grouping concept; the foundation is
frozen now.

### OPERATOR_FUSION_SEMANTIC_EQUIVALENCE_RULE_V1

OPERATOR FUSION = multiple logical operations → ONE physical operator. Fusion
is legal ONLY when the fused operator's declared conformance contract proves
semantic equivalence for the fused requirement set (CAPABILITY_CONFORMANCE_GATES_PHYSICAL_SUBSTITUTION_V1).
Fusion must never change authored WHAT.

### PIPELINE_FUSION_FOUNDATION_V1

PIPELINE FUSION = separate physical operators → one streaming/same-process
pipeline (e.g. filter-graph style composition). Same equivalence rule; typed
pipeline boundaries must not cross DURABLE_BARRIER limits (section 10).

### EXECUTION_COLOCATION_FOUNDATION_V1

COLOCATION = separate tasks/processes → same worker/machine/device. Colocation
is a physical placement decision (future #22/#23), derived from
PlanningContext; it never changes authored WHAT and never crosses
region/privacy/security barriers (section 10).

## 9. Data movement / exchange

### DATA_MOVEMENT_AND_EXCHANGE_MODEL_V1

Future PhysicalRenderGraph must make expensive boundaries visible through typed
Exchange/DataMovement/Materialization concepts. Physical optimization accounts
for (eventually): compute cost, decode/encode cost, CPU↔GPU transfer, device
transfer, process boundary, machine boundary, network transfer, intermediate
artifact materialization, storage IO, startup cost, queue delay.

### MATERIALIZATION_IS_AN_EXPLICIT_PHYSICAL_COST_V1

Materialization (intermediate artifact writes/reads) is an explicit physical
cost in the cost vector, never free, never implicit.

### OPTIMIZE_THE_DATA_PATH_NOT_ONLY_THE_OPERATORS_V1

Optimization scope includes the data path (exchange/materialization/locality),
not only operator selection. Do NOT implement this physical graph in #20.

## 10. Durable barriers

### DURABLE_BARRIER_LIMITS_PHYSICAL_FUSION_V1

The optimizer MUST NOT fuse across semantic/durable barriers such as:
canonical revision commit, human interaction, external asynchronous event,
durable artifact publication, transaction boundary, security boundary,
region/privacy boundary, long retry boundary.

### EXECUTION_OPTIMIZATION_REGION_V1

Fusion/locality optimization operates ONLY inside a valid bounded optimization
region — the maximal connected subgraph that respects all durable barriers.
Regions are derived, typed, and re-derived per physical planning run.

## 11. Capability conformance rule

### CAPABILITY_CONFORMANCE_GATES_PHYSICAL_SUBSTITUTION_V1

A physical implementation may replace/fuse logical capability requirements
ONLY when its declared implementation has PASSED the relevant capability
conformance contract (typed conformance evidence, not heuristic). The
optimizer never decides semantic equivalence by heuristic; absence of
conformance evidence = substitution forbidden (fail closed).

## 12. Roadmap allocation

Freeze implementation allocation:

ROADMAP #20  — Logical RenderPlan, Logical RenderGraph, stable identity,
               typed requirements, physical/optimizer hooks (this refinement).
ROADMAP #21  — GPU/device execution properties; Vulkan/WebGPU/wgpu
               implementation concerns.
ROADMAP #22  — Physical planning: CapabilityImplementation profiles, candidate
               enumeration, ExecutionIsland, provider/worker/device locality,
               physical binding, cost/latency inputs; Execution Planning
               (media-execution-plan-module adoption path, section 13).
ROADMAP #23  — Distributed scheduling: queue pressure, worker utilization,
               cross-worker placement, deadline/resource scheduling.

Do NOT pull #21/#22/#23 implementation into #20.

## 13. MEDIA_EXECUTION_PLAN_MODULE_ADOPTION_REVIEW (REQUIRED by task §14)

Result: PASS (bounded adoption review of the 53-type orphan module).

Principle: canonical semantic authority follows the frozen #20 contract, NOT
existing type quantity. The module is structurally an EXECUTION-PLANNING layer
(downstream of logical planning in the DATABASE_STYLE pipeline), not the
canonical logical RenderPlan/RenderGraph layer. It remains unconsumed in #20;
no new consumers are added in #20; its types are classified below for the
#22 adoption path.

Evidence: module included in settings.gradle but NO other module depends on it
and no code imports com.example.platform.execution.* (reality report §6/§7);
it depends on artifact-module + render-module + platform-algorithms:graph
(direction compatible with a future execution-planning layer); MediaExecutionPlan
is immutable + deterministic digest + "Backend-neutral — no reference to
FFmpeg, Kubernetes, OpenCue" (verified first-hand); ExecutionDependency
forbids self-edges (verified); ExecutionResourceRequirement is fully typed
(verified); ExecutionPlanErrorCode has a rich typed vocabulary incl.
EXECUTION_PLAN_CYCLE / EXECUTION_PLAN_NOT_DETERMINISTIC /
EXECUTION_PLAN_NON_DETERMINISTIC_CACHE_KEY / EXECUTION_PLAN_CROSS_TENANT_INPUT
(verified).

### Classification

REUSE_AS_IS (value/pattern types — provider-neutral, deterministic, typed):
- ExecutionDeterminism (DETERMINISTIC/CONDITIONALLY_DETERMINISTIC/NON_DETERMINISTIC) — per-step determinism declaration precedent (C20).
- ExecutionCapabilityRequirement — capability requirement value object (C17/C19 shape; contract C19 already names it).
- ExecutionResourceRequirement + CpuClass/MemoryClass/GpuRequirement/NetworkRequirement/TemporaryStorageClass — typed resource requirement values (C19 hook material).
- ExecutionPlanErrorCode — typed diagnostic vocabulary precedent (C24).
- ExecutionPlanDigestCalculator / ExecutionPlanCanonicalSerializer — deterministic digest/serialization patterns (C7/C8).
- MediaExecutionPlanValidator / MediaExecutionGraphProjection — validation + graph projection delegating to the graph kernel (C23/C30 precedent).
- ExecutionCacheKey — cache-key shape (C21 hook).

REUSE_SHAPE_ONLY (shape for the future #22 physical/execution-planning layer;
NOT the logical-layer types):
- MediaExecutionPlan / MediaExecutionStep — immutable typed plan/step shape.
- ExecutionDependency + ExecutionDependencyType{DATA,CONTROL} — typed edge shape (logical graph keeps C5 semantic variants; DATA/CONTROL belongs to the physical layer).
- ExecutionInputBinding / ExecutionOutputDeclaration / ExecutionInputId / ExecutionOutputId / ExecutionEdgeId / ExecutionStepId / ExecutionPlanId — typed I/O/edge identity shapes.
- ExecutionStepKind / ExecutionStepFailurePolicy / ExecutionCreationContext — step-kind taxonomy, failure policy, creation context shapes.
- MediaExecutionPlanBuilder — builder pattern shape.

ADAPT_LATER (to be wired only in #22, and only as a consumer of the logical
layer):
- MediaOperation family (Analysis/AudioMix/Compose/Crop/Decode/GeneratedMedia/
  IntegrityVerification/MediaInspection/Package/Scale/SubtitleBurnIn/Thumbnail/
  Transcode/Trim/Waveform) — physical operation vocabulary for #22; MUST be
  driven by LogicalRenderPlan nodes, never by raw timeline.
- TimelineToExecutionPlanCompiler — MUST be adapted to consume LogicalRenderPlan
  (never the raw timeline directly; otherwise it bypasses the canonical logical
  layer). Must NOT be wired into any #20 production path.
- MediaBackendCompiler / ExecutionProvider / ExecutionCreationContext — backend
  compile + provider seam for #22.

RETIRE_LATER:
- None required today. If the module remains unconsumed through #21, its
  RETIRE_LATER status is re-evaluated at #22 entry (no action in #20).

DO_NOT_USE:
- The module as a whole as the canonical RenderPlan/RenderGraph authority
  (canonical semantic authority = frozen #20 contract).
- MediaExecutionPlan.timelineRevisionId / timelineRevisionDigest as STRING
  fields must not become the logical layer's revision-reference pattern
  (logical layer uses typed TimelineRevision identity; C7).
- Map-based or stringly-typed fields, if any surface during #22 adaptation,
  are rejected under the no-Map<String,Object> rule (C19).

## 14. Consistency with the parent contract

Every frozen name in this refinement is explicitly NON-CONFLICTING:

| Refinement | Parent contract anchor | Relation |
|---|---|---|
| DATABASE_STYLE_RENDER_PLANNER_PIPELINE_V1 | C16 (materialization/planning phases), C29 (slice) | extends: full pipeline reserved, #20 implements logical stages only |
| LOGICAL_*/PHYSICAL_* separation | C1/C2/C3 | extends: logical = C1/C2; physical = future derived layer |
| LOGICAL_PLAN_IDENTITY_INDEPENDENT_OF_EXECUTION_BINDING_V1 | C7 | reinforces (fingerprint excludes binding) |
| PHYSICAL_IMPLEMENTATION_SELECTION_IS_DERIVED_V1 / EXECUTION_BINDING_IS_TRANSIENT_RUNTIME_STATE_V1 | C6 (execution-attempt identity), C18 | reinforces |
| MULTI_OBJECTIVE_* / HARD_CONSTRAINT_VS_SOFT_OBJECTIVE_V1 / RENDER_COST_VECTOR_V1 / OPTIMIZER_MAY_CHANGE_HOW_NOT_WHAT_V1 | C18/C20/C22/C31 | reinforces (class D optimizer deferred, hooks only) |
| SEMANTIC_APPROXIMATION_REQUIRES_EXPLICIT_AUTHORIZATION_V1 | C17/C18/C31 | reinforces |
| RENDER_PLANNING_CONTEXT_SNAPSHOT_V1 / RENDER_STATISTICS_MODEL_HOOK_V1 / COST_MODEL_IS_SEPARATE_FROM_SELECTION_POLICY_V1 | C20 (plan determinism inputs) | extends: context is transient, excluded from fingerprint |
| RENDER_EXPLAIN_* / PLANNER_VERSION_AND_STATISTICS_PROVENANCE_V1 | C26 (provenance) | extends |
| DSL_* / PLAN_FAMILY_SEPARATION_V1 | C34/C35 + operation-module | reinforces (plan families disjoint) |
| PREPARATION_* / WORKFLOW_OWNS_DURABLE_PROCESS_V1 / RECIPE_IS_NOT_WORKFLOW_V1 | C2/C5/C34 | extends: preparation is a plan-boundary hook, never graph nodes |
| CAPABILITY_TO_PHYSICAL_IMPLEMENTATION_BOUNDARY_V1 | C17 | extends (four-level distinction) |
| MEDIA_PHYSICAL_PROPERTY_MODEL_HOOK_V1 | C14 | extends (physical side of color/image) |
| EXECUTION_ISLAND_* / PIPELINE_FUSION_* / EXECUTION_COLOCATION_FOUNDATION_V1 | C31 (class D deferred) | hooks only |
| DATA_MOVEMENT_AND_EXCHANGE_MODEL_V1 / MATERIALIZATION_IS_AN_EXPLICIT_PHYSICAL_COST_V1 | C15/C21 | extends (physical layer) |
| DURABLE_BARRIER_LIMITS_PHYSICAL_FUSION_V1 / EXECUTION_OPTIMIZATION_REGION_V1 | C2/C34 | reinforces |
| CAPABILITY_CONFORMANCE_GATES_PHYSICAL_SUBSTITUTION_V1 | C17/C18/C31 | reinforces |
| ROADMAP_ALLOCATION | C29 (slice) | reinforces (#20 logical only) |
| MEDIA_EXECUTION_PLAN_MODULE_ADOPTION_REVIEW | C30 (kernel), C19 (hooks) | PASS; module stays orphan until #22 |

REFINEMENT_CONFLICT_WITH_ORIGINAL = NO

## 15. Required final status

ROADMAP20_DECISION_RECOVERY = PASS
ORIGINAL_CONTRACT = FROZEN (C1-C38 untouched)
POST_DECISION_REFINEMENT = FROZEN (this document)
REFINEMENT_CONFLICT_WITH_ORIGINAL = NO
MEDIA_EXECUTION_PLAN_MODULE_ADOPTION_REVIEW = PASS
MATERIAL_BLOCKERS = 0
ARCHITECTURE_ESCALATION_REQUIRED = NO
FINAL_ROADMAP20_IMPLEMENTATION_CONTRACT = ORIGINAL_CONTRACT + POST_DECISION_REFINEMENT
READY_FOR_ROADMAP20_IMPLEMENTATION = YES
ROADMAP20_IMPLEMENTATION_STARTED = NO
NEXT_ACTION = ROADMAP_20_CANONICAL_RENDERPLAN_RENDERGRAPH_BOUNDED_IMPLEMENTATION
(separate authorization required)
