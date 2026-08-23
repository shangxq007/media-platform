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

### 5.2 Provider identity
- ProviderId — semantic capability identity (stable, typed).
- ProviderImplementationId — replaceable implementation identity.
- ProviderVersion — implementation version.
- One semantic capability may have multiple providers/implementations/versions.
- ProviderDescriptor — declared static provider metadata (contract, version, declared capability profile reference).
- ProviderContract — the authority contract a provider implementation commits to.
- CapabilityProfile — typed declared executable support (NOT Map<String,String>; bounded typed extension points: supported capabilities, input/output artifact kinds, codecs, pixel/sample constraints, execution requirements, device class requirements, determinism properties, sandbox requirements).

### 5.3 ProviderProbePort
- DECLARED_PROVIDER_CAPABILITY vs OBSERVED_RUNTIME_AVAILABILITY separation.
- Probe results: mutable, time-sensitive, ephemeral. Never enter Timeline content hash / RenderPlan fingerprint / LogicalExecutionGraph digest / PhysicalExecutionPlan digest.

## 6. Two-Stage Feasibility (frozen)

- Stage 1 SEMANTIC/TECHNICAL COMPATIBILITY: can this provider implementation execute this PhysicalUnit in principle? Deterministic over frozen inputs (PhysicalPlanUnit, ExecutionRequirement, ProviderContract, ProviderDescriptor, CapabilityProfile, artifact compatibility). Independent of queue depth/utilization/heartbeat age/free memory/worker load.
- Stage 2 RUNTIME ELIGIBILITY: can a current worker/device execute it now? Inputs: WorkerDescriptor, DeviceDescriptor, probe snapshot, availability snapshot, sandbox readiness, runtime health. Mutable; outside canonical digests.

## 7. Constraint Kernel (frozen)

Purpose: answer feasibility, not global optimization.
Pipeline: PhysicalExecutionPlan → ExecutionRequirement → Provider Candidates → Constraint Kernel → Compatibility Results → Feasible Provider Compatibility Graph.

Typed explanations (at minimum):
CAPABILITY_UNSUPPORTED, INPUT_ARTIFACT_INCOMPATIBLE, OUTPUT_ARTIFACT_INCOMPATIBLE, CODEC_UNSUPPORTED, DEVICE_CLASS_UNAVAILABLE, RUNTIME_VERSION_INCOMPATIBLE, SANDBOX_REQUIREMENT_UNSATISFIED, DETERMINISM_REQUIREMENT_UNSATISFIED, CROSS_PROVIDER_BOUNDARY_UNAVAILABLE, PROBE_UNKNOWN, RUNTIME_UNAVAILABLE.

No generic "provider unavailable" as the only failure model.

## 8. Provider Compatibility Graph (frozen)

Bounded typed model: PhysicalPlanUnit → feasible ProviderImplementation candidate set + feasible transitions across provider boundaries.
Edge result: DIRECT_COMPATIBLE | ARTIFACT_MATERIALIZATION_REQUIRED | INCOMPATIBLE | UNKNOWN_FAIL_CLOSED.
No optimization across INCOMPATIBLE edges. No universal interoperability claim.

## 9. ExecutableTaskGraph V1 (frozen)

- ONE PhysicalPlanUnit → ONE PRIMARY executable work task in V1 (LAW_R22_009). No semantic fusion, no arbitrary N:M decomposition.
- Infrastructure boundary representation DECISION (from §14 options A/B):
  **B. TYPED BOUNDARY ACTIONS ATTACHED TO PRIMARY TASKS** — artifact materialization, artifact transfer, sandbox preparation, runtime staging are typed boundary actions on the primary task (single authority, no parallel task-kind authority). Rationale: avoids a parallel task taxonomy, keeps one-primary-task-per-unit law clean, and infrastructure actions have no PhysicalPlanUnit identity of their own.
- ExecutableTaskId / ExecutableTaskGraphId / ExecutableTaskGraphDigest: business identity != semantic digest; runtime attempt identity != task identity; retry creates new attempt, never new task.

## 10. Provider Binding / PlanLowerer / RuntimeAdapter (frozen)

- CapabilityRequirement ≠ ProviderImplementation. PhysicalPlanUnit says WHAT; executable binding says WHICH. No provider name in canonical #20/#21 semantics.
- PlanLowerer: typed PhysicalPlanUnit/ExecutableTask → provider-specific executable specification. MUST NOT redefine semantic meaning; fails closed if it cannot represent requested semantics. FFmpeg/Blender/CUDA command lines never canonical semantic authority (LAW_R22_010).
- RuntimeAdapter: HOW a request is launched/monitored/cancelled (separate from PlanLowerer's WHAT). Same provider semantics + different runtime environments (FFmpeg local / container / remote worker) without redefining capability semantics.

## 11. Worker / Device (frozen)

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

## 15. Lease / Heartbeat / Cancellation (frozen)

- Lease, heartbeat, lease expiry, worker loss are runtime mechanics; never canonical media state / RenderPlan state / PhysicalExecutionPlan semantics. #22 establishes the local/bounded primitive contract; distributed fleet coordination is #23.
- Cancellation distinguishes cancel task attempt vs delete canonical media result. Cancellation never rewrites historical media state; explicit semantics when a completed immutable Artifact exists.

## 16. Runtime Probe Cache (frozen)

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

## 18. Failure Algebra (frozen)

Distinguish at minimum: PLANNING_INPUT_INVALID, NO_COMPATIBLE_PROVIDER, PROVIDER_CONTRACT_MISMATCH, ARTIFACT_BOUNDARY_INCOMPATIBLE, PROBE_FAILED, PROBE_STALE, NO_ELIGIBLE_WORKER, NO_ELIGIBLE_DEVICE, LOWERING_UNSUPPORTED, RUNTIME_START_FAILED, RUNTIME_EXECUTION_FAILED, RUNTIME_TIMEOUT, LEASE_LOST, WORKER_LOST, DEVICE_LOST, ARTIFACT_MATERIALIZATION_FAILED, CANCELLED.
Semantic incompatibility and transient runtime failure MUST be separate. UNKNOWN fails closed everywhere (UNKNOWN_COMPATIBILITY≠COMPATIBLE, UNKNOWN_PROVIDER_SUPPORT≠SUPPORTED, UNKNOWN_RUNTIME_AVAILABILITY≠AVAILABLE).

## 19. Determinism / Resource / ExecutionRequirement authority (frozen)

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

## 22. Identity/Digest Matrix

| Identity | BUSINESS_IDENTITY | SEMANTIC_DIGEST | RUNTIME_ID | STABLE | MUTABLE | PERSISTED | INCLUDED_IN | EXCLUDED_FROM |
|---|---|---|---|---|---|---|---|---|
| ProviderId | YES | NO | NO | YES | NO | YES | executable graph (as binding, if bound graph) | #20/#21 digests |
| ProviderImplementationId | YES | NO | NO | YES | NO | YES | executable binding | #20/#21 digests |
| WorkerId | YES | NO | runtime | YES | NO | YES | runtime/provenance | upstream semantic digests |
| DeviceId | YES | NO | runtime | YES | NO | YES | runtime/provenance | upstream semantic digests |
| ExecutableTaskGraphId | YES | NO | NO | YES | NO | YES | — | digest separate |
| ExecutableTaskGraphDigest | NO | YES | NO | YES | NO | YES | executable graph semantics (excluding mutable runtime fields; MAY include provider binding if graph defined as bound executable artifact — explicit contract decision) | queue/health/lease/heartbeat/metrics |
| ExecutableTaskId | YES | NO | NO | YES | NO | YES | graph digest as constituent | — |
| ExecutionAttemptId | YES | NO | runtime | NO | NO | YES | provenance only | task identity, semantic digests |
| LeaseId | YES | NO | runtime | NO | YES | transient | none | all semantic digests |

## 23. Mutability Matrix

| Class | Types |
|---|---|
| IMMUTABLE_SEMANTIC | #20 RenderPlan/RenderGraph/RenderNode/RenderDependency semantics, #21 ExecutionRequirement/LogicalExecutionGraph/PhysicalExecutionPlan + digests, Artifact content pin |
| IMMUTABLE_EXECUTABLE_BINDING | ProviderContract/Descriptor, CapabilityProfile, bound ExecutableTaskGraph identity components, PlanLowerer output spec |
| MUTABLE_RUNTIME_STATE | WorkerRuntimeState, DeviceRuntimeState, lease, heartbeat, availability, probe snapshot, attempt lifecycle, retry counter |
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

PHYSICAL_PLAN_PROVIDER_BINDING_COUNT=0, PHYSICAL_PLAN_WORKER_BINDING_COUNT=0, PHYSICAL_PLAN_DEVICE_BINDING_COUNT=0, UPSTREAM_DIGEST_MUTABLE_RUNTIME_FIELD_COUNT=0, PROVIDER_SPECIFIC_COMMAND_CANONICAL_AUTHORITY_COUNT=0, STRINGLY_TYPED_PROVIDER_CAPABILITY_AUTHORITY_COUNT=0, UNKNOWN_COMPATIBILITY_ACCEPTANCE_COUNT=0, INCOMPATIBLE_PROVIDER_SELECTION_COUNT=0, CROSS_PROVIDER_IMPLICIT_MEMORY_BOUNDARY_COUNT=0, TASK_RETRY_CREATES_NEW_SEMANTIC_TASK_COUNT=0, PROVIDER_PROBE_CANONICAL_AUTHORITY_COUNT=0, ROADMAP_23_GLOBAL_OPTIMIZER_COUNT=0, KUBERNETES_DOMAIN_AUTHORITY_COUNT=0, COMPATIBILITY_WRAPPER_COUNT=0.

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

## 34. Final Decision Recovery Status

ROADMAP_22_DECISION_RECOVERY=PASS (draft, pending ChatGPT review)
READY_FOR_CHATGPT_ROADMAP_22_DECISION_RECOVERY_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
ROADMAP_23=NOT_STARTED
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE

NEXT_ACTION=CHATGPT_ROADMAP_22_DECISION_RECOVERY_REVIEW
