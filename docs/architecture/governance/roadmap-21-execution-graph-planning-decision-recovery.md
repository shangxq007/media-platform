# ROADMAP #21 — EXECUTION GRAPH PLANNING — DECISION RECOVERY

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
BASE_SHA=cf8c3abcf9fb2d0ad064246735714a4ac032ca81
BASE_TREE=1f706a336f01615d2c9e6e81a1cc05edd8e2ff42

---

## 1. ROADMAP #21 IDENTITY RECOVERY

### A. Repository historical evidence

- Integrated Architecture Roadmap V2 §14 milestone table:
  - #21 = `UNKNOWN (no repository name evidence; referenced only as NOT STARTED)`
    — recorded as UNKNOWN by UNKNOWN_IS_FIRST_CLASS_NOT_IMPLICIT_PASS_V1.
  - #22 = `WORKER_FABRIC / PHYSICAL_PLANNING` — repository evidence:
    "CapabilityImplementation profiles, candidate enumeration, ExecutionIsland,
    provider/worker/device locality, physical binding, cost/latency inputs".
  - #23 = `DISTRIBUTED_SCHEDULING` — "queue pressure, worker utilization,
    cross-worker placement, deadline/resource scheduling".
- Roadmap #20 records: `#21/#22 started: NO`.
- media-execution-plan-module (commit e561ef02 "feat(execution): add media
  execution plan v1") — 47 production types (MediaExecutionPlan immutable
  computation DAG, MediaExecutionStep, ExecutionDependency, ExecutionInputBinding,
  ExecutionOutputDeclaration, ExecutionPlanDigest, ExecutionResourceRequirement,
  GpuRequirement, CpuClass, MediaExecutionGraphProjection,
  TimelineToExecutionPlanCompiler, ExecutionPlanCanonicalSerializer,
  ExecutionPlanDigestCalculator, MediaExecutionPlanValidator) + 18 tests.
  Zero production consumers outside the module. ExecutionProvider is a sealed
  interface with a single Stub permit, javadoc: "FROZEN — do not implement.
  Execution providers are out of scope for V1."

### B. Previously adopted architecture authority (this Decision Recovery input)

Post-#20 split adopted by architecture authority:

- ROADMAP #21 = EXECUTION GRAPH PLANNING (provider-neutral execution-planning layer)
- ROADMAP #22 = EXECUTABLE TASK GRAPH + RUNTIME EXECUTION / WORKER FABRIC (runtime realization)

Conceptual chain: Verified Canonical Revision State → Logical RenderPlan (#20)
→ RenderGraph (#20) → ExecutionRequirement (#21) → LogicalExecutionGraph (#21)
→ PhysicalPlanner (#21) → PhysicalExecutionPlan (#21) → [hard boundary] →
ExecutableTaskGraph (#22) → provider/worker/device binding (#22) → runtime
scheduling (#22) → execution (#22).

### C. Naming / scope conflicts

1. Historical #22 wording "Physical planning: … physical binding, cost/latency
   inputs" spans BOTH new-#21 provider-neutral physical planning AND new-#22
   runtime binding. Conflict classification: SCOPE_OVERLAP — historical #22
   text pre-dates the #20/#21 split; its "physical binding" belongs to new-#22,
   its "candidate enumeration / ExecutionIsland" structural concepts belong to
   new-#21 (structural, provider-neutral).
2. media-execution-plan-module: an unshipped, fully-tested execution-plan DAG
   module with zero production wiring. Its MediaExecutionPlan/MediaExecutionStep/
   ExecutionDependency/ExecutionPlanDigest model is semantically the #21
   Logical/Physical execution plan — historical placeholder evidence, not a
   frozen contract. Classification: EXISTING_PLACEHOLDER (unwired, tested).
3. RenderExecutionRequirement already exists as a field on #20 RenderNode
   (GpuRequirement, RenderDeterminismClass, sandboxedIntent) — a #20-declared
   execution intent. Not the same authority as #21 ExecutionRequirement; the
   relationship must be frozen (see §5).

### D. Corrected canonical milestone boundary

- #21 EXECUTION_GRAPH_PLANNING: RenderGraph → ExecutionRequirement →
  LogicalExecutionGraph → PhysicalPlanner → PhysicalExecutionPlan.
  Provider-neutral, deterministic, typed. NO runtime binding.
- #22 EXECUTABLE_TASK_GRAPH_AND_RUNTIME_EXECUTION: ExecutableTaskGraph,
  provider/worker/device binding, runtime scheduling, probing, isolation,
  resource allocation, QoS, locality, cache/reuse runtime, retries/leases,
  distributed execution.
- #23 DISTRIBUTED_SCHEDULING (unchanged identity; queue pressure, utilization,
  cross-worker placement, deadline/resource scheduling).

### E. Repository canonicalization recommendation

Update Roadmap V2 §14 rows:
- #21: `EXECUTION_GRAPH_PLANNING` — NOT STARTED → (pending review) — source:
  this decision recovery.
- #22: refine canonical name to `EXECUTABLE_TASK_GRAPH_AND_RUNTIME_EXECUTION`
  (worker fabric retained as implementation-era evidence), scope = runtime
  realization only.
- #23 unchanged.
No renumbering. All 28 milestone numbers preserved.

---

## 2. REPOSITORY REALITY INVENTORY (mechanical, current main)

| Type | Classification |
|---|---|
| RenderPlan (render/domain/renderplan) | EXISTING_AND_AUTHORITATIVE (#20) |
| RenderGraph (render/domain/renderplan, validated DAG projection; RenderNode/RenderDependencyEdge/RenderGraphFingerprint) | EXISTING_AND_AUTHORITATIVE (#20) |
| RenderNode.capabilityRequirements (List<CapabilityRequirement>) | EXISTING_AND_AUTHORITATIVE (#20 declaration) |
| RenderNode.executionRequirements (RenderExecutionRequirement: GpuRequirement, RenderDeterminismClass, sandboxedIntent) | EXISTING_AND_AUTHORITATIVE (#20 declared execution intent) |
| RenderNode.materializationRequirements / outputRequirements / requiredSampleWindow | EXISTING_AND_AUTHORITATIVE (#20) |
| ExecutionPlannerService (render/app/planner) — PRE21 pure planner over FrozenPlanningContext → ExecutionPlan (render/domain/planner; planId/tenantId/projectId/targetProductId/targetProductType/status/stages) | EXISTING_AND_AUTHORITATIVE (PRE21 planner purity closure; product-job planning, upstream of #21 execution planning) |
| RenderExecutionPlan / LocalExecutionPlanFailureReason (render/domain/compile/executionplan) | EXISTING_AND_AUTHORITATIVE (local plan execution compile layer) |
| ProviderRenderPlan (render/infrastructure; selectedProviders, fallbackPlan, estimatedCost) | EXISTING_BUT_WRONG_LAYER (#22 provider binding concept currently in infrastructure) |
| ExecutionBackend / ExecutionBackendRegistry (outbox-event-module coordination) | EXISTING_AND_AUTHORITATIVE (task execution backend — #22 runtime) |
| TaskCapability / PlatformTask / TaskHandlerRegistry / PlatformTaskDispatcher | EXISTING_AND_AUTHORITATIVE (#22 task runtime) |
| media-execution-plan-module (47 types, 18 tests, 0 production consumers, FROZEN ExecutionProvider) | EXISTING_PLACEHOLDER (unwired historical execution-plan model) |
| ExecutionRequirement / LogicalExecutionGraph / PhysicalPlanner / PhysicalExecutionPlan | ABSENT (mechanically searched: zero definitions) |
| CapabilityRequirement (extension/domain; CapabilityId + ContractVersionRange + required + alternatives) | EXISTING_AND_AUTHORITATIVE |
| RenderExtent (render/domain/renderplan; MediaTime start/end + FrameRate) | EXISTING_AND_AUTHORITATIVE (typed, single authority) |
| Typed failure algebra (RenderResultFailureReason, module ErrorCode enums) | EXISTING_AND_AUTHORITATIVE |
| Mutable runtime reads in planning paths | 0 (PRE21 closure: LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT=0) |

---

## 3. ROADMAP #21 INPUT / OUTPUT BOUNDARY

INPUT AUTHORITY (authoritative inputs):

1. RenderGraph (validated #20 DAG) — authoritative graph input.
2. RenderPlan identity/fingerprint — provenance anchor.
3. Exact revision identity (base/ours/theirs + revision digest where applicable).
4. Requested RenderExtent — typed authority limiting graph construction.
5. CapabilityRequirement set declared on RenderNodes — propagated, never invented.
6. RenderExecutionRequirement (from RenderNode) — declared execution intent
   (determinism class, sandboxed intent, GPU intent) consumed as declared facts.
7. ExecutionRequirement (new #21 type) — deterministic execution intent for the
   requested operation (see §4).
8. Deterministic execution intent: product/job-level plan identity from
   ExecutionPlannerService output (PRE21) where the job is being planned.

DERIVED CONTEXT (not authoritative): provider availability, worker state,
queue depth, current inventory, dynamic probes, machine load.

OUTPUT AUTHORITY:

1. LogicalExecutionGraph — deterministic typed DAG of execution work +
   dependencies. Transient derived value (recomputable from inputs).
2. PhysicalExecutionPlan — deterministic typed structural plan (partition/fusion
   shaped), provider-neutral. Status: transient derived value for V1;
   reproducibility/provenance digest kept; persistence is a #22 decision
   (PERSISTENCE_MECHANICS_MUST_NOT_DEFINE_SEMANTICS).

---

## 4. EXECUTION REQUIREMENT DECISION

EXECUTION_REQUIREMENT_V1 (new #21 typed record):

- Owner: render-module / domain execution-planning package
  (com.example.platform.render.domain.executionplan or equivalent new package).
- Purpose: deterministic, typed execution intent that binds the #20 RenderGraph
  declarations to a concrete requested operation — NOT a capability declaration,
  NOT a runtime binding.
- Fields (candidate): executionRequirementId, operationId/target node identity,
  requestedRenderExtent (typed), determinismClass (from RenderNode or explicit),
  sandboxedIntent (from RenderNode), declaredCapabilityRequirements (propagated
  references — List<CapabilityId>), structuralHints (partition/fusion hints —
  OPTIONAL_V1, typed, non-runtime).
- Relationship to CapabilityRequirement: ExecutionRequirement CARRIES/REFERENCES
  declared CapabilityRequirements; it never creates them
  (P6: NO dual requirement authority).
- Relationship to RenderExtent: requested extent is a typed field; extent is the
  single authority (P7).
- Forbidden fields: providerId, workerId, gpuId, machineId, podId, live queue
  depth, current utilization, live probe result, availability registry keys.
- Canonical equality: structural record equality + deterministic canonical
  serialization digest.
- Validation: non-blank ids, typed extent valid, referenced capabilities exist
  in the graph declaration set, no runtime identifiers.
- Failure: invalid requirement → typed planning failure (INVALID_EXECUTION_REQUIREMENT).
- Versioning: schema version field; evolution via additive fields only.

---

## 5. LOGICAL EXECUTION GRAPH DECISION

LOGICAL_EXECUTION_GRAPH_V1:

- NODE MODEL: typed node kinds (EXECUTION_STEP with per-node: nodeId
  (deterministic from source RenderNode identity + operationKey), sourceRenderNodeRef,
  capabilityRefs, outputDecls, materializationIntent, requiredSampleWindow).
  One RenderGraph node MAY lower to N logical execution nodes (explicit
  decomposition rule; N=1 default).
- EDGE MODEL: typed dependency semantics — ExecutionDependencyType
  (DATA_DEPENDENCY, TEMPORAL_ORDER, BARRIER). Data edges carry input/output
  bindings; temporal edges carry exact MediaTime windows (rational time only,
  no float authority).
- GRAPH RULES: DAG required; cycle detection → typed failure (CYCLE_DETECTED);
  deterministic construction (sorted node/edge lists, canonical digest);
  validation invariants: all refs resolve, all bindings typed, no dangling
  capability refs.
- PARALLELISM: parallel regions expressed as independence (no edge between
  branches); this is STRUCTURAL — never runtime scheduling (P3/P10).
- TEMPORALITY: exact MediaTime/TemporalMapping semantics; temporal windows on
  edges; no float-time.
- DEMAND/EXTENT: requested RenderExtent prunes out-of-extent work at graph
  construction; omitted work must be provably outside requested extent
  (demand-driven pruning evidence in implementation phase).

---

## 6. PHYSICAL PLANNER DECISION

PHYSICAL_PLANNER_V1 (provider-neutral):

- LOGICAL = what work/dependency semantics exist.
- PHYSICAL (#21) = structural shaping of that work (partition/fusion into
  plan units) WITHOUT runtime binding.
- RUNTIME (#22) = who/where/when executes.

Legal V1 transformations (classification):

| Transformation | Class |
|---|---|
| Partition into plan units | REQUIRED_V1 |
| Fusion of adjacent units (same temporal window, same capability refs, no intervening barrier) | OPTIONAL_V1 (semantics-preserving proof required) |
| Stage formation (input staging boundaries) | OPTIONAL_V1 |
| Materialization boundary insertion | DEFERRED (#22 cache/materialization runtime) |
| Fan-out/fan-in shaping | OPTIONAL_V1 |
| Reusable deterministic sub-plan identification | OPTIONAL_V1 (digest-based, no runtime cache) |
| Temporal chunking | OPTIONAL_V1 (extent-driven) |
| Extent-based elimination | REQUIRED_V1 |

Every transformation must preserve semantic equivalence to the logical graph;
implementation phase proves equivalence via deterministic digest comparison of
expanded plan vs logical graph semantics.

---

## 7. PHYSICAL EXECUTION PLAN DECISION

PHYSICAL_EXECUTION_PLAN_V1:

- Identity: ExecutionPlanId (deterministic).
- Digest: canonical serialization SHA-256 (ExecutionPlanDigest).
- Version: ExecutionPlanSchemaVersion.
- Units: List<PhysicalPlanUnit> (partitioned/fused units) with typed inputs,
  outputs, dependencies, temporal windows, propagated CapabilityRequirement
  refs + ExecutionRequirement refs + RenderExtent.
- Artifact boundaries: declared input/output artifact refs (typed), no storage
  URIs as semantic authority.
- Determinism/cacheability metadata: unit digests + semantic cacheability flag
  (DECLARATIVE ONLY — no runtime cache lookup).
- Validation: DAG, typed bindings, extent propagation complete.
- Persistence: transient for V1; digest record only.
- PROVABLE ABSENCE: no provider binding, no worker/device binding, no runtime
  queue binding, no live availability binding (guards in implementation phase).

---

## 8. DETERMINISM / CACHE BOUNDARY

PLANNER_DETERMINISM_CONTRACT:

- Same frozen input state + same ExecutionRequirement → same LogicalExecutionGraph
  AND same PhysicalExecutionPlan semantics (digest-equal).
- Forbidden non-deterministic inputs: runtime availability, worker state, queue
  pressure, provider health, current GPU inventory, dynamic probes, machine
  load, Kubernetes state, current time (as semantic input), random values.
- #21 defines deterministic identities/digests and semantic cacheability flags.
- #22+ owns runtime cache lookup/reuse policy.

---

## 9. INTERACTIVE / QOS / RESOURCE / LOCALITY BOUNDARY

- #21 carries forward ONLY typed declarative non-runtime constraints:
  determinismClass, sandboxedIntent, structural hints. These are deterministic
  intents, never live scheduling policy.
- Interactive latency/headroom principles remain product principles owned by
  #22+ runtime policy; #21 must not convert "interactive" into hidden planner
  heuristics.
- resource/QoS/locality (GpuRequirement/CpuClass/etc. from media-execution-plan
  or declared intents): typed DECLARATIVE facts only at #21; actual allocation
  is #22.

---

## 10. CAPABILITY BOUNDARY

- CapabilityRequirement authority remains extension-module (CapabilityId +
  ContractVersionRange). #20 RenderNode declares requirements. #21 planner
  propagates them (graph → logical → physical). 
  PLANNER_INVENTED_CAPABILITY_REQUIREMENT_COUNT=0 (guard in implementation).
- No productType→capability, providerType→capability, or implementation-class→
  capability switches as execution-semantic authority.
- Planner must not query PluginRegistryPort as raw capability authority.

---

## 11. DOMAIN / MODULE AUTHORITY

Proposed ownership (implementation phase freezes exact packages):

- ExecutionRequirement / LogicalExecutionGraph / PhysicalExecutionPlan /
  PhysicalPlanner / planning failures / validation →
  render-module (or dedicated execution-planning package inside render-module;
  decision: single-module placement keeps #20/#21 adjacency and avoids new
  module wiring; revisit if module grows).
- Dependency direction: render domain (RenderPlan/RenderGraph) → execution
  planning → (hard boundary) → outbox/task runtime (#22) + future runtime
  modules. No reverse dependency. No planner → runtime-infrastructure semantic
  dependency. No planner → mutable persistence reads.
- media-execution-plan-module disposition: DEFER (kept as historical evidence;
  #21 V1 does NOT wire it; a later consolidation decision may MIGRATE its
  tested model into #21 or retire it — requires separate review; CLEAN FORWARD
  does not demand deletion of an unshipped module that is not a dual authority
  because it has zero production wiring).

---

## 12. FAILURE ALGEBRA

TYPED_EXECUTION_PLANNING_FAILURE_V1 — module-local enum (render execution
planning package), V1 bounded classes:

| Code | Stage | Retryable | Caller correction |
|---|---|---|---|
| INVALID_EXECUTION_REQUIREMENT | input | NO | YES |
| INVALID_LOGICAL_GRAPH | build | NO | YES |
| CYCLE_DETECTED | validate | NO | YES |
| MISSING_SEMANTIC_INPUT | input | NO | YES |
| INCONSISTENT_RENDER_EXTENT | input | NO | YES |
| ILLEGAL_PARTITION | physical | NO | YES |
| ILLEGAL_FUSION | physical | NO | YES |
| UNSATISFIED_STRUCTURAL_CONSTRAINT | physical | NO | YES |
| UNSUPPORTED_V1_PLANNING_CONSTRUCT | build | NO | YES |
| DETERMINISM_INVARIANT_VIOLATION | any | NO | YES |

Each carries machine-readable context (typed record fields). No free-text
semantic branching. No global mega error code.

---

## 13. COST / FORMAL LAYER BOUNDARY

- Cost Optimizer / Semantic Rewrite / Constraint Kernel runtime / Evidence
  runtime / Formal Methods runtime remain future cross-cutting layers.
- #21 V1 may use ONLY bounded deterministic structural heuristics (partition/
  fusion rules as frozen in §6); they are NOT a general Cost Optimizer
  (heuristics are enumerated, typed, semantics-preserving, digest-stable).

---

## 14. ROADMAP #21 → #22 HANDOFF CONTRACT

ROADMAP_21_TO_22_HANDOFF_CONTRACT_V1:

#21 delivers: PhysicalExecutionPlan + typed requirements (CapabilityRequirement
refs, ExecutionRequirement, RenderExtent) + deterministic structural
dependencies + semantic/provenance identities + plan digests.

#22 may add: ExecutableTaskGraph, provider implementation binding, worker
binding, device binding, runtime resource allocation, locality placement, QoS
scheduling, queue/deadline policy, runtime cache/reuse, probing, isolation,
retry/lease/heartbeat semantics, distributed execution.

#22 must NOT: reinterpret canonical media semantics; change #21 plan semantics
because a different provider is selected; bypass typed extent/requirement
authorities.

Governance correction required: Roadmap V2 §14 #22 wording refined from
"WORKER_FABRIC / PHYSICAL_PLANNING (… physical binding …)" to
"EXECUTABLE_TASK_GRAPH_AND_RUNTIME_EXECUTION (worker fabric era evidence
preserved; runtime realization)" — #21 owns provider-neutral physical planning.

---

## 15. CLEAN FORWARD / MIGRATION DECISION

| Surface | Disposition |
|---|---|
| media-execution-plan-module | DEFER (zero production wiring; tested historical model; consolidation decision requires separate review — not a dual authority while unwired) |
| ProviderRenderPlan (infrastructure selectedProviders) | RETAIN (existing #22-era infrastructure surface; #21 does not touch it; #22 refinement may relocate) |
| PRE21 ExecutionPlannerService / ExecutionPlan | KEEP (authoritative product-job planning, upstream input to #21) |
| RenderExecutionPlan compile layer | KEEP (#22-adjacent local execution; no conflict) |
| String plan encodings / legacy execution planners | NONE FOUND (mechanically searched) |

COMPATIBILITY_REQUIRED_COUNT=0. DUAL_AUTHORITY_ALLOWED=NO.

---

## 16. IMPLEMENTATION EVIDENCE DESIGN (future, not executed)

Guards (implementation phase):

- LOGICAL_EXECUTION_PLANNER_RUNTIME_MUTABLE_READ_COUNT=0
- LOGICAL_EXECUTION_PLANNER_RUNTIME_INFRA_DEP_COUNT=0
- PHYSICAL_PLANNER_PROVIDER_BINDING_COUNT=0
- PHYSICAL_PLAN_WORKER_BINDING_COUNT=0
- PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0
- PLANNER_INVENTED_CAPABILITY_REQUIREMENT_COUNT=0
- DUAL_EXECUTION_REQUIREMENT_AUTHORITY_COUNT=0
- FREE_TEXT_RENDER_EXTENT_AUTHORITY_COUNT=0
- STRING_FAILURE_SEMANTIC_BRANCH_COUNT=0
- GLOBAL_EXECUTION_MEGA_ERROR_CODE_COUNT=0
- CRITICAL_CROSS_MODULE_INTERNAL_ACCESS_COUNT=0
- EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0
- EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0

Behavioral evidence: same-inputs determinism (logical + physical), invalid
graph fail-closed, cycle fail-closed, extent-limited pruning, parallel branch
independence, temporal dependency exactness, legal fusion preserved, illegal
fusion rejected, legal partition preserved, capability propagation (not
invention), provider/runtime state change does not alter planning semantics.

RED mutations (future): add mutable runtime read; invent requirement from
product/provider type; bind provider/worker/device in #21 types; duplicate
execution authority; replace typed failure with message branch; bypass extent;
make plan depend on live worker state; resurrect legacy plan wrapper; allow
cycle; allow semantic-changing fusion/partition. Each: detector fails,
restore verified.

---

## 17. DECISIONS SUMMARY

| Decision | Selected |
|---|---|
| D-01 #21 identity | EXECUTION_GRAPH_PLANNING (adopted) |
| D-02 #22 identity refinement | EXECUTABLE_TASK_GRAPH_AND_RUNTIME_EXECUTION (proposed governance correction) |
| D-03 ExecutionRequirement owner | render-module execution-planning package |
| D-04 ExecutionRequirement vs CapabilityRequirement | ExecutionRequirement references/carries declared CapabilityRequirements; never invents |
| D-05 LogicalExecutionGraph | deterministic typed DAG; N-to-1 RenderNode lowering; typed edges (DATA/TEMPORAL/BARRIER); cycle → typed failure |
| D-06 PhysicalPlanner scope | provider-neutral structural shaping; partition REQUIRED_V1, fusion OPTIONAL_V1; no runtime binding |
| D-07 PhysicalExecutionPlan status | transient derived value + digest; persistence deferred to #22 |
| D-08 Determinism contract | frozen inputs → digest-equal plans; forbidden non-deterministic inputs enumerated |
| D-09 media-execution-plan-module | DEFER (unwired; separate consolidation review) |
| D-10 Failure algebra | module-local typed enum, 10 V1 classes, no mega enum |
| D-11 Module ownership | render-module execution-planning package; no new module for V1 |
| D-12 #22 handoff | PhysicalExecutionPlan + typed requirements + digests; #22 adds runtime binding only |

UNRESOLVED_DECISION_COUNT=1: whether #21 types live in render-module or a new
execution-planning module (recommended render-module for V1; module split is a
#22-era decision).

---

## 18. SCOPE CONTROL

PRODUCTION_CODE_CHANGED=NO
IMPLEMENTATION_STARTED=NO
SCHEMA_CHANGED=NO
PROVIDER_CODE_CHANGED=NO
RUNTIME_CODE_CHANGED=NO
SCHEDULER_CODE_CHANGED=NO
CLOSED_PRE21_BOUNDARY_REOPENED=NO

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21_DECISION_RECOVERY=READY_FOR_CHATGPT_REVIEW
ROADMAP_21_IMPLEMENTATION=NO_GO
