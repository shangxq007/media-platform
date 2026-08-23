# Roadmap #22 Executable Task Graph and Worker Fabric Runtime — Decision Recovery

STATUS=FROZEN_BOUNDED_ARCHITECTURE_CONTRACT_DRAFT
READY_FOR_CHATGPT_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO

## 1. Authority Baseline

| Field | Value |
|---|---|
| Repository | shangxq007/media-platform |
| CANONICAL_MAIN_SHA | 036f21f7f94f61da92faa2e91934675d024d99e8 |
| CANONICAL_MAIN_TREE | 7a61effeb2840c428cab2705a9f529159fc4e345 |
| ROADMAP_21_FINAL_PUBLICATION | d2422e534ded961255d4e8e69d2325c0f8ccafe0 |
| ROADMAP_21_CLOSURE_SHA | 036f21f7f94f61da92faa2e91934675d024d99e8 |
| ROADMAP_21 | CLOSED |
| ROADMAP_22 branch | agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery |
| ROADMAP_22 worktree | .worktrees/roadmap22-decision-recovery |
| ROADMAP_22 base | 036f21f7f94f61da92faa2e91934675d024d99e8 (exact closure) |

## 2. Decision Recovery Mode

MODE=ARCHITECTURE_DECISION_RECOVERY
PRIMARY_OUTPUT=FROZEN_BOUNDED_ARCHITECTURE_CONTRACT
SECONDARY_OUTPUT=REPOSITORY_REALITY_AND_CLEAN_FORWARD_LEDGER
PRODUCTION_CODE_CHANGES=0
TEST_CODE_CHANGES=0
BUILD_CODE_CHANGES=0

## 3. Inherited Architecture (NOT reopened)

Roadmap #20 owns WHAT_MUST_BE_RENDERED (RenderPlan/RenderGraph/RenderNode/RenderDependency/RenderExtent/RenderExecutionCoverage/RenderSampleWindow/RenderOutputRequirement/RenderMaterializationRequirement/RenderExecutionRequirement/CapabilityRequirement).

Roadmap #21 owns PROVIDER_NEUTRAL_STRUCTURAL_HOW (ExecutionRequirement/LogicalExecutionGraph/LogicalExecutionGraphDigest/PhysicalExecutionPlan/PhysicalExecutionPlanDigest). CLOSED.

Roadmap #22 owns EXECUTABLE_PROVIDER_AND_LOCAL_RUNTIME_HOW.

Roadmap #23 owns DISTRIBUTED_AND_GLOBAL_PLACEMENT_OPTIMIZATION.

### 3.1 Provider composition principles (adopted)

- PROVIDER_COMPOSITION_IS_CONSTRAINT_SOLVING_NOT_UNIVERSAL_INTEROPERABILITY_V1
- PARTIAL_PROVIDER_COMPOSABILITY_IS_NORMAL_V1
- CROSS_PROVIDER_OPTIMIZATION_OPERATES_ONLY_OVER_FEASIBLE_COMPATIBILITY_GRAPH_V1
- PROVIDER_SELECTION_FAILS_CLOSED_ON_INCOMPATIBILITY_V1
- OPTIMIZATION_NEVER_CREATES_SEMANTIC_COMPATIBILITY_V1
- Order: REQUIREMENTS → PROVIDER CANDIDATES → SEMANTIC/TECHNICAL COMPATIBILITY → FEASIBLE PROVIDER GRAPH → RUNTIME ELIGIBILITY → BOUNDED LOCAL SELECTION → EXECUTABLE BINDING
- CAN_RUN before WHICH_IS_BEST

### 3.2 Cross-provider boundary

- EXPLICIT, IMMUTABLE, TYPED, ARTIFACT MATERIALIZATION at cross-provider boundaries
- CROSS_PROVIDER_FUSION_DEFAULT=NO

### 3.3 Clean Forward

- CLEAN_FORWARD=AUTHORITATIVE
- No unshipped legacy provider APIs, wrappers, aliases, fallback authorities, V1/V2 dual models, old runtime shadows
- Replace callers first, prove zero use, then delete obsolete authority

## 4. Repository Reality Summary

Modules with #22-relevant surfaces: render-module, remote-render-worker, sandbox-worker, scheduler-module, outbox-event-module, extension-module, media-execution-plan-module, ai-module, storage-module, artifact-module, delivery-module, cloud-resource-module.

Key existing surfaces (details in source disposition ledger):

1. **RenderProvider** (render-module/infrastructure) — legacy provider god-interface: `render(jobId, aiScript, profile)`, `getSupportedProfiles()`, `supports(String capability)`, `RenderProviderCapability.legacy(...)` stringly-typed capability. Implementations: GStreamerRenderProvider, plus deprecated javacv providers (GPAC/JavaCV/OFX) under docs/deprecated/javacv.
2. **ExecutionProvider** (media-execution-plan-module/execution/domain/provider) — FROZEN sealed Stub interface explicitly "out of scope for V1"; ExecutionManifest(backendType, manifestData) stringly-typed; ProviderCapabilities(providerType, supportsGpu) stringly-typed; ExecutionAttempt with AttemptStatus.
3. **Render farm package** (render-module/infrastructure/farm) — RenderWorkerRegistration/Record/Status, RenderWorkerHeartbeat, RenderJobLeaseRecord/Repository/Service/Status, StaleRenderJobLeaseCompensationService, RenderFarmClaimResult. Real existing worker/heartbeat/lease surfaces (queue-based, legacy render jobs).
4. **RenderWorkerQueueService / LayerWorkerQueueProcessor / RenderNatronQueueProcessor** (render-module/app) — queue-based render job processing.
5. **Extension module sandbox runtime** (extension-module/runtime/sandbox) — SandboxRuntimeService, SandboxWorkerPort, HttpSandboxWorkerAdapter, NoopSandboxWorkerAdapter, SandboxWorkerRequest/Result, plus SandboxExecutionService + ProcessExecutor/ProcessToolRunner + TrustPolicyEnforcer.
6. **SandboxWorker** standalone module (sandbox-worker) — SandboxWorkerApplication, SandboxExecutionService, controller/config.
7. **Outbox PlatformTask coordination** (outbox-event-module/coordination) — PlatformTask(id, jobId, taskType, capability, provider, status, attemptCount, maxAttempts, ...), PlatformTaskDispatcher, PlatformTaskRepository; TaskStatus incl LEASED; canRetry/isLeasable/markLeased — existing task/attempt/lease/retry primitives.
8. **scheduler-module** — ScheduledJobDefinition/ScheduledJobRun/JobStatus, ScheduleRegistryService (time-based job scheduling, not #22 task scheduling).
9. **EffectCapabilityProfile / TransitionCapabilityProfile** (render-module/domain/visual) — bounded visual capability definitions (SCALE/CROP/... with category/status/consistency/fallback/safety) — typed capability model already exists for visual effects.
10. **Ai-module provider routing** (ai-module) — ChatProvider, ConfigurableModelRouter, RouteTarget, AiRoutingProperties — AI-provider routing (separate concern; may reuse routing patterns but not authority).
11. **Artifact module** — Artifact authority (immutable media data plane).
12. **Storage module / storage-provider-opendal** — filesystem/R2/RustFS-capable storage backends.

## 5. Authority Model (frozen)

### 5.1 PhysicalExecutionPlan
Remains provider-neutral / worker-neutral / device-neutral. Must NOT gain providerId, workerId, deviceId, queue/probe state, availability, lease, heartbeat, runtime retries. #22 consumes it; does not redefine it.

### 5.2 Capability / Provider identity authority separation (R22-DR-C1, frozen)

**CapabilityId** — AUTHORITY: Roadmap #16 CapabilityRegistry. Meaning: semantic
capability contract identity (e.g. media.decode.*, video.encode.*, render.*).
Stable, typed, namespaced, implementation-neutral, provider-neutral,
plan-neutral. CapabilityRequirement expresses WHAT capability is required.
#22 does NOT redefine this.

**CapabilityImplementationId** — AUTHORITY: Roadmap #16 CapabilityRegistry.
Meaning: one concrete realization of ONE CapabilityId. Existing authority
unchanged. Provider/Worker/Device do not enter this identity.

**ProviderId** — AUTHORITY: Roadmap #22 provider runtime domain. Meaning:
stable provider/backend family identity (FFmpeg, Blender, NVIDIA_NIM, future
scene provider). ProviderId has NO semantic capability authority. It MUST NOT
replace CapabilityId, MUST NOT mint semantic capability namespaces, MUST NOT
become CapabilityRegistry authority.

**ProviderImplementationId** — AUTHORITY: Roadmap #22 executable provider
runtime domain. Meaning: stable identity of one provider runtime/adapter
implementation. It MUST NOT represent a Worker, a Device, a runtime
installation instance, a CapabilityId, or a CapabilityImplementationId. A
ProviderImplementation may expose/realize MANY existing capability
implementations.

**ProviderVersion** — implementation version.

**ProviderDescriptor** — declared static provider metadata (ProviderId,
ProviderImplementationId, ProviderVersion, ProviderExecutionContractVersion,
declared ProviderCapabilityProfile reference).

**ProviderExecutionContract** — the provider execution/SPI contract (NOT a
semantic Capability contract): provider SPI compatibility, lowering contract,
runtime adapter contract, provider implementation schema/version. It MUST
reference upstream Capability contracts rather than redefine them.

**ProviderCapabilityProfile** — typed declared executable support (NOT
Map<String,String>). Freeze
PROVIDER_CAPABILITY_PROFILE_IS_EXECUTION_SUPPORT_PROJECTION_NOT_CAPABILITY_AUTHORITY_V1.
Each support declaration references existing capability authority:
CapabilityId + compatible ContractVersion/ContractVersionRange, and where
repository architecture requires concrete implementation identity,
CapabilityImplementationId. ProviderCapabilityProfile is an
EXECUTION_FEASIBILITY_PROJECTION — NOT a CapabilityRegistry, NOT capability
definition authority, NOT capability contract lifecycle authority. It does NOT
copy or recreate capability lifecycle authority.

Typed relationship (frozen shape):

```
ProviderImplementation {
  ProviderImplementationId
  ProviderId
  ProviderVersion
  ProviderExecutionContractVersion
  ProviderCapabilityProfile   // typed support declarations -> CapabilityId(s)
                              // + ContractVersion/Range + optional CapabilityImplementationId
}
```

### 5.3 ProviderProbePort
- DECLARED_PROVIDER_CAPABILITY vs OBSERVED_RUNTIME_AVAILABILITY separation.
- Probe results: mutable, time-sensitive, ephemeral. Never enter Timeline content hash / RenderPlan fingerprint / LogicalExecutionGraph digest / PhysicalExecutionPlan digest.

## 6. Two-Stage Feasibility (frozen, R22-DR-C3)

- Stage 1 **CompatibilityKernel** (a.k.a. ProviderCompatibilityKernel) — PURE
  deterministic static stage. Inputs MUST be frozen/immutable:
  PhysicalPlanUnit, ExecutionRequirement, ProviderDescriptor,
  ProviderExecutionContract, ProviderCapabilityProfile, immutable Artifact
  requirements, typed cross-provider boundary requirements. It MUST NOT read:
  ProviderProbeResult, WorkerRuntimeState, DeviceRuntimeState, queue depth,
  utilization, heartbeat, lease, current free memory, current device
  availability, current runtime health, wall clock. Output:
  **CompatibilityDecision** — algebra COMPATIBLE | INCOMPATIBLE |
  UNKNOWN_FAIL_CLOSED with typed static explanations (CAPABILITY_UNSUPPORTED,
  CAPABILITY_CONTRACT_VERSION_UNSUPPORTED, INPUT_ARTIFACT_INCOMPATIBLE,
  OUTPUT_ARTIFACT_INCOMPATIBLE, CODEC_UNSUPPORTED, DEVICE_KIND_UNSUPPORTED,
  PROVIDER_RUNTIME_CLASS_UNSUPPORTED, PROVIDER_CONTRACT_INCOMPATIBLE,
  SANDBOX_MODE_UNSUPPORTED, DETERMINISM_UNSUPPORTED,
  CROSS_PROVIDER_BOUNDARY_INCOMPATIBLE, LOWERING_SEMANTICALLY_UNREPRESENTABLE).
  No runtime-state reasons here.
- Stage 2 **RuntimeEligibilityEvaluator** — separate stage. Inputs:
  compatible ProviderImplementation candidate, WorkerDescriptor,
  DeviceDescriptor, ProviderProbeResult, WorkerRuntimeState,
  DeviceRuntimeState, sandbox runtime state, availability snapshot. Output:
  **RuntimeEligibilityDecision** — algebra ELIGIBLE | INELIGIBLE |
  UNKNOWN_FAIL_CLOSED with runtime explanations (PROBE_UNKNOWN, PROBE_STALE,
  PROBE_FAILED, NO_ELIGIBLE_WORKER, NO_ELIGIBLE_DEVICE, WORKER_UNAVAILABLE,
  DEVICE_UNAVAILABLE, RUNTIME_UNAVAILABLE, SANDBOX_RUNTIME_UNAVAILABLE,
  INSUFFICIENT_CURRENT_RESOURCE). These are mutable runtime facts.

### Graph separation (frozen)

- **ProviderCompatibilityGraph** = Stage-1 deterministic graph ONLY. It MUST
  NOT contain probe state, worker health, device availability, queue state,
  heartbeat, lease, current utilization. Immutable deterministic derivation.
- **RuntimeEligibleCandidateView** (ephemeral) = ProviderCompatibilityGraph +
  runtime eligibility evidence. Mutable runtime view; NOT a new semantic
  authority; no semantic digest required for the mutable eligibility view.

## 7. Selection Pipeline (frozen exact order)

```
PhysicalExecutionPlan
        ↓
Provider candidate discovery
        ↓
STATIC CompatibilityKernel
        ↓
ProviderCompatibilityGraph
        ↓
RuntimeEligibilityEvaluator
        ↓
RuntimeEligibleCandidateView
        ↓
bounded local Provider selection
        ↓
ProviderBoundExecutableTaskGraph
        ↓
Worker/Device dispatch
        ↓
ExecutionAttempt
```
CAN_RUN before WHICH_IS_BEST. Optimization never creates compatibility.

## 8. Constraint Kernel (frozen)

Purpose: answer feasibility, not global optimization. The Constraint Kernel is
the bounded static feasibility engine embodied by CompatibilityKernel (Stage
1) + ProviderCompatibilityGraph. It does not read mutable runtime state, and
its results are deterministic over frozen inputs.

## 9. Provider Compatibility Graph (frozen, R22-DR-C3)

Bounded typed model: PhysicalPlanUnit → feasible ProviderImplementation
candidate set + feasible transitions across provider boundaries. Stage-1
deterministic graph ONLY — no probe state, worker health, device availability,
queue state, heartbeat, lease, current utilization.

Edge transition algebra (frozen):
- Default for cross-provider transitions: **ARTIFACT_MATERIALIZATION_REQUIRED**
- DIRECT_COMPATIBLE allowed ONLY when: same provider/runtime boundary where no
  provider crossing occurs, OR an explicit typed interoperability/transport
  contract proves compatibility. No inferred shared memory. No vendor-specific
  implicit shortcut.
- INCOMPATIBLE — no optimization across this edge.
- UNKNOWN — fail closed.

## 10. ExecutableTaskGraph V1 (frozen, R22-DR-C4)

EXECUTABLE_TASK_GRAPH_V1=PROVIDER_BOUND, WORKER_UNBOUND, DEVICE_UNBOUND.

- ONE PhysicalPlanUnit → ONE PRIMARY executable work task in V1
  (LAW_R22_009). No semantic fusion, no arbitrary N:M decomposition.
- Provider selection occurs BEFORE ETG V1 construction/freeze.
- Specific WorkerId and DeviceId are NOT part of ETG semantic content;
  worker/device placement is runtime dispatch/attempt state
  (ExecutionAssignment, §14).
- Infrastructure boundary representation: **B — typed boundary actions
  attached to primary tasks** (single authority, no parallel task-kind
  authority).
- ExecutableTaskId / ExecutableTaskGraphId / ExecutableTaskGraphDigest:
  business identity != semantic digest; runtime attempt identity != task
  identity; retry creates new attempt, never new task.

### ExecutableTaskGraphDigest — frozen inclusion/exclusion (NOT conditional)

INCLUDE at minimum:
- graph format/schema version
- exact PhysicalPlanUnit identity/reference
- task dependency topology
- ExecutableTaskId semantics
- selected ProviderId
- selected ProviderImplementationId
- immutable ProviderExecutionContract version/pin
- immutable ProviderCapabilityProfile version/digest or equivalent frozen pin
- typed boundary actions
- immutable required input Artifact references/pins
- provider binding semantics necessary to reproduce lowering

EXCLUDE:
- ExecutableTaskGraphId (if business id separate)
- WorkerId
- DeviceId
- ExecutionAttemptId
- ProviderProbeResult
- WorkerRuntimeState
- DeviceRuntimeState
- queue state
- current locality/cache state
- utilization, free memory
- heartbeat, lease, retry count
- timestamps, metrics, logs
- correlation, trace
- observability/provenance-only fields

Frozen laws:
WORKER_ASSIGNMENT_DOES_NOT_CHANGE_EXECUTABLE_TASK_GRAPH_DIGEST_V1
DEVICE_ASSIGNMENT_DOES_NOT_CHANGE_EXECUTABLE_TASK_GRAPH_DIGEST_V1

### Provider binding pinning (frozen)

ProviderImplementationId alone is not sufficient if mutable metadata could
change. Freeze an immutable binding pin sufficient for reproducibility:
ProviderImplementationId + ProviderVersion + ProviderExecutionContractVersion
+ ProviderCapabilityProfileVersionOrDigest. Mutable probe results do NOT
participate.

## 11. Provider Binding / PlanLowerer / RuntimeAdapter (frozen)

- CapabilityRequirement ≠ ProviderImplementation. PhysicalPlanUnit says WHAT;
  executable binding says WHICH. No provider name in canonical #20/#21
  semantics.
- PlanLowerer: typed PhysicalPlanUnit/ExecutableTask → provider-specific
  executable specification. MUST NOT redefine semantic meaning; fails closed if
  it cannot represent requested semantics. FFmpeg/Blender/CUDA command lines
  never canonical semantic authority (LAW_R22_010).
- RuntimeAdapter: HOW a request is launched/monitored/cancelled (separate from
  PlanLowerer's WHAT). Same provider semantics + different runtime
  environments (FFmpeg local / container / remote worker) without redefining
  capability semantics.
- ProviderExecutionContract covers provider SPI compatibility, lowering
  contract, runtime adapter contract, provider implementation schema/version —
  and references upstream Capability contracts rather than redefining them.

## 12. ExecutionAssignment (frozen, R22-DR-C4)

Separate runtime concept:
```
ExecutionAssignment {
  ExecutableTaskId
  ProviderImplementationId
  WorkerId
  optional DeviceId(s)
}
```
Mutable/runtime-scoped. It does NOT modify ETG digest. ExecutionAttempt
references the assignment. WorkerId/DeviceId are NOT added back into
PhysicalExecutionPlan.

## 13. Worker / Device (frozen)

- WorkerDescriptor (stable/static: WorkerId, runtime environment, supported isolation modes, declared device inventory refs, execution backend class) separate from WorkerRuntimeState (AVAILABLE/BUSY/DEGRADED/UNAVAILABLE/DRAINING — mutable, not in digests).
- DeviceDescriptor (stable: DeviceId, DeviceKind CPU|GPU|MEDIA_ACCELERATOR|OTHER_ACCELERATOR, vendor, model, driver compatibility, memory class, media engines, compute features) separate from DeviceRuntimeState (free memory/utilization/temperature — mutable).
- No NVIDIA-only assumptions.

## 12. Artifact / Cross-Provider Data Plane (frozen)

- Artifact remains immutable media data-plane authority. Provider runtime consumes/produces ArtifactId + typed metadata + immutable content pin/digest.
- No provider-private filesystem path / mutable URL / temp local path as canonical artifact identity.
- CROSS_PROVIDER_ARTIFACT_MATERIALIZATION_BOUNDARY_V1: provider X → immutable output Artifact → storage/data plane → provider Y input. No hidden shared-memory assumption, no implicit provider-private object passing. Direct provider-to-provider shortcut only as explicit later optimization with proven compatibility; never required for correctness.
- Locality (LOCAL/REMOTE/CACHED/NEEDS_MATERIALIZATION) is runtime info, not canonical identity; global locality optimization is #23.
- RUSTFS_IS_SELF_HOSTED_S3_BACKEND_NOT_ARTIFACT_AUTHORITY_V1; RUSTFS_AND_R2_MUST_PASS_COMMON_ARTIFACT_STORAGE_CONFORMANCE_V1; S3_VENDOR_SPECIFIC_BEHAVIOR_MUST_NOT_DEFINE_ARTIFACT_SEMANTICS_V1.

## 13. Sandbox / Isolation (frozen)

- Runtime isolation semantics frozen; implementation environments (native process, OCI/rootless container, LXC, VM, remote sandbox) are implementations, not domain authority. Podman/Docker/LXC/KVM/Kubernetes are NOT domain authorities.
- Canonical execution requirements may require isolation characteristics; provider runtime must prove satisfaction.
- TRUST_PERMISSION_SANDBOX_V1: authorization/trust checks occur before runtime execution; ProviderAdapter.execute() must NOT bypass application/OperationPlan authorization where applicable.

## 14. Task Execution Lifecycle / Attempt / Retry (frozen)

- Lifecycle (runtime, mutable, not canonical media revision semantics): CREATED, READY, DISPATCHED, RUNNING, SUCCEEDED, FAILED, CANCELLED (bounded; exact names may adapt to repo reality).
- ExecutableTask ≠ ExecutionAttempt. Retries create new attempts (ExecutionAttemptId), never new semantic tasks (LAW_R22_006).
- Retry typed and bounded: RETRYABLE_RUNTIME_FAILURE vs NON_RETRYABLE_RUNTIME_FAILURE vs SEMANTIC_INCOMPATIBILITY. Never retry semantic incompatibility. Retry policy NOT in PhysicalExecutionPlan.

## 15. BoundaryAction V1 (frozen, R22-DR-C5)

Keep Decision B: typed boundary actions attached to primary tasks.
BOUNDARY_ACTION_IS_NOT_SCHEDULABLE_TASK_V1.

BoundaryAction MUST have:
- typed action kind (examples: FETCH_ARTIFACT, STAGE_ARTIFACT,
  VERIFY_ARTIFACT, PREPARE_SANDBOX, PREPARE_RUNTIME)
- owning ExecutableTaskId
- phase = PRE_EXECUTION | POST_EXECUTION
- typed dependency/artifact reference where applicable
- deterministic ordering semantics

BoundaryAction has NO independent task lifecycle, scheduler identity, lease,
heartbeat, or ExecutionAttempt. It executes within the owning task attempt.
Its immutable semantics participate in ETG digest.

### Cross-provider materialization ownership (frozen)

- Producer task success means: all declared immutable output Artifacts have
  been successfully committed to Artifact authority.
- Consumer cross-provider acquisition/staging is a PRE_EXECUTION
  BoundaryAction.
- Do NOT create an independently schedulable transfer task in V1.
- Failure is typed. Artifact materialization/staging failure must not silently
  mutate canonical Artifact identity.

## 16. Lease / Heartbeat / Cancellation (frozen)

- Lease, heartbeat, lease expiry, worker loss are runtime mechanics; never canonical media state / RenderPlan state / PhysicalExecutionPlan semantics. #22 establishes the local/bounded primitive contract; distributed fleet coordination is #23.
- Cancellation distinguishes cancel task attempt vs delete canonical media result. Cancellation never rewrites historical media state; explicit semantics when a completed immutable Artifact exists.

## 17. Runtime Probe Cache (frozen)

- CACHE != AUTHORITY. ProviderProbePort remains evidence source. Cache entries require timestamp/freshness, provider implementation identity, worker/device identity, probe schema/version. Stale probe fails closed or re-probes per contract. Redis may be implementation later; REDIS_IS_NOT_REQUIRED_ARCHITECTURE_V1.

## 17. Provider SPI (frozen)

No god interface. Separated responsibilities:
- ProviderContract (authority contract)
- ProviderDescriptor (static declared metadata)
- CapabilityProfile (typed declared capability)
- ProviderProbePort (runtime evidence)
- PlanLowerer (WHAT request)
- RuntimeAdapter (HOW launch/monitor/cancel)
Design test cases: FFmpeg software, FFmpeg Intel QSV/VAAPI, FFmpeg NVIDIA NVENC/NVDEC, Blender CPU, Blender CUDA/OptiX, NVIDIA NIM, future Omniverse/RTX, future AI providers.

## 19. Failure Algebra (frozen, corrected)

Split failure families into THREE distinct sealed/typed families
(no single generic enum that permits authority confusion):

1. **STATIC COMPATIBILITY FAILURE** — deterministic, from CompatibilityKernel:
   CAPABILITY_UNSUPPORTED, CAPABILITY_CONTRACT_VERSION_UNSUPPORTED,
   INPUT_ARTIFACT_INCOMPATIBLE, OUTPUT_ARTIFACT_INCOMPATIBLE,
   CODEC_UNSUPPORTED, DEVICE_KIND_UNSUPPORTED,
   PROVIDER_RUNTIME_CLASS_UNSUPPORTED, PROVIDER_CONTRACT_INCOMPATIBLE,
   SANDBOX_MODE_UNSUPPORTED, DETERMINISM_UNSUPPORTED,
   CROSS_PROVIDER_BOUNDARY_INCOMPATIBLE, LOWERING_SEMANTICALLY_UNREPRESENTABLE.
2. **RUNTIME ELIGIBILITY FAILURE** — mutable facts from
   RuntimeEligibilityEvaluator: PROBE_UNKNOWN, PROBE_STALE, PROBE_FAILED,
   NO_ELIGIBLE_WORKER, NO_ELIGIBLE_DEVICE, WORKER_UNAVAILABLE,
   DEVICE_UNAVAILABLE, RUNTIME_UNAVAILABLE, SANDBOX_RUNTIME_UNAVAILABLE,
   INSUFFICIENT_CURRENT_RESOURCE.
3. **RUNTIME EXECUTION ATTEMPT FAILURE** — execution lifecycle: RUNTIME_START_FAILED,
   RUNTIME_EXECUTION_FAILED, RUNTIME_TIMEOUT, LEASE_LOST, WORKER_LOST,
   DEVICE_LOST, ARTIFACT_MATERIALIZATION_FAILED, CANCELLED.

Runtime failures must NOT leak into CompatibilityKernel deterministic result.
Semantic incompatibility and transient runtime failure MUST be separate.
UNKNOWN fails closed everywhere (UNKNOWN_COMPATIBILITY≠COMPATIBLE,
UNKNOWN_PROVIDER_SUPPORT≠SUPPORTED, UNKNOWN_RUNTIME_AVAILABILITY≠AVAILABLE).

## 20. Determinism / Resource / ExecutionRequirement authority (frozen)

- SEMANTIC_DETERMINISM_REQUIREMENT vs PROVIDER_DECLARED_DETERMINISM_SUPPORT vs RUNTIME_OBSERVED_EXECUTION separated; no second independent determinism authority; compatibility respects upstream RenderExecutionRequirement determinism.
- Runtime resource descriptors for feasibility only; REQUIRED_RESOURCE_CONSTRAINT (frozen input) vs CURRENT_AVAILABLE_RESOURCE (mutable runtime state). Do not redefine upstream semantic requirements.
- #21 ExecutionRequirement remains pure normalized projection; #22 consumes it. No competing Provider/Worker/Runtime ExecutionRequirement semantic authorities; derived constraint views only.

## 20. Formal Foundation (FAOF-2 / FAOF-3)

- FAOF_2 begins with #22. Primary POC Lean4, complementary Coq. Bounded formalization targets: provider compatibility relation, constraint satisfaction laws, feasible candidate filtering, artifact-boundary preservation. No production runtime dependency on theorem provers; POC artifacts are optional verification/proof evidence.
- FAOF_3 bounded beginning: feasibility, compatibility, local constraint satisfaction, bounded deterministic candidate filtering. Distributed/global optimization deferred to #23.

## 21. Formal Laws (frozen)

- LAW_R22_001 INCOMPATIBLE_PROVIDER_NEVER_SELECTED
- LAW_R22_002 UNKNOWN_COMPATIBILITY_FAILS_CLOSED
- LAW_R22_003 OPTIMIZATION_NEVER_CREATES_COMPATIBILITY
- LAW_R22_004 SAME_FROZEN_INPUTS_PRODUCE_SAME_COMPATIBILITY_GRAPH
- LAW_R22_005 MUTABLE_RUNTIME_AVAILABILITY_DOES_NOT_CHANGE_UPSTREAM_SEMANTIC_DIGESTS
- LAW_R22_006 RETRY_CREATES_NEW_ATTEMPT_NOT_NEW_SEMANTIC_TASK
- LAW_R22_007 CROSS_PROVIDER_DEPENDENCY_REQUIRES_EXPLICIT_COMPATIBLE_BOUNDARY
- LAW_R22_008 PROVIDER_LOWERING_PRESERVES_PHYSICAL_UNIT_SEMANTICS
- LAW_R22_009 ONE_PRIMARY_EXECUTABLE_WORK_TASK_PER_PHYSICAL_PLAN_UNIT_V1
- LAW_R22_010 NO_PROVIDER_SPECIFIC_COMMAND_IS_CANONICAL_SEMANTIC_AUTHORITY
- LAW_R22_011 CAPABILITY_ID_IS_PROVIDER_NEUTRAL_AUTHORITY
- LAW_R22_012 PROVIDER_ID_NEVER_REPLACES_CAPABILITY_ID
- LAW_R22_013 CAPABILITY_IMPLEMENTATION_ID_NEVER_REPLACED_BY_PROVIDER_IMPLEMENTATION_ID
- LAW_R22_014 STATIC_COMPATIBILITY_IS_INDEPENDENT_OF_MUTABLE_RUNTIME_STATE
- LAW_R22_015 RUNTIME_ELIGIBILITY_DOES_NOT_MUTATE_PROVIDER_COMPATIBILITY_GRAPH
- LAW_R22_016 WORKER_DEVICE_ASSIGNMENT_DOES_NOT_CHANGE_ETG_DIGEST
- LAW_R22_017 BOUNDARY_ACTION_IS_NOT_INDEPENDENT_SCHEDULABLE_TASK_V1

## 22. Identity/Digest Matrix (corrected)

| Identity | AUTHORITY | BUSINESS_IDENTITY | SEMANTIC_CAPABILITY_IDENTITY | PROVIDER_IDENTITY | RUNTIME_IDENTITY | STABLE | MUTABLE | PERSISTED | DIGEST_PARTICIPATION |
|---|---|---|---|---|---|---|---|---|---|
| CapabilityId | #16 CapabilityRegistry | YES | YES | NO | NO | YES | NO | YES | #20/#21 upstream semantics |
| CapabilityImplementationId | #16 CapabilityRegistry | YES | YES (realization) | NO | NO | YES | NO | YES | upstream semantics |
| ProviderId | #22 provider runtime domain | YES | NO | YES | NO | YES | NO | YES | ETG digest (binding) only |
| ProviderImplementationId | #22 executable provider runtime domain | YES | NO | YES | NO | YES | NO | YES | ETG digest (binding) only |
| WorkerId | #22 worker domain | YES | NO | NO | YES | YES | NO | YES | NOT in ETG digest (runtime/provenance) |
| DeviceId | #22 device domain | YES | NO | NO | YES | YES | NO | YES | NOT in ETG digest (runtime/provenance) |
| ExecutableTaskGraphId | #22 | YES | NO | NO | NO | YES | NO | YES | separate from digest |
| ExecutableTaskGraphDigest | #22 | NO | NO | NO | NO | YES | NO | YES | ETG semantics per §10 frozen include/exclude (provider-bound; worker/device-excluded) |
| ExecutableTaskId | #22 | YES | NO | NO | NO | YES | NO | YES | ETG digest constituent |
| ExecutionAssignmentId | #22 runtime | YES | NO | NO | YES | NO | YES | transient | NOT in any semantic digest |
| ExecutionAttemptId | #22 runtime | YES | NO | NO | YES | NO | NO | YES | provenance only |
| LeaseId | #22 runtime | YES | NO | NO | YES | NO | YES | transient | none |

## 23. Mutability Matrix (corrected)

| Class | Types |
|---|---|
| IMMUTABLE_SEMANTIC | #20 RenderPlan/RenderGraph/RenderNode/RenderDependency semantics, #21 ExecutionRequirement/LogicalExecutionGraph/PhysicalExecutionPlan + digests, Artifact content pin, CapabilityId/CapabilityImplementationId authority |
| IMMUTABLE_EXECUTABLE_BINDING | ProviderExecutionContract/ProviderDescriptor (pinned), ProviderCapabilityProfile (immutable pinned declaration), ProviderId/ProviderImplementationId binding pins, ProviderBoundExecutableTaskGraph (immutable executable binding), ProviderCompatibilityGraph (immutable deterministic derivation), PlanLowerer output spec |
| MUTABLE_RUNTIME_STATE | WorkerRuntimeState, DeviceRuntimeState, ProviderProbeResult (mutable runtime evidence), ExecutionAssignment (runtime placement), ExecutionAttempt (runtime lifecycle), lease, heartbeat, availability, retry counter, RuntimeEligibleCandidateView (ephemeral mutable derivation) |
| OBSERVABILITY_ONLY | timestamps, metrics, queue depth, utilization, telemetry |
| PROVENANCE_ONLY | provider version, worker/device identity, adapter version, attempt ids, input/output pins, failure reasons |

## 24. #22/#23 Boundary Matrix

#22: Provider Contract, CapabilityProfile, ProviderProbePort, Worker, Device, ExecutableTaskGraph, local dispatch, retry, lease/heartbeat primitive, sandbox, PlanLowerer, RuntimeAdapter, Constraint Kernel (bounded), FAOF-2 POC, FAOF-3 bounded start.
#23: distributed worker fleet, global scheduling, global optimization, cross-region placement, fleet-wide cost optimization, global locality optimization, large-scale solver integration.

## 25. Product/Infrastructure Authority Matrix

| Product | ROLE | MAY_IMPLEMENT | MUST_NOT_OWN | ACTIVATION |
|---|---|---|---|---|
| FFmpeg | provider implementation | provider adapter, PlanLowerer target | canonical semantics | #22 early (CPU), #22 late (QSV/NVENC) |
| Blender | provider implementation | provider adapter | canonical semantics | #22 late |
| NVIDIA/CUDA | runtime implementation | device descriptor, adapter | canonical semantics | #22 late / conformance |
| AWS | infra | ephemeral conformance worker | domain authority | #22 late |
| PVE | infra | guest/worker hosting | domain authority | validation env |
| Podman | runtime impl | sandbox backend | domain authority | #22 |
| LXC | runtime impl | isolation backend | domain authority | validation env |
| RustFS | storage impl | artifact storage backend | artifact authority | #22 early optional |
| R2 | storage impl | artifact storage backend | artifact authority | #22 mid |
| Redis | cache impl | probe/lease cache | architecture | only with consumer |
| PostgreSQL | relational impl | persistence | vendor authority | existing |
| Novu | notification impl | external channel orchestration | #22 scheduler | later |
| n8n | automation impl | external integration adapter | workflow authority | later |
| OpenUSD/Hydra/etc | interchange tech | future scene provider hooks | scene canonical authority | later |
| Omniverse | product surface | future provider | canonical media domain | later |

## 26. Module Boundary Proposal

Do NOT create a giant runtime-module. Recommended bounded placement (repository reality check):
- Provider contract/SPI (ProviderContract, ProviderDescriptor, CapabilityProfile, ProviderProbePort, PlanLowerer, RuntimeAdapter) → media-execution-plan-module execution domain extension or a dedicated bounded provider-contract package under the execution planning module (nearest to PhysicalExecutionPlan consumer); evaluate existing module boundaries before deciding.
- Constraint Kernel + compatibility graph → same bounded module (pure, provider-independent).
- ExecutableTaskGraph + identities → same bounded module.
- Worker/Device domain → bounded worker-fabric package/module (may reuse remote-render-worker patterns after disposition).
- Provider adapters (FFmpeg/Blender) → separate adapter modules depending on SPI, never core.
DEPENDENCY_DIRECTION: #20 Render → #21 Execution Planning → #22 Runtime Contracts → Provider Adapters / Worker Runtime. Canonical core must not depend back on FFmpeg/Blender/AWS/CUDA/RustFS/R2.

## 27. Decision Table (required unresolved choices)

| QUESTION | OPTIONS | REPO EVIDENCE | TRADEOFFS | RECOMMENDED | ESCALATION |
|---|---|---|---|---|---|
| Infrastructure boundary representation | A typed executable task kinds / B typed boundary actions on primary tasks | No existing #22 task model; outbox PlatformTask is flat task taxonomy | A parallel taxonomy risks two authorities; B keeps one-task-per-unit law | B | NO |
| ExecutableTaskGraph binding in digest | bound (includes provider binding) vs unbound (pure semantics) | PhysicalExecutionPlan is provider-neutral | bound graph is concrete executable artifact; unbound is semantic-only | Bound-by-default with explicit statement (contract §9) | NO |
| Legacy RenderProvider fate | migrate/delete | god-interface + string capability + deprecated javacv | CLEAN FORWARD vs migration cost | MIGRATE_REDESIGN core interface; DELETE_SHADOW javacv deprecated | NO (implementation phase) |
| ExecutionProvider fate | delete/replace | FROZEN sealed Stub, "out of scope V1" | #22 owns provider contract; Stub is placeholder | DELETE_SHADOW → replace with #22 Provider SPI | NO |
| Worker registry reuse | adopt remote-render-worker registry vs new bounded worker-fabric | WorkerRegistryService exists (in-memory, job-scoped) | reuse mechanics vs clean #22 model | REUSE_MECHANICS_ONLY (patterns), new #22 typed model | NO |
| Outbox PlatformTask reuse | adopt lease/attempt semantics vs #22 own | PlatformTask has lease/attempt/retry already | outbox is event coordination, not runtime dispatch | REUSE_MECHANICS_ONLY for patterns; no authority merge | NO |
| Module placement | extend media-execution-plan-module vs new runtime-module | no runtime-module exists; execution-plan-module owns PhysicalExecutionPlan | avoid module proliferation vs clear boundary | bounded packages within execution-plan-module (or minimal new module per final review) | NO |

## 28. Zero Guard Plan (implementation phase)

Original guards:
PHYSICAL_PLAN_PROVIDER_BINDING_COUNT=0, PHYSICAL_PLAN_WORKER_BINDING_COUNT=0, PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0, UPSTREAM_DIGEST_MUTABLE_RUNTIME_FIELD_COUNT=0, PROVIDER_SPECIFIC_COMMAND_CANONICAL_AUTHORITY_COUNT=0, STRINGLY_TYPED_PROVIDER_CAPABILITY_AUTHORITY_COUNT=0, UNKNOWN_COMPATIBILITY_ACCEPTANCE_COUNT=0, INCOMPATIBLE_PROVIDER_SELECTION_COUNT=0, CROSS_PROVIDER_IMPLICIT_MEMORY_BOUNDARY_COUNT=0, TASK_RETRY_CREATES_NEW_SEMANTIC_TASK_COUNT=0, PROVIDER_PROBE_CANONICAL_AUTHORITY_COUNT=0, ROADMAP_23_GLOBAL_OPTIMIZER_COUNT=0, KUBERNETES_DOMAIN_AUTHORITY_COUNT=0, COMPATIBILITY_WRAPPER_COUNT=0.

Additional guards (R22-DR-C1..C5 correction):
PROVIDER_ID_AS_CAPABILITY_ID_AUTHORITY_COUNT=0, PROVIDER_CAPABILITY_PROFILE_CAPABILITY_REGISTRY_AUTHORITY_COUNT=0, PROVIDER_IMPLEMENTATION_REPLACES_CAPABILITY_IMPLEMENTATION_COUNT=0, COMPATIBILITY_KERNEL_MUTABLE_RUNTIME_READ_COUNT=0, PROVIDER_COMPATIBILITY_GRAPH_RUNTIME_STATE_FIELD_COUNT=0, ETG_WORKER_ID_SEMANTIC_FIELD_COUNT=0, ETG_DEVICE_ID_SEMANTIC_FIELD_COUNT=0, ETG_MUTABLE_PROBE_FIELD_COUNT=0, BOUNDARY_ACTION_INDEPENDENT_TASK_AUTHORITY_COUNT=0.

All previous zero-guard plans retained.

## 29. Infrastructure Activation Matrix (NOT NOW)

#22 EARLY: local PostgreSQL/Testcontainers, Z8 CPU provider, local filesystem Artifact backend, optional RustFS test instance, pve-2 Intel media worker when runtime begins.
#22 MID: R2 conformance, observability, provider probe integration.
#22 LATE: AWS NVIDIA ephemeral worker, Blender GPU, NIM/provider conformance.
Redis: only if concrete cache/lease/probe consumer exists. Novu/n8n: external ecosystem later, not core prerequisite. KUBERNETES=DEFER.

## 30. Implementation Phase Plan (proposal, adjust to repo reality)

PHASE_0 CLEAN_FORWARD source disposition → PHASE_1 Provider Contract/Descriptor/CapabilityProfile → PHASE_2 Worker/Device static+runtime separation → PHASE_3 Constraint Kernel + typed compatibility → PHASE_4 Provider Compatibility Graph → PHASE_5 ExecutableTaskGraph + identities/digest → PHASE_6 PlanLowerer + RuntimeAdapter SPI → PHASE_7 Artifact materialization boundary → PHASE_8 bounded local worker runtime (dispatch/attempt/retry/cancellation) → PHASE_9 lease/heartbeat/probe-cache → PHASE_10 sandbox/isolation → PHASE_11 FAOF-2 Lean4+Coq POC → PHASE_12 FFmpeg CPU + Intel QSV/VAAPI conformance → PHASE_13 NVIDIA/cloud conformance → PHASE_14 freeze/FCV/review.

## 31. Parallel Task DAG (future, not now)

BOUNDED_PARALLEL_CODEX_EXECUTION_V1=ADOPTED; ONE_CODEX_EXECUTOR_ONE_ISOLATED_WORKTREE_V1; HERMES_OWNS_TASK_DAG_AND_CONVERGENCE_V1; PARALLEL_IMPLEMENTATION_DOES_NOT_CREATE_PARALLEL_ARCHITECTURE_AUTHORITIES_V1.
Initial target 2-3 sessions after contract freeze. Candidate lanes: A Provider Contract/Descriptor/CapabilityProfile, B Worker/Device model, C FAOF-2 Lean4 POC — parallel if WRITE_SET_DISJOINT, AUTHORITY_SET_NON_CONFLICTING, NO_UNRESOLVED_API_DEPENDENCY, SAME_FROZEN_BASE. Constraint Kernel/convergence follow foundations.

## 32. Model Routing Recommendations (future implementation)

Architecture-sensitive: gpt-5.6-sol reasoning=high. Balanced: gpt-5.6-terra high. Mechanical: gpt-5.6-terra medium/high or gpt-5.6-luna medium where justified. Fresh candidate review: codex-auto-review high. Adversarial review: gpt-5.6-sol high/xhigh with explicit reason. No silent fallback.

## 33. Escalation Conditions (monitored, none triggered)

A. #21 provider-neutral plan cannot support binding without redesign → NOT OBSERVED (PhysicalExecutionPlan is neutral; binding is #22-side).
B. Artifact authority cannot represent cross-provider materialization → NOT OBSERVED (Artifact immutable data plane suffices).
C. Provider compatibility conflicts with frozen Capability model → NOT OBSERVED (EffectCapabilityProfile is visual-domain; #22 CapabilityProfile is execution-domain; no conflict identified).
D. Worker/device requires modifying Media/Timeline/Render authorities → NOT OBSERVED.
E. Persisted/external compatibility proves CLEAN FORWARD deletion unsafe → NOT OBSERVED (no shipped #22 surface exists).
F. #22 cannot stay bounded/local → NOT OBSERVED.
G. Formalization reveals contradiction in laws → NOT OBSERVED (laws are consistent at DR level).

## 34. Final Decision Recovery Status (Correction 1)

ROADMAP_22_DECISION_RECOVERY_CORRECTION_1=PASS (draft, pending ChatGPT review)
ROADMAP_22_DECISION_RECOVERY=PASS (as corrected)
READY_FOR_CHATGPT_ROADMAP_22_DECISION_RECOVERY_CORRECTION_1_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
ROADMAP_23=NOT_STARTED
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE

NEXT_ACTION=CHATGPT_ROADMAP_22_DECISION_RECOVERY_CORRECTION_1_REVIEW
