# Roadmap #22 Executable Task Graph and Worker Fabric Runtime — Decision Recovery

STATUS=FROZEN_BOUNDED_ARCHITECTURE_CONTRACT (as amended by Amendment 1 + Amendment 2)
READY_FOR_CHATGPT_FINAL_DECISION_RECOVERY_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
AMENDMENT_1=docs/architecture/governance/roadmap-22-shared-worker-fabric-provider-local-composition-amendment-1.md
AMENDMENT_2=docs/architecture/governance/roadmap-22-native-pull-lease-execution-backend-amendment-2.md
PRECEDENCE: Amendment 2 wins over Amendment 1 ONLY for assignment protocol,
  ExecutionBackend/DispatchBackend terminology, Native Pull default role,
  TaskLease/local admission semantics, OpenCue Role V2, backend-specific
  placement authority. Amendment 1 remains authority for its non-conflicting
  frozen content. Where this contract conflicts with either amendment, the
  amendments govern (LAW_R22_009 is SUPERSEDED by A1; DispatchBackend peer
  authority and OPEN_CUE_ROLE_V1 are SUPERSEDED by A2).

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
| DR original | c0d43852b5d028089e7a2790c96495cee3ff2239 |
| DR Correction 1 | 06064e3cc3f5eda4896d130696cf7a08b26cacfd |

## 2. Decision Recovery Mode

MODE=ARCHITECTURE_DECISION_RECOVERY_FINAL_COHERENCE_CORRECTION
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
9. **EffectCapabilityProfile / TransitionCapabilityProfile** (render-module/domain/visual) — bounded visual capability definitions (SCALE/CROP/... with category/status/consistency/fallback/safety) — typed capability model already exists for visual effects; visual-domain ONLY, NOT #22 execution authority.
10. **Ai-module provider routing** (ai-module) — ChatProvider, ConfigurableModelRouter, RouteTarget, AiRoutingProperties — AI-provider routing (separate concern; may reuse routing patterns but not authority).
11. **Artifact module** — Artifact authority (immutable media data plane).
12. **Storage module / storage-provider-opendal** — filesystem/R2/RustFS-capable storage backends.

## 5. Authority Model (frozen)

### 5.1 PhysicalExecutionPlan
Remains provider-neutral / worker-neutral / device-neutral. Must NOT gain providerId, workerId, deviceId, queue/probe state, availability, lease, heartbeat, runtime retries. #22 consumes it; does not redefine it.

### 5.2 Capability / Provider identity authority separation (frozen)

**CapabilityId** — AUTHORITY: Roadmap #16 CapabilityRegistry. Meaning: semantic
capability contract identity (e.g. media.decode.*, video.encode.*, render.*).
Stable, typed, namespaced, implementation-neutral, provider-neutral,
plan-neutral. CapabilityRequirement expresses WHAT capability is required.
#22 does NOT redefine this.

**CapabilityImplementationId** — AUTHORITY: Roadmap #16 CapabilityRegistry.
Meaning: one concrete realization of ONE CapabilityId.
SEMANTIC_CAPABILITY_IDENTITY=NO (it does NOT replace CapabilityId, does NOT
become semantic Capability contract identity); CAPABILITY_IMPLEMENTATION_IDENTITY=YES.
Existing authority unchanged. Provider/Worker/Device do not enter this identity.
CAPABILITY_IMPLEMENTATION_ID_IN_20_21_UPSTREAM_DIGEST_BY_DEFAULT=NO:
#20/#21 provider-neutral requirements express semantic capability
requirements through CapabilityId + contract compatibility, NOT concrete
provider/runtime implementation selection. CapabilityImplementationId MAY
participate in the #22 ETG digest ONLY IF a ProviderCapabilityProfile support
declaration explicitly pins/references it as part of the selected provider
binding — then it is ETG_PROVIDER_BINDING_PARTICIPATION, NOT
UPSTREAM_SEMANTIC_AUTHORITY.

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
CapabilityImplementationId (ETG_PROVIDER_BINDING_PARTICIPATION only).
ProviderCapabilityProfile is an EXECUTION_FEASIBILITY_PROJECTION — NOT a
CapabilityRegistry, NOT capability definition authority, NOT capability
contract lifecycle authority. It does NOT copy or recreate capability
lifecycle authority.

Typed relationship (frozen shape):

```
ProviderImplementation {
  ProviderImplementationId
  ProviderId
  ProviderVersion
  ProviderExecutionContractVersion
  ProviderCapabilityProfile   // typed support declarations -> CapabilityId(s)
                              // + ContractVersion/Range + optional CapabilityImplementationId pin
}
```

### 5.3 ProviderProbePort
- DECLARED_PROVIDER_CAPABILITY vs OBSERVED_RUNTIME_AVAILABILITY separation.
- Probe results: mutable, time-sensitive, ephemeral. Never enter Timeline content hash / RenderPlan fingerprint / LogicalExecutionGraph digest / PhysicalExecutionPlan digest.

## 6. Two-Stage Feasibility (frozen)

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
its results are deterministic over frozen inputs. Bounded FAOF-3 beginning.

## 9. Provider Compatibility Graph (frozen)

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

## 10. ProviderBoundExecutableTaskGraph V1 (frozen)

EXECUTABLE_TASK_GRAPH_V1=PROVIDER_BOUND, WORKER_RUNTIME_UNBOUND, PHYSICAL_HOST_UNBOUND, DEVICE_ASSIGNMENT_UNBOUND (until runtime placement). Amended by Amendment 1 §4/§16: membership model (every PhysicalPlanUnit → exactly one ExecutableTaskMembership; ExecutableTask → one or more memberships when provider-local composition is proven). LAW_R22_009 (strict 1:1) is SUPERSEDED.

- Provider selection occurs BEFORE ETG V1 construction/freeze.
- Specific WorkerRuntimeId / PhysicalHostId / DeviceId are NOT part of ETG
  semantic content; runtime placement is ExecutionAssignment (§13).
- Infrastructure boundary representation: **B — typed boundary actions
  attached to primary tasks** (single authority, no parallel task-kind
  authority).
- ExecutableTaskId / ExecutableTaskGraphId / ExecutableTaskGraphDigest:
  business identity != semantic digest; runtime attempt identity != task
  identity; retry creates new attempt, never new task. ExecutableTaskId may
  participate in ETG digest BECAUSE its identity is deterministically derived
  solely from frozen ETG binding inputs (§11). ExecutableTaskGraphId remains
  business identity separate from digest. No runtime-generated identity
  participates.

### ExecutableTaskGraphDigest — frozen inclusion/exclusion (NOT conditional)

ETG digest includes canonical ProviderBindingPin semantics; it does NOT
separately define slightly different provider binding fields (single binding
authority, LAW_R22_023).

ETG_DIGEST_INCLUDE at minimum:
- graph format/schema version
- exact PhysicalPlanUnit identity/reference
- task dependency topology
- deterministic ExecutableTaskId (§11)
- canonical ProviderBindingPin (§10)
- typed BoundaryAction semantics
- immutable required input Artifact pins
- other explicitly frozen lowering semantics

ETG_DIGEST_EXCLUDE:
- ExecutableTaskGraphId (business id separate)
- WorkerId
- DeviceId
- ExecutionAssignmentId
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
- ACTUAL OUTPUT Artifact pins (do not exist before successful execution;
  they are EXECUTION_RESULT / PROVENANCE_LINEAGE, immutable after Artifact
  commit — NOT an input to the pre-execution ETG digest; do not confuse
  output requirements with actual output Artifact identity)

Frozen laws:
WORKER_ASSIGNMENT_DOES_NOT_CHANGE_EXECUTABLE_TASK_GRAPH_DIGEST_V1
DEVICE_ASSIGNMENT_DOES_NOT_CHANGE_EXECUTABLE_TASK_GRAPH_DIGEST_V1

### ProviderBindingPin — SINGLE canonical definition (frozen)

PROVIDER_BINDING_PIN_DEFINITION_COUNT=1. There is exactly ONE canonical
ProviderBindingPin definition; all sections reference this exact definition.

```
ProviderBindingPin {
  ProviderId
  ProviderImplementationId
  ProviderVersion
  ProviderExecutionContractVersion
  ProviderCapabilityProfileVersionOrDigest
  canonical optional CapabilityImplementationId pins
}
```

- ProviderBindingPin is the single executable-binding authority
  (LAW_R22_023). No separately-defined slightly-different binding fields
  anywhere.
- Optional CapabilityImplementationId pin ordering: CAPABILITY_IMPLEMENTATION_PIN_ORDER_IS_NONSEMANTIC_V1 —
  canonical serialization sorts pins deterministically by typed canonical
  identity before framing. NO insertion order, NO traversal position.
- Multiplicity: the pin set represents selected DISTINCT implementations.
  Duplicate identical pin = INVALID_PROVIDER_BINDING, fail closed
  (LAW_R22_022). Pin permutation does not change the binding
  (LAW_R22_021).
- Mutable probe results do NOT participate.

### ProviderVersion semantics (frozen)

ProviderVersion MUST version the provider implementation behavior that may
affect deterministic PlanLowerer output / executable provider binding. If
provider lowering behavior changes incompatibly, ProviderVersion and/or
ProviderExecutionContractVersion MUST change. ProviderVersion is part of the
immutable ProviderBindingPin: IMMUTABLE_EXECUTABLE_BINDING=YES,
ETG_BINDING_PARTICIPATION=YES. It MAY also be copied into execution
provenance; it is NOT PROVENANCE_ONLY. A RuntimeAdapter implementation
version may be provenance-only ONLY when changing it does not change
executable semantic lowering; if runtime adapter behavior changes semantics,
it must be reflected through an immutable Provider binding/version contract,
not hidden in provenance. No unpinned semantic execution behavior.

## 11. ExecutableTaskId — Deterministic Identity (frozen)

EXECUTABLE_TASK_ID_V1_DETERMINISTIC=YES.

Because ExecutableTaskId participates in ETG digest, it MUST be determined
only from frozen executable binding inputs.

Canonical identity input:
```
PhysicalPlanUnit stable identity/reference
+ ProviderBindingPin
```
ProviderBindingPin consists of:
- ProviderId
- ProviderImplementationId
- ProviderVersion
- ProviderExecutionContractVersion
- ProviderCapabilityProfileVersionOrDigest
- optional CapabilityImplementationId pin(s) only where explicitly selected

The exact encoding MUST use deterministic structural framing. A stable
digest-derived strong ID is acceptable.

FORBIDDEN inputs: WorkerId, DeviceId, ExecutionAssignmentId, ExecutionAttemptId,
probe result, queue state, heartbeat, lease, retry count, clock/time, random
UUID, DB sequence, traversal position, runtime availability.

Frozen laws:
LAW_R22_019 SAME_FROZEN_PHYSICAL_UNIT_AND_PROVIDER_BINDING_PRODUCES_SAME_EXECUTABLE_TASK_ID
LAW_R22_020 WORKER_DEVICE_ATTEMPT_ASSIGNMENT_NEVER_CHANGES_EXECUTABLE_TASK_ID

## 12. Provider Binding / PlanLowerer / RuntimeAdapter (frozen)

- CapabilityRequirement ≠ ProviderImplementation. PhysicalPlanUnit says WHAT;
  executable binding says WHICH. No provider name in canonical #20/#21
  semantics.
- PlanLowerer: immutable provider lowering contract (media-execution-plan-module).
  Typed PhysicalPlanUnit/ExecutableTask → provider-specific executable
  specification. MUST NOT redefine semantic meaning; fails closed if it cannot
  represent requested semantics. FFmpeg/Blender/CUDA command lines never
  canonical semantic authority (LAW_R22_010).
- RuntimeAdapter: runtime launch/monitor/cancel contract (worker-fabric-module).
  Separate from PlanLowerer's WHAT. Same provider semantics + different runtime
  environments (FFmpeg local / container / remote worker) without redefining
  capability semantics.
- A provider adapter may implement BOTH, but the interfaces remain separated.
  No combined Provider.execute() god interface.
- ProviderExecutionContract covers provider SPI compatibility, lowering
  contract, runtime adapter contract, provider implementation schema/version —
  and references upstream Capability contracts rather than redefining them.

## 13. ExecutionAssignment (frozen)

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

## 14. Worker / Device (frozen)

- WorkerDescriptor (stable/static: WorkerId, runtime environment, supported isolation modes, declared device inventory refs, execution backend class) separate from WorkerRuntimeState (AVAILABLE/BUSY/DEGRADED/UNAVAILABLE/DRAINING — mutable, not in digests).
- DeviceDescriptor (stable: DeviceId, DeviceKind CPU|GPU|MEDIA_ACCELERATOR|OTHER_ACCELERATOR, vendor, model, driver compatibility, memory class, media engines, compute features) separate from DeviceRuntimeState (free memory/utilization/temperature — mutable).
- No NVIDIA-only assumptions.

## 15. Artifact / Cross-Provider Data Plane (frozen)

- Artifact remains immutable media data-plane authority. Provider runtime consumes/produces ArtifactId + typed metadata + immutable content pin/digest.
- No provider-private filesystem path / mutable URL / temp local path as canonical artifact identity.
- CROSS_PROVIDER_ARTIFACT_MATERIALIZATION_BOUNDARY_V1: provider X → immutable output Artifact → storage/data plane → provider Y input. No hidden shared-memory assumption, no implicit provider-private object passing. Direct provider-to-provider shortcut only as explicit later optimization with proven compatibility; never required for correctness.
- Locality (LOCAL/REMOTE/CACHED/NEEDS_MATERIALIZATION) is runtime info, not canonical identity; global locality optimization is #23.
- RUSTFS_IS_SELF_HOSTED_S3_BACKEND_NOT_ARTIFACT_AUTHORITY_V1; RUSTFS_AND_R2_MUST_PASS_COMMON_ARTIFACT_STORAGE_CONFORMANCE_V1; S3_VENDOR_SPECIFIC_BEHAVIOR_MUST_NOT_DEFINE_ARTIFACT_SEMANTICS_V1.

## 16. Sandbox / Isolation (frozen)

- Runtime isolation semantics frozen; implementation environments (native process, OCI/rootless container, LXC, VM, remote sandbox) are implementations, not domain authority. Podman/Docker/LXC/KVM/Kubernetes are NOT domain authorities.
- Canonical execution requirements may require isolation characteristics; provider runtime must prove satisfaction.
- TRUST_PERMISSION_SANDBOX_V1: authorization/trust checks occur before runtime execution; ProviderAdapter.execute() must NOT bypass application/OperationPlan authorization where applicable.

## 17. Task Execution Lifecycle / Attempt / Retry (frozen)

- Lifecycle (runtime, mutable, not canonical media revision semantics): CREATED, READY, DISPATCHED, RUNNING, SUCCEEDED, FAILED, CANCELLED (bounded; exact names may adapt to repo reality).
- ExecutableTask ≠ ExecutionAttempt. Retries create new attempts (ExecutionAttemptId), never new semantic tasks (LAW_R22_006).
- Retry typed and bounded: RETRYABLE_RUNTIME_FAILURE vs NON_RETRYABLE_RUNTIME_FAILURE vs SEMANTIC_INCOMPATIBILITY. Never retry semantic incompatibility. Retry policy NOT in PhysicalExecutionPlan.

## 18. BoundaryAction V1 (frozen)

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

## 19. Lease / Heartbeat / Cancellation (frozen)

- Lease, heartbeat, lease expiry, worker loss are runtime mechanics; never canonical media state / RenderPlan state / PhysicalExecutionPlan semantics. #22 establishes the local/bounded primitive contract; distributed fleet coordination is #23.
- Cancellation distinguishes cancel task attempt vs delete canonical media result. Cancellation never rewrites historical media state; explicit semantics when a completed immutable Artifact exists.

## 20. Runtime Probe Cache (frozen)

- CACHE != AUTHORITY. ProviderProbePort remains evidence source. Cache entries require timestamp/freshness, provider implementation identity, worker/device identity, probe schema/version. Stale probe fails closed or re-probes per contract. Redis may be implementation later; REDIS_IS_NOT_REQUIRED_ARCHITECTURE_V1.

## 21. Provider SPI (frozen)

No god interface. Separated responsibilities:
- ProviderDescriptor (static declared metadata)
- ProviderExecutionContract (provider SPI/execution contract only)
- ProviderCapabilityProfile (execution support projection only)
- ProviderProbePort (runtime evidence)
- PlanLowerer (WHAT request)
- RuntimeAdapter (HOW launch/monitor/cancel)

Neither ProviderCapabilityProfile nor ProviderExecutionContract owns semantic
Capability identity or lifecycle.

Design test cases: FFmpeg software, FFmpeg Intel QSV/VAAPI, FFmpeg NVIDIA
NVENC/NVDEC, Blender CPU, Blender CUDA/OptiX, NVIDIA NIM, future
Omniverse/RTX, future AI providers.

## 22. Failure Algebra (frozen)

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

## 23. Determinism / Resource / ExecutionRequirement authority (frozen)

- SEMANTIC_DETERMINISM_REQUIREMENT vs PROVIDER_DECLARED_DETERMINISM_SUPPORT vs RUNTIME_OBSERVED_EXECUTION separated; no second independent determinism authority; compatibility respects upstream RenderExecutionRequirement determinism.
- Runtime resource descriptors for feasibility only; REQUIRED_RESOURCE_CONSTRAINT (frozen input) vs CURRENT_AVAILABLE_RESOURCE (mutable runtime state). Do not redefine upstream semantic requirements.
- #21 ExecutionRequirement remains pure normalized projection; #22 consumes it. No competing Provider/Worker/Runtime ExecutionRequirement semantic authorities; derived constraint views only.

## 24. Formal Foundation (FAOF-2 / FAOF-3)

- FAOF_2 begins with #22. Primary POC Lean4, complementary Coq. Bounded formalization targets: provider compatibility relation, constraint satisfaction laws, feasible candidate filtering, artifact-boundary preservation. No production runtime dependency on theorem provers; POC artifacts are optional verification/proof evidence.
- FAOF_3 bounded beginning: feasibility, compatibility, local constraint satisfaction, bounded deterministic candidate filtering. Distributed/global optimization deferred to #23.

## 25. Formal Laws (frozen)

- LAW_R22_001 INCOMPATIBLE_PROVIDER_NEVER_SELECTED
- LAW_R22_002 UNKNOWN_COMPATIBILITY_FAILS_CLOSED
- LAW_R22_003 OPTIMIZATION_NEVER_CREATES_COMPATIBILITY
- LAW_R22_004 SAME_FROZEN_INPUTS_PRODUCE_SAME_COMPATIBILITY_GRAPH
- LAW_R22_005 MUTABLE_RUNTIME_AVAILABILITY_DOES_NOT_CHANGE_UPSTREAM_SEMANTIC_DIGESTS
- LAW_R22_006 RETRY_CREATES_NEW_ATTEMPT_NOT_NEW_SEMANTIC_TASK
- LAW_R22_007 CROSS_PROVIDER_DEPENDENCY_REQUIRES_EXPLICIT_COMPATIBLE_BOUNDARY
- LAW_R22_008 PROVIDER_LOWERING_PRESERVES_PHYSICAL_UNIT_SEMANTICS
- LAW_R22_009 ~~ONE_PRIMARY_EXECUTABLE_WORK_TASK_PER_PHYSICAL_PLAN_UNIT_V1~~ **SUPERSEDED by Amendment 1** — replaced by membership model (LAW_R22_025: every PhysicalPlanUnit → exactly one ExecutableTaskMembership; LAW_R22_026: ExecutableTask may contain multiple memberships only when provider-local composition is proven). Not retained as parallel V1 contract. See Amendment 1 §3-§6.
- LAW_R22_010 NO_PROVIDER_SPECIFIC_COMMAND_IS_CANONICAL_SEMANTIC_AUTHORITY
- LAW_R22_011 CAPABILITY_ID_IS_PROVIDER_NEUTRAL_AUTHORITY
- LAW_R22_012 PROVIDER_ID_NEVER_REPLACES_CAPABILITY_ID
- LAW_R22_013 CAPABILITY_IMPLEMENTATION_ID_NEVER_REPLACED_BY_PROVIDER_IMPLEMENTATION_ID
- LAW_R22_014 STATIC_COMPATIBILITY_IS_INDEPENDENT_OF_MUTABLE_RUNTIME_STATE
- LAW_R22_015 RUNTIME_ELIGIBILITY_DOES_NOT_MUTATE_PROVIDER_COMPATIBILITY_GRAPH
- LAW_R22_016 WORKER_DEVICE_ASSIGNMENT_DOES_NOT_CHANGE_ETG_DIGEST
- LAW_R22_017 BOUNDARY_ACTION_IS_NOT_INDEPENDENT_SCHEDULABLE_TASK_V1
- LAW_R22_018 CAPABILITY_IMPLEMENTATION_SELECTION_NEVER_MUTATES_UPSTREAM_PROVIDER_NEUTRAL_SEMANTICS
- LAW_R22_019 SAME_FROZEN_PHYSICAL_UNIT_AND_PROVIDER_BINDING_PRODUCES_SAME_EXECUTABLE_TASK_ID
- LAW_R22_020 WORKER_DEVICE_ATTEMPT_ASSIGNMENT_NEVER_CHANGES_EXECUTABLE_TASK_ID
- LAW_R22_021 CAPABILITY_IMPLEMENTATION_PIN_PERMUTATION_DOES_NOT_CHANGE_PROVIDER_BINDING
- LAW_R22_022 DUPLICATE_CAPABILITY_IMPLEMENTATION_PIN_FAILS_CLOSED
- LAW_R22_023 PROVIDER_BINDING_PIN_IS_SINGLE_EXECUTABLE_BINDING_AUTHORITY
- LAW_R22_024 INPUT_ARTIFACT_PIN_PARTICIPATES_IN_EXECUTABLE_BINDING_WHILE_ACTUAL_OUTPUT_ARTIFACT_PIN_IS_EXECUTION_RESULT

## 26. Identity/Digest Matrix (final)

| Identity | AUTHORITY | BUSINESS_IDENTITY | SEMANTIC_CAPABILITY_IDENTITY | CAPABILITY_IMPLEMENTATION_IDENTITY | PROVIDER_IDENTITY | RUNTIME_IDENTITY | STABLE | MUTABLE | PERSISTED | DIGEST_PARTICIPATION |
|---|---|---|---|---|---|---|---|---|---|---|
| CapabilityId | #16 CapabilityRegistry | YES | YES | NO | NO | NO | YES | NO | YES | #20/#21 upstream semantics |
| CapabilityImplementationId | #16 CapabilityRegistry | YES | NO | YES | NO | NO | YES | NO | YES | NOT in #20/#21 upstream digests by default; ETG digest ONLY when explicitly pinned via ProviderCapabilityProfile support declaration (ETG_PROVIDER_BINDING_PARTICIPATION) |
| ProviderId | #22 provider runtime domain | YES | NO | NO | YES | NO | YES | NO | YES | ETG digest (via ProviderBindingPin) only |
| ProviderImplementationId | #22 executable provider runtime domain | YES | NO | NO | YES | NO | YES | NO | YES | ETG digest (via ProviderBindingPin) only |
| ProviderVersion | #22 provider runtime domain | NO | NO | NO | component of ProviderBindingPin | NO | YES | NO | YES | ETG digest (via ProviderBindingPin); MAY copy to provenance; NOT provenance-only |
| WorkerId | #22 worker domain | YES | NO | NO | NO | YES | YES | NO | YES | NOT in ETG digest (runtime/provenance) |
| DeviceId | #22 device domain | YES | NO | NO | NO | YES | YES | NO | YES | NOT in ETG digest (runtime/provenance) |
| ExecutableTaskGraphId | #22 | YES | NO | NO | NO | NO | YES | NO | YES | separate from digest |
| ExecutableTaskGraphDigest | #22 | NO | NO | NO | NO | NO | YES | NO | YES | ETG semantics per §10 frozen include/exclude (provider-bound via ProviderBindingPin; worker/device-excluded) |
| ExecutableTaskId | #22 | YES | NO | NO | NO | NO | YES | NO | YES | ETG digest constituent (deterministic from PhysicalPlanUnit + ProviderBindingPin) |
| ExecutionAssignmentId | #22 runtime | YES | NO | NO | NO | YES | NO | YES | transient | NOT in any semantic digest |
| ExecutionAttemptId | #22 runtime | YES | NO | NO | NO | YES | NO | NO | YES | provenance only |
| LeaseId | #22 runtime | YES | NO | NO | NO | YES | NO | YES | transient | none |

## 27. Mutability Matrix (final)

| Class | Types |
|---|---|
| IMMUTABLE_SEMANTIC | #20 RenderPlan/RenderGraph/RenderNode/RenderDependency semantics, #21 ExecutionRequirement/LogicalExecutionGraph/PhysicalExecutionPlan + digests, Artifact content pin, CapabilityId/CapabilityImplementationId authority |
| IMMUTABLE_EXECUTABLE_BINDING | ProviderExecutionContract/ProviderDescriptor (pinned), ProviderCapabilityProfile (immutable pinned declaration), ProviderBindingPin (single canonical definition incl. ProviderId/ProviderImplementationId/ProviderVersion/ProviderExecutionContractVersion/ProviderCapabilityProfileVersionOrDigest/canonical optional CapabilityImplementationId pins), ProviderVersion (ETG_BINDING_PARTICIPATION=YES, NOT provenance-only), immutable required input Artifact pins (ETG_DIGEST_PARTICIPATION=YES, NOT provenance-only), ProviderBoundExecutableTaskGraph (immutable executable binding), ExecutableTaskId (deterministic), ProviderCompatibilityGraph (immutable deterministic derivation), PlanLowerer output spec |
| MUTABLE_RUNTIME_STATE | WorkerRuntimeState, DeviceRuntimeState, ProviderProbeResult (mutable runtime evidence), ExecutionAssignment (runtime placement), ExecutionAttempt (runtime lifecycle), lease, heartbeat, availability, retry counter, RuntimeEligibleCandidateView (ephemeral mutable derivation) |
| EXECUTION_RESULT / PROVENANCE_LINEAGE | ACTUAL OUTPUT Artifact pins (do not exist before successful execution; immutable after Artifact commit; NOT input to pre-execution ETG digest) |
| OBSERVABILITY_ONLY | timestamps, metrics, queue depth, utilization, telemetry |
| PROVENANCE_ONLY | worker/device identity (copy), adapter version (only when it does not change executable semantic lowering), attempt ids, failure reasons, input/output pin copies |

## 28. #22/#23 Boundary Matrix (final terminology)

#22: ProviderExecutionContract, ProviderCapabilityProfile, ProviderProbePort,
Worker, Device, ProviderBoundExecutableTaskGraph, ExecutableTaskId,
ExecutableTaskGraphDigest, ExecutionAssignment, ExecutionAttempt, local
dispatch, retry, lease/heartbeat primitive, sandbox, PlanLowerer,
RuntimeAdapter, CompatibilityKernel, ProviderCompatibilityGraph, FAOF-2 POC,
FAOF-3 bounded start.
#23: distributed worker fleet, global scheduling, global optimization,
cross-region placement, fleet-wide cost optimization, global locality
optimization, large-scale solver integration.

## 29. Product/Infrastructure Authority Matrix

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

## 30. Module Boundary Final Freeze

Do NOT create a giant runtime-module. TWO core modules:

### 30.1 media-execution-plan-module
OWNS the immutable provider/executable binding layer:
- ProviderId, ProviderImplementationId, ProviderVersion
- ProviderDescriptor, ProviderExecutionContract, ProviderCapabilityProfile
- CompatibilityKernel, CompatibilityDecision, static compatibility failure algebra
- ProviderCompatibilityGraph
- ProviderBoundExecutableTaskGraph, ExecutableTaskGraphId, ExecutableTaskGraphDigest, ExecutableTaskId
- BoundaryAction
- PlanLowerer
- Pure canonical/deterministic helpers required by the above

This module MUST remain free from mutable Worker/Device/Probe/Attempt state.

### 30.2 worker-fabric-module (CREATE during implementation — ONE minimal new bounded module)
OWNS the mutable/runtime execution layer:
- ProviderProbePort, ProviderProbeResult
- WorkerId, WorkerDescriptor, WorkerRuntimeState
- DeviceId, DeviceDescriptor, DeviceRuntimeState
- RuntimeEligibilityEvaluator, RuntimeEligibilityDecision, runtime eligibility failure algebra
- RuntimeEligibleCandidateView
- ExecutionAssignment, ExecutionAssignmentId
- ExecutionAttempt, ExecutionAttemptId, runtime attempt failure algebra
- bounded local dispatch, retry, lease, heartbeat, cancellation
- runtime sandbox/isolation coordination
- RuntimeAdapter

No #23 global scheduler/optimizer.

### 30.3 Dependency direction (frozen)

```
render-module / #20
        ↓
media-execution-plan-module
        ↓
worker-fabric-module
        ↓
concrete provider/worker adapters/apps
```
- The logical architecture arrow means downstream consumption.
- Compile/module dependency MUST preserve: worker-fabric-module DEPENDS ON
  media-execution-plan-module — NOT the reverse.
- media-execution-plan-module → worker-fabric-module is FORBIDDEN (immutable
  execution binding must not depend on mutable runtime state).
- ProviderExecutionContract (in media-execution-plan-module) MUST NOT import
  or reference worker-fabric RuntimeAdapter Java types (guard:
  PROVIDER_EXECUTION_CONTRACT_IMPORTS_WORKER_FABRIC_COUNT=0). It may define
  immutable provider runtime compatibility/version requirements; RuntimeAdapter
  (worker-fabric-module) consumes/conforms to those immutable requirements.
- Concrete provider adapters may depend on media-execution-plan-module,
  worker-fabric-module, Artifact/Storage ports, required infrastructure ports.
- Concrete provider adapters must NOT become dependencies of either core
  module.
- Static CompatibilityKernel cannot import mutable probe/runtime types
  (mechanically enforced).

## 31. Decision Table (resolved)

| QUESTION | OPTIONS | REPO EVIDENCE | TRADEOFFS | RECOMMENDED (FROZEN) | ESCALATION |
|---|---|---|---|---|---|
| Infrastructure boundary representation | A typed executable task kinds / B typed boundary actions on primary tasks | No existing #22 task model; outbox PlatformTask is flat task taxonomy | A parallel taxonomy risks two authorities; B keeps one-task-per-unit law | B (frozen, LAW_R22_017) | NO |
| ExecutableTaskGraph binding in digest | bound (includes provider binding) vs unbound | PhysicalExecutionPlan is provider-neutral | bound graph is concrete executable artifact; unbound is semantic-only | PROVIDER_BOUND / WORKER_UNBOUND / DEVICE_UNBOUND (frozen, §10) | NO |
| CapabilityImplementationId digest authority | default in upstream digest vs binding-only | #16 registry; #20/#21 provider-neutral requirements | concrete implementation selection is not upstream semantic authority | NOT in #20/#21 upstream digest by default; ETG binding participation only (frozen, §5.2, LAW_R22_018) | NO |
| ExecutableTaskId derivation | runtime-generated vs deterministic frozen-input | ETG digest needs stable constituent | runtime ids break digest determinism | Deterministic from PhysicalPlanUnit + ProviderBindingPin (frozen, §11, LAW_R22_019/020) | NO |
| Legacy RenderProvider fate | migrate/delete | god-interface + string capability + deprecated javacv | CLEAN FORWARD vs migration cost | MIGRATE_REDESIGN core interface; DELETE_SHADOW javacv deprecated | NO (implementation phase) |
| ExecutionProvider fate | delete/replace | FROZEN sealed Stub, "out of scope V1" | #22 owns provider contract; Stub is placeholder | DELETE_SHADOW → replace with #22 Provider SPI | NO |
| Worker registry reuse | adopt remote-render-worker registry vs new bounded worker-fabric | WorkerRegistryService exists (in-memory, job-scoped) | reuse mechanics vs clean #22 model | REUSE_MECHANICS_ONLY (patterns), new #22 typed model | NO |
| Outbox PlatformTask reuse | adopt lease/attempt semantics vs #22 own | PlatformTask has lease/attempt/retry already | outbox is event coordination, not runtime dispatch | REUSE_MECHANICS_ONLY for patterns; no authority merge | NO |
| Module placement | extend media-execution-plan-module vs new runtime-module | no runtime-module exists; execution-plan-module owns PhysicalExecutionPlan | avoid module proliferation vs clear immutable/mutable separation | FROZEN: media-execution-plan-module (immutable binding) + worker-fabric-module (mutable runtime), one minimal new module (§30) | NO |

## 32. Zero Guard Plan (implementation phase)

Original guards:
PHYSICAL_PLAN_PROVIDER_BINDING_COUNT=0, PHYSICAL_PLAN_WORKER_BINDING_COUNT=0, PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0, UPSTREAM_DIGEST_MUTABLE_RUNTIME_FIELD_COUNT=0, PROVIDER_SPECIFIC_COMMAND_CANONICAL_AUTHORITY_COUNT=0, STRINGLY_TYPED_PROVIDER_CAPABILITY_AUTHORITY_COUNT=0, UNKNOWN_COMPATIBILITY_ACCEPTANCE_COUNT=0, INCOMPATIBLE_PROVIDER_SELECTION_COUNT=0, CROSS_PROVIDER_IMPLICIT_MEMORY_BOUNDARY_COUNT=0, TASK_RETRY_CREATES_NEW_SEMANTIC_TASK_COUNT=0, PROVIDER_PROBE_CANONICAL_AUTHORITY_COUNT=0, ROADMAP_23_GLOBAL_OPTIMIZER_COUNT=0, KUBERNETES_DOMAIN_AUTHORITY_COUNT=0, COMPATIBILITY_WRAPPER_COUNT=0.

C1 guards:
PROVIDER_ID_AS_CAPABILITY_ID_AUTHORITY_COUNT=0, PROVIDER_CAPABILITY_PROFILE_CAPABILITY_REGISTRY_AUTHORITY_COUNT=0, PROVIDER_IMPLEMENTATION_REPLACES_CAPABILITY_IMPLEMENTATION_COUNT=0, COMPATIBILITY_KERNEL_MUTABLE_RUNTIME_READ_COUNT=0, PROVIDER_COMPATIBILITY_GRAPH_RUNTIME_STATE_FIELD_COUNT=0, ETG_WORKER_ID_SEMANTIC_FIELD_COUNT=0, ETG_DEVICE_ID_SEMANTIC_FIELD_COUNT=0, ETG_MUTABLE_PROBE_FIELD_COUNT=0, BOUNDARY_ACTION_INDEPENDENT_TASK_AUTHORITY_COUNT=0.

C2 guards:
STALE_R22_PROVIDER_CONTRACT_AUTHORITY_COUNT=0, STALE_BARE_R22_CAPABILITY_PROFILE_AUTHORITY_COUNT=0, CAPABILITY_IMPLEMENTATION_IN_UPSTREAM_PROVIDER_NEUTRAL_DIGEST_COUNT=0, NONDETERMINISTIC_EXECUTABLE_TASK_ID_SOURCE_COUNT=0, EXECUTABLE_TASK_ID_WORKER_DEVICE_DEPENDENCY_COUNT=0, IMMUTABLE_EXECUTION_BINDING_IMPORTS_MUTABLE_WORKER_FABRIC_COUNT=0, MEDIA_EXECUTION_PLAN_MODULE_MUTABLE_RUNTIME_STATE_COUNT=0, WORKER_FABRIC_GLOBAL_OPTIMIZER_COUNT=0.

C3 guards (contract search guards):
SEMANTIC_CAPABILITY_IDENTITY_YES_FOR_CAPABILITY_IMPLEMENTATION_ID_COUNT=0, PROVIDER_BINDING_PIN_DEFINITION_COUNT=1, INCOMPLETE_PROVIDER_BINDING_PIN_DEFINITION_COUNT=0, PROVIDER_VERSION_PROVENANCE_ONLY_COUNT=0, INPUT_ARTIFACT_PIN_PROVENANCE_ONLY_COUNT=0, INPUT_OUTPUT_ARTIFACT_PIN_UNDIFFERENTIATED_COUNT=0, INFORMAL_LEDGER_PATH_ELLIPSIS_COUNT=0, INFORMAL_LEDGER_PATH_PLUS_TESTS_COUNT=0, UNDECLARED_LEDGER_DOUBLE_STAR_COUNT=0, LEGACY_TARGET_AUTHORITY_COUNT=0, PROVIDER_EXECUTION_CONTRACT_IMPORTS_WORKER_FABRIC_COUNT=0 (implementation guard-plan level).

All previous zero-guard plans retained.

## 33. Infrastructure Activation Matrix (NOT NOW)

#22 EARLY: local PostgreSQL/Testcontainers, Z8 CPU provider, local filesystem Artifact backend, optional RustFS test instance, pve-2 Intel media worker when runtime begins.
#22 MID: R2 conformance, observability, provider probe integration.
#22 LATE: AWS NVIDIA ephemeral worker, Blender GPU, NIM/provider conformance.
Redis: only if concrete cache/lease/probe consumer exists. Novu/n8n: external ecosystem later, not core prerequisite. KUBERNETES=DEFER.

## 34. Implementation Phase Plan (frozen proposal, as amended)

Revised by Amendment 1 §25 then Amendment 2 §36 (PHASE_0..24):
CLEAN_FORWARD legacy provider/runtime/queue shadows → Provider immutable
identity/contract/profile/binding → PhysicalHost/WorkerRuntime/Device
foundations → Capacity/Reservation/Observed primitives →
CompatibilityKernel/ProviderCompatibilityGraph → Provider-local composition +
ExecutableTaskMembership → ProviderBoundExecutableTaskGraph + IDs/digest →
HostResourceAgent + runtime eligibility → ExecutionBackend +
ExecutionBackendSelection contracts → Native Pull RequestWork +
registration/freshness/idempotency → CentralWorkMatcher bounded matching →
ExecutionAssignment + Reservation + TaskLease + ExecutionAttempt atomic grant
→ TaskLease fencing/heartbeat/expiry/disconnect recovery → LocalAdmission +
typed decline + reservation reconciliation → retry/cancellation/late
completion/duplicate-execution fencing → PlanLowerer/RuntimeAdapter → Artifact
staging/materialization + fenced output completion → sandbox/isolation →
FAOF-2 Lean4 + Coq → FFmpeg CPU Native Pull conformance → Intel VAAPI/QSV
Native Pull conformance → OpenCue specialized farm backend bounded POC →
RemoteProvider backend conformance → NVIDIA/cloud Native Pull worker
conformance → candidate freeze / FCV / independent review.
Do NOT start these phases yet.

## 35. Parallel Task DAG (future, not now)

BOUNDED_PARALLEL_CODEX_EXECUTION_V1=ADOPTED; ONE_CODEX_EXECUTOR_ONE_ISOLATED_WORKTREE_V1; HERMES_OWNS_TASK_DAG_AND_CONVERGENCE_V1; PARALLEL_IMPLEMENTATION_DOES_NOT_CREATE_PARALLEL_ARCHITECTURE_AUTHORITIES_V1.
Initial target 2-3 sessions after contract freeze. Candidate lanes: A Provider identity/Descriptor/ProviderExecutionContract/ProviderCapabilityProfile, B Worker/Device model (worker-fabric-module foundation), C FAOF-2 Lean4 POC — parallel if WRITE_SET_DISJOINT, AUTHORITY_SET_NON_CONFLICTING, NO_UNRESOLVED_API_DEPENDENCY, SAME_FROZEN_BASE. CompatibilityKernel/convergence work follows foundations.

## 36. Model Routing Recommendations (future implementation)

Architecture-sensitive: gpt-5.6-sol reasoning=high. Balanced: gpt-5.6-terra high. Mechanical: gpt-5.6-terra medium/high or gpt-5.6-luna medium where justified. Fresh candidate review: codex-auto-review high. Adversarial review: gpt-5.6-sol high/xhigh with explicit reason. No silent fallback.

## 37. Escalation Conditions (monitored, none triggered)

A. #21 provider-neutral plan cannot support binding without redesign → NOT OBSERVED (PhysicalExecutionPlan is neutral; binding is #22-side).
B. Artifact authority cannot represent cross-provider materialization → NOT OBSERVED (Artifact immutable data plane suffices).
C. Provider compatibility conflicts with frozen Capability model → NOT OBSERVED (visual EffectCapabilityProfile is visual-domain; #22 ProviderCapabilityProfile is execution-domain; no conflict identified).
D. Worker/device requires modifying Media/Timeline/Render authorities → NOT OBSERVED.
E. Persisted/external compatibility proves CLEAN FORWARD deletion unsafe → NOT OBSERVED (no shipped #22 surface exists).
F. #22 cannot stay bounded/local → NOT OBSERVED.
G. Formalization reveals contradiction in laws → NOT OBSERVED (laws are consistent at DR level).

## 38. Final Decision Recovery Status (as amended)

ROADMAP_22_DECISION_RECOVERY_CORRECTION_4=PASS (docs-only, ledger machine closure)
ROADMAP_22_AMENDMENT_1=PASS (docs-only, shared worker fabric + provider-local composition)
ROADMAP_22_AMENDMENT_2=PASS (docs-only, Native Pull lease assignment + ExecutionBackend role V2)
ROADMAP_22_DECISION_RECOVERY=PASS (as corrected and amended)
READY_FOR_CHATGPT_FINAL_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
ROADMAP_23=NOT_STARTED
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE

NEXT_ACTION=CHATGPT_ROADMAP_22_DECISION_RECOVERY_AMENDMENT_2_FINAL_REVIEW
