# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED ARCHITECTURE CONTRACT V1

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
REVISION=CR-01..CR-06 (2026-08-22, supersedes prior C1..C25 text where in conflict)
BASE_SHA=cf8c3abcf9fb2d0ad064246735714a4ac032ca81
CORRECTION_BASE=35cfe5362108012fd83211b50d323273b1ce6aaf

This contract is the bounded Roadmap #21 implementation specification.
Implementation authorization remains NO_GO until ChatGPT independent review.

---

## C1. Milestone identity and owner module

ROADMAP #21 = EXECUTION_GRAPH_PLANNING. Provider-neutral execution-planning
layer between #20 RenderGraph and #22 runtime execution.
ROADMAP21_OWNER_MODULE=media-execution-plan-module (existing module becomes
the canonical #21 home; render-module remains #20 upstream authority).
#22 = EXECUTABLE_TASK_GRAPH_AND_WORKER_FABRIC_RUNTIME; #23 =
DISTRIBUTED_SCHEDULING. No renumbering.

## C2. #20 input boundary

Authoritative inputs: RenderPlan (with request.extent) + validated RenderGraph
(+ RenderNode declared requirements: capabilityRequirements,
executionRequirements, outputRequirements, materializationRequirements,
requiredSampleWindow) + exact revision identity + deterministic product/job
plan identity from PRE21 ExecutionPlannerService where applicable.
Consistency invariant: graph.planFingerprint == plan.fingerprint.

## C3. #22/#23 output/handoff boundary

#21 output = PhysicalExecutionPlan + normalized ExecutionRequirement projection
+ deterministic structural dependencies + provenance identities + digests.
#22 owns runtime realization (ExecutableTaskGraph, provider/worker/device
binding, probing, isolation, retry/lease/heartbeat, runtime cache mechanics,
bounded/local dispatch). #23 owns cross-worker/global placement policy, queue
pressure, utilization optimization, deadline/resource scheduling, distributed
locality. #22/#23 must not reinterpret canonical media semantics or alter #21
plan semantics per provider. ROADMAP22_23_SCOPE_OVERLAP=0.

## C4. ExecutionRequirement semantics

PURE DERIVED NORMALIZED PROJECTION of RenderPlan + RenderGraph declarations.
MUST NOT independently redeclare RenderExtent, CapabilityRequirement,
RenderExecutionRequirement, RenderOutputRequirement,
RenderMaterializationRequirement, or sample-window semantics — those are
referenced from their #20 canonical declarations. Correlation/request/job
identity not affecting execution semantics: PROVENANCE_ONLY,
EXCLUDED_FROM_SEMANTIC_DIGEST. No CapabilityId-only downgrade.
EXECUTION_REQUIREMENT_DUAL_AUTHORITY=NO.

## C5. CapabilityRequirement relationship

ExecutionRequirement references declared CapabilityRequirements; never invents
them. No productType/providerType/implementation-class→capability switches.
PLANNER_INVENTED_CAPABILITY_REQUIREMENT_COUNT=0.
SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0 (ExecutionCapabilityRequirement
deleted). No PluginRegistryPort queries as raw authority.

## C6. LogicalExecutionGraph semantics

Deterministic typed DAG. RENDER_NODE_TO_LOGICAL_NODE=1_TO_1
(N_TO_M_LOGICAL_DECOMPOSITION=DEFERRED). Logical node retains exact source
RenderNode reference + typed declared requirement references.

## C7. Node semantics

Node identity deterministic and stable across equal inputs. Node carries
typed input/output bindings and references to #20 declarations; no
re-declaration; no storage URI authority; no runtime identity.

## C8. Edge/dependency semantics

Logical dependencies preserve exact RenderDependencyEdge / RenderDependency
semantic variants. NO flattening into a weaker generic DATA edge authority.
PLANNER_INVENTED_BARRIER_COUNT=0 (no invented BARRIER semantics). Temporal
information projects existing exact MediaTime / RenderSampleWindow /
TemporalMapping semantics only. LOGICAL_DEPENDENCY_SEMANTIC_LOSS=NO.

## C9. Graph validation / cycle rules

DAG required. Cycle → typed failure CYCLE_DETECTED. Validation invariants:
all refs resolve, bindings typed, no dangling capability refs, extent
propagation complete, planFingerprint consistency.

## C10. Parallel-region semantics

Independence expressed as absence of edges between branches. Structural only;
never runtime scheduling.

## C11. Temporal dependency/window semantics

Exact MediaTime/RenderSampleWindow/TemporalMapping projections only; no new
time authority; no float-time.

## C12. RenderExtent handling

Requested RenderExtent is the typed single authority (RenderPlan.request.extent
→ extent authority). Extent limits graph construction; out-of-extent work
pruned with proof that omitted work is outside requested extent.

## C13. Demand-driven pruning

Extent-based elimination REQUIRED_V1; pruning evidence = omitted work provably
outside requested extent (behavioral test).

## C14. PhysicalPlanner authority

Provider-neutral structural shaping. LOGICAL=work semantics; PHYSICAL(#21)=
structural partition; RUNTIME(#22)=binding/execution. ONE_LOGICAL_NODE_TO_ONE_
PHYSICAL_PLAN_UNIT for V1. No live provider/worker/GPU/device/pod/machine
selection. No mutable runtime reads.

## C15. Partition/fusion semantics

PARTITION_BASELINE=REQUIRED_V1 (one logical node → one physical plan unit).
FUSION=DEFERRED (requires common LogicalSemanticProjection + equivalence
algorithm before V1 inclusion). TEMPORAL_CHUNKING=DEFERRED.
SEMANTIC_REWRITE=DEFERRED. GENERAL_COST_OPTIMIZATION=DEFERRED.
Illegal partition → typed failure ILLEGAL_PARTITION.

## C16. PhysicalExecutionPlan semantics

Typed plan: identity, schema version, digest (canonical SHA-256), units with
typed inputs/outputs/dependencies/temporal windows, propagated requirement
references and extent, deterministic cacheability metadata (declarative only).
Separate digests: LogicalExecutionGraphDigest vs PhysicalExecutionPlanDigest —
different layer digests are NOT semantic-equivalence proof.
PROVABLE ABSENCE of provider/worker/device/queue/availability binding.

## C17. Determinism/digest semantics

Same frozen inputs + same ExecutionRequirement → digest-equal logical graph and
physical plan. Forbidden inputs enumerated (runtime availability, worker state,
queue pressure, provider health, GPU inventory, probes, load, Kubernetes state,
time-as-semantic-input, random). Provenance-only identity excluded from
semantic digest. PLANNER_DETERMINISM_CONTRACT.

## C18. Persistence/provenance status

V1: transient derived values + digest records. Persistence mechanics must not
define semantics; durable persistence is a #22 decision.

## C19. Typed failure algebra

Module-local enum in media-execution-plan-module, ACTIVE V1 classes:
INVALID_EXECUTION_REQUIREMENT, INVALID_LOGICAL_GRAPH, CYCLE_DETECTED,
MISSING_SEMANTIC_INPUT, INCONSISTENT_RENDER_EXTENT, ILLEGAL_PARTITION,
UNSATISFIED_STRUCTURAL_CONSTRAINT, UNSUPPORTED_V1_PLANNING_CONSTRUCT,
DETERMINISM_INVARIANT_VIOLATION.
ILLEGAL_FUSION is NOT an active V1 surface (fusion DEFERRED); it may be
documented as a future possible failure only. Each failure carries
machine-readable context. No free-text semantic branching. No global mega
error code. ExecutionPlanErrorCode deleted (single authority).

## C20. Module ownership/boundaries

ROADMAP21_OWNER_MODULE=media-execution-plan-module. Dependency direction:
render domain → execution planning (media-execution-plan-module) → (hard
boundary) → #22 runtime. No planner → runtime-infrastructure semantic
dependency. No planner → mutable persistence reads. No cross-module internal
exposure.

## C21. No mutable runtime reads

LOGICAL_EXECUTION_PLANNER_RUNTIME_MUTABLE_READ_COUNT=0.
LOGICAL_EXECUTION_PLANNER_RUNTIME_INFRA_DEP_COUNT=0. Missing inputs must be
resolved by the caller/context construction before planning.

## C22. No provider/runtime binding

PHYSICAL_PLANNER_PROVIDER_BINDING_COUNT=0. PHYSICAL_PLAN_WORKER_BINDING_COUNT=0.
PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0. EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0.
EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0.

## C23. Clean-forward migration/deletion policy (Phase 0)

media-execution-plan-module is the canonical home (MEDIA_EXECUTION_PLAN_MODULE_
DEFERRED_AS_WHOLE=NO). Phase 0 disposition per the 53-row type-disposition
ledger (zero external callers before delete, zero retired definitions at
closure): DELETE_SHADOW=23 (ExecutionInputRole, ExecutionOutputRole,
ExecutionCapabilityRequirement, MediaOperation hierarchy 16,
GpuRequirement, TimelineToExecutionPlanCompiler, MediaBackendCompiler,
ExecutionPlanErrorCode); DEFER_TO_22_PLUS=8 (ExecutionResourceRequirement,
CpuClass, MemoryClass, NetworkRequirement, TemporaryStorageClass,
ExecutionStepFailurePolicy, ExecutionProvider, ExecutionCacheKey);
MIGRATE_REDESIGN=13 (ExecutionInputBinding, ExecutionOutputDeclaration,
ExecutionStepKind, ExecutionDependency, ExecutionDependencyType,
ExecutionDeterminism, MediaExecutionPlan, MediaExecutionStep,
ExecutionPlanDigest, MediaExecutionPlanBuilder, MediaExecutionPlanValidator,
MediaExecutionGraphProjection, ExecutionPlanDomainException);
REUSE_AS_CANONICAL=7 (ExecutionPlanId, ExecutionPlanSchemaVersion,
ExecutionEdgeId, ExecutionInputId, ExecutionOutputId, ExecutionStepId,
ExecutionCreationContext provenance-only); REUSE_MECHANICS_ONLY=2
(ExecutionPlanCanonicalSerializer, ExecutionPlanDigestCalculator).
No compatibility wrappers. DUAL_AUTHORITY_ALLOWED=NO.

## C24. Implementation acceptance evidence

Guards (final target): SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0,
SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0,
DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0,
EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY_COUNT=0,
GENERIC_EXECUTION_DEPENDENCY_AUTHORITY_COUNT=0,
RENDER_DEPENDENCY_VARIANT_LOSS_COUNT=0,
EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY_COUNT=0,
ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0,
ROADMAP21_RUNTIME_FAILURE_POLICY_COUNT=0,
EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0,
EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0,
EXECUTION_INPUT_ROLE_SHADOW_AUTHORITY_COUNT=0,
EXECUTION_OUTPUT_ROLE_SHADOW_AUTHORITY_COUNT=0,
EXECUTION_IO_UPSTREAM_SEMANTIC_REDECLARATION_COUNT=0,
EXECUTION_INPUT_MUTABLE_AVAILABILITY_AUTHORITY_COUNT=0, plus the 13
invariants from Decision Recovery §16 (runtime reads 0, binding 0, invented
capability 0, free-text extent 0, string failure branch 0, mega error 0,
cross-module 0).
Behavioral tests: same-inputs determinism, fail-closed invalid/cycle,
extent pruning, parallel independence, temporal exactness, partition
preservation, capability propagation, runtime-state invariance, planFingerprint
consistency. RED mutations: 11 as designed + shadow-authority resurrection
mutations (ExecutionCapabilityRequirement, MediaOperation,
TimelineToExecutionPlanCompiler, ExecutionPlanErrorCode).

## C25. Deferred items

ExecutableTaskGraph, provider/worker/device binding, runtime scheduling,
probing, isolation, resource allocation, QoS, locality, cache/reuse runtime,
retries/leases/heartbeats, distributed execution → #22/#23.
Cost Optimizer / Semantic Rewrite / Constraint Kernel / Evidence / Formal
Methods runtimes → future cross-cutting layers (integration seams only).
Fusion / temporal chunking / N-to-M decomposition → DEFERRED within #21.

## C26. Unresolved decisions

UNRESOLVED_DECISION_COUNT=0 (module placement resolved: media-execution-plan-module).

---

CONTRACT_FREEZE_RECOMMENDATION=SUBMIT_TO_CHATGPT
ROADMAP_21_IMPLEMENTATION=NO_GO
