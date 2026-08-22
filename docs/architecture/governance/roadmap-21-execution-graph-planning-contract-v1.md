# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED ARCHITECTURE CONTRACT V1

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
BASE_SHA=cf8c3abcf9fb2d0ad064246735714a4ac032ca81

This contract is the bounded Roadmap #21 implementation specification.
Implementation authorization remains NO_GO until ChatGPT independent review.

---

## C1. Milestone identity

ROADMAP #21 = EXECUTION_GRAPH_PLANNING. Provider-neutral execution-planning
layer between #20 RenderGraph and #22 runtime execution. #22 =
EXECUTABLE_TASK_GRAPH_AND_RUNTIME_EXECUTION (proposed V2 §14 wording
correction; worker fabric retained as era evidence). No renumbering.

## C2. #20 input boundary

Authoritative inputs: RenderGraph (validated DAG), RenderPlan fingerprint,
exact revision identity, requested RenderExtent (typed), CapabilityRequirement
set declared on RenderNodes, RenderExecutionRequirement (declared intent),
ExecutionRequirement (#21), deterministic product/job plan identity from PRE21
ExecutionPlannerService where applicable.

## C3. #22 output/handoff boundary

#21 output = PhysicalExecutionPlan + typed requirements + deterministic
structural dependencies + provenance identities + digests. #22 adds runtime
binding/scheduling/probing/isolation/cache/reuse/retries/distributed execution.
#22 must not reinterpret canonical media semantics or alter #21 plan semantics
per provider.

## C4. ExecutionRequirement semantics

Typed record (render-module execution-planning package). Deterministic
execution intent. Carries: requested RenderExtent, determinism class,
sandboxed intent, declared capability refs, optional typed structural hints.
Forbidden: provider/worker/device/machine/pod ids, live availability, queue
depth, utilization, probe results. Canonical equality + digest.

## C5. CapabilityRequirement relationship

ExecutionRequirement references declared CapabilityRequirements; never invents
them. No productType/providerType/implementation-class→capability switches.
PLANNER_INVENTED_CAPABILITY_REQUIREMENT_COUNT=0. No PluginRegistryPort queries
as raw authority.

## C6. LogicalExecutionGraph semantics

Deterministic typed DAG. Nodes: typed kinds, deterministic ids derived from
source RenderNode identity + operationKey, source refs, capability refs,
output declarations, materialization intent, sample window. One RenderGraph
node may lower to N logical nodes (N=1 default; explicit decomposition rule).

## C7. Node semantics

Node identity deterministic and stable across equal inputs. Node carries
typed input/output bindings; no storage URI authority; no runtime identity.

## C8. Edge/dependency semantics

Typed edges: DATA_DEPENDENCY (with input/output binding), TEMPORAL_ORDER
(with exact MediaTime windows), BARRIER (synchronization). No float-time
authority — exact rational MediaTime only.

## C9. Graph validation / cycle rules

DAG required. Cycle → typed failure CYCLE_DETECTED. Validation invariants:
all refs resolve, bindings typed, no dangling capability refs, extent
propagation complete.

## C10. Parallel-region semantics

Independence expressed as absence of edges between branches. Structural only;
never runtime scheduling.

## C11. Temporal dependency/window semantics

Exact MediaTime windows on temporal edges; interaction with TemporalMapping
uses existing rational-time semantics; no new time authority.

## C12. RenderExtent handling

Requested RenderExtent is the typed single authority. Extent limits graph
construction; out-of-extent work pruned with proof that omitted work is
outside requested extent.

## C13. Demand-driven pruning

Extent-based elimination REQUIRED_V1; pruning evidence = omitted work provably
outside requested extent (behavioral test).

## C14. PhysicalPlanner authority

Provider-neutral structural shaping. LOGICAL=work semantics; PHYSICAL(#21)=
structural partition/fusion; RUNTIME(#22)=binding/execution. No live provider,
worker, GPU, device, pod, machine selection. No mutable runtime reads.

## C15. Partition/fusion semantics

Partition into plan units REQUIRED_V1. Fusion OPTIONAL_V1 (same window, same
capability refs, no intervening barrier; semantics-preservation proof via
digest comparison of expanded equivalence). Illegal partition/fusion → typed
failure (ILLEGAL_PARTITION / ILLEGAL_FUSION).

## C16. PhysicalExecutionPlan semantics

Typed plan: identity, schema version, digest (canonical SHA-256), units with
typed inputs/outputs/dependencies/temporal windows, propagated requirements
and extent, deterministic cacheability metadata (declarative only).
PROVABLE ABSENCE of provider/worker/device/queue/availability binding.

## C17. Determinism/digest semantics

Same frozen inputs + same ExecutionRequirement → digest-equal logical graph and
physical plan. Forbidden inputs enumerated (runtime availability, worker state,
queue pressure, provider health, GPU inventory, probes, load, Kubernetes state,
time-as-semantic-input, random). PLANNER_DETERMINISM_CONTRACT.

## C18. Persistence/provenance status

V1: transient derived values + digest records. Persistence mechanics must not
define semantics; durable persistence is a #22 decision.

## C19. Typed failure algebra

Module-local enum, V1 classes: INVALID_EXECUTION_REQUIREMENT,
INVALID_LOGICAL_GRAPH, CYCLE_DETECTED, MISSING_SEMANTIC_INPUT,
INCONSISTENT_RENDER_EXTENT, ILLEGAL_PARTITION, ILLEGAL_FUSION,
UNSATISFIED_STRUCTURAL_CONSTRAINT, UNSUPPORTED_V1_PLANNING_CONSTRUCT,
DETERMINISM_INVARIANT_VIOLATION. Each with machine-readable context. No
free-text semantic branching. No global mega error code.

## C20. Module ownership/boundaries

#21 types in render-module execution-planning package (V1). Dependency
direction: render domain → execution planning → (hard boundary) → #22 runtime.
No planner → runtime-infrastructure semantic dependency. No planner → mutable
persistence reads. No cross-module internal exposure.

## C21. No mutable runtime reads

LOGICAL_EXECUTION_PLANNER_RUNTIME_MUTABLE_READ_COUNT=0.
LOGICAL_EXECUTION_PLANNER_RUNTIME_INFRA_DEP_COUNT=0. Missing inputs must be
resolved by the caller/context construction before planning.

## C22. No provider/runtime binding

PHYSICAL_PLANNER_PROVIDER_BINDING_COUNT=0. PHYSICAL_PLAN_WORKER_BINDING_COUNT=0.
PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0. EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0.
EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0.

## C23. Clean-forward migration/deletion policy

media-execution-plan-module DEFERRED (unwired; separate consolidation review —
not a dual authority while zero-wired). ProviderRenderPlan retained as
#22-era infrastructure surface (untouched by #21). No compatibility wrappers.
DUAL_AUTHORITY_ALLOWED=NO.

## C24. Implementation acceptance evidence

Guards + behavioral tests + RED mutations as designed in Decision Recovery §16
(same-inputs determinism, fail-closed invalid/cycle, extent pruning, parallel
independence, temporal exactness, fusion/partition preservation, capability
propagation, runtime-state invariance).

## C25. Deferred items

ExecutableTaskGraph, provider/worker/device binding, runtime scheduling,
probing, isolation, resource allocation, QoS, locality, cache/reuse runtime,
retries/leases/heartbeats, distributed execution → ROADMAP #22+.
Cost Optimizer / Semantic Rewrite / Constraint Kernel / Evidence / Formal
Methods runtimes → future cross-cutting layers (integration seams only).

---

CONTRACT_FREEZE_RECOMMENDATION=SUBMIT_TO_CHATGPT
ROADMAP_21_IMPLEMENTATION=NO_GO
