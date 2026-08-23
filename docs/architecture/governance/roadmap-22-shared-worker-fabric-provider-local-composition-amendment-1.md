# Roadmap #22 Shared Worker Fabric and Provider-Local Composition — Amendment 1

STATUS=FROZEN_AMENDMENT (docs-only; supersedes conflicting DR clauses)
ROADMAP_22_IMPLEMENTATION=NO_GO
SUPERSEDES=ONE_PHYSICAL_PLAN_UNIT_ONE_EXECUTABLE_TASK_V1 (strict 1:1)
RETIRED_LAWS=LAW_R22_009 (strict 1:1 cardinality — see §3)

## 1. Amendment Basis

A newer authoritative handoff (SHARED_WORKER_FABRIC_V1) supersedes older #22
assumptions. It is architecture input. ROADMAP_21_REOPEN=NO;
ROADMAP_22_RESTART_FROM_ZERO=NO; amendment stays entirely within #22;
ARCHITECTURE_ESCALATION=NONE (no CLOSED upstream authority requires change).

## 2. Governance State

ROADMAP_22_DECISION_RECOVERY_AMENDMENT_REQUIRED=YES (satisfied by this document)
ARCHITECTURE_RECONCILIATION_REQUIRED=YES (satisfied)
ROADMAP_21_REOPEN=NO
ROADMAP_22_IMPLEMENTATION=NO_GO (until ChatGPT returns ROADMAP_22_BOUNDED_IMPLEMENTATION=GO)

## 3. Superseded Cardinality (explicit)

OLD: ONE PhysicalPlanUnit → ONE PRIMARY ExecutableTask (LAW_R22_009, strict 1:1).
NEW: MULTIPLE_PHYSICAL_UNITS_TO_ONE_EXECUTABLE_TASK=POTENTIALLY_YES when
provider-local execution coalescing is legal.

LAW_R22_009 is SUPERSEDED by this amendment. It is NOT retained in parallel
as a V1 contract. Its replacement is the membership model (§4) + coalescing
legality (§6). No compatibility alias for the docs-only old contract.

## 4. Physical Unit Membership Model (frozen)

- EVERY PhysicalPlanUnit → EXACTLY ONE ExecutableTaskMembership
  (LAW_R22_025)
- EVERY ExecutableTask → ONE_OR_MORE PhysicalPlanUnits
  (LAW_R22_026: multiple memberships ONLY when provider-local composition is
  proven)
- ExecutableTaskMembership retains attribution to original PhysicalPlanUnit:
  PhysicalPlanUnit identity/reference, membership identity, provider-local
  ordering (canonical), input mapping, output mapping, dependency mapping,
  failure attribution, mandatory materialization markers.
- No ambiguous direct N:M ownership.

## 5. Executable Task Identity (frozen, revised for multi-unit)

Deterministic identity retained (LAW_R22_019/020). Based ONLY on immutable
execution semantics: membership set (canonical order) + ProviderBindingPin.
Membership ordering MUST be canonical; permutation of nonsemantic input
ordering does not change task identity. Forbidden inputs unchanged: WorkerId,
DeviceId, host assignment, runtime attempt, lease, heartbeat, queue state,
clock, random UUID, traversal order, observed telemetry.

## 6. Provider-Local Composition Model (frozen)

PROVIDER_LOCAL_COMPOSITION_AND_EXECUTION_COALESCING_V1.

Provider-local execution coalescing is an EXECUTION LOWERING optimization:
- NOT semantic fusion, NOT RenderGraph/LogicalExecutionGraph/PhysicalExecutionPlan
  rewrite, NOT FAOF-4 semantic transformation (LAW_R22_027).
- #21 PhysicalExecutionPlan remains UNCHANGED.
- GENERAL_SEMANTIC_FUSION=DEFER_TO_FAOF_4.

### ProviderLocalCompositionEvaluator (frozen)

Typed static feasibility step (LAW_R22_030):
- Input: ordered/set PhysicalPlanUnit candidates, ProviderBindingPin,
  ProviderCapabilityProfile, ProviderExecutionContract, static Artifact
  boundary requirements, required isolation/device semantics.
- Output: COALESCING_ALLOWED | COALESCING_FORBIDDEN | UNKNOWN_FAIL_CLOSED,
  with typed reasons.
- Same ProviderImplementation does NOT imply coalescing is legal.
- Coalescing blockers (at minimum): MANDATORY_INTERMEDIATE_ARTIFACT,
  PROVIDER_NATIVE_PIPELINE_UNSUPPORTED, DEVICE_CONTEXT_INCOMPATIBLE,
  SOFTWARE_HARDWARE_FRAME_BOUNDARY, SANDBOX_OR_TRUST_BOUNDARY,
  BRANCHING_SEMANTIC_BOUNDARY, RETRY_OR_FAILURE_ISOLATION_BOUNDARY,
  DETERMINISM_INCOMPATIBLE, INPUT_OUTPUT_CONTRACT_INCOMPATIBLE,
  UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS.
- MANDATORY_ARTIFACT_BOUNDARY_BLOCKS_COALESCING (LAW_R22_028): coalescing
  across a mandatory immutable Artifact boundary is FORBIDDEN unless a future
  proven FAOF-4 semantic optimization changes the upstream plan (NOT #22).

### PlanLowerer composition contract

- ProviderLocalCompositionEvaluator answers: "Can these units legally share one
  provider-native execution?"
- PlanLowerer answers: "How are the accepted memberships lowered into the
  provider-native request?"
- PlanLowerer is NOT the sole hidden feasibility authority. No provider-
  specific command string defines legality (LAW_R22_010 retained).

### Failure attribution (LAW_R22_029)

Coalescing preserves attribution to original PhysicalPlanUnits. A runtime
failure of one provider-native process covering P1/P2/P3 MUST be explainable
in terms of the affected ExecutableTask and one or more PhysicalPlanUnit
memberships. Where exact stage attribution is impossible: report typed
UNKNOWN_MEMBER_ATTRIBUTION. Never invent attribution.

### Provider upgrades

Composition feasibility may change when ProviderVersion /
ProviderExecutionContractVersion / ProviderCapabilityProfileVersionOrDigest
changes. This MUST NOT mutate CapabilityId, Capability contract semantics,
PhysicalExecutionPlan, or historical ETG. Historical ETGs remain pinned.

## 7. Physical Host / Worker Runtime Separation (frozen)

Distinct identities, never collapsed:
- PhysicalHostId — resource capacity, device ownership, trust-zone context,
  network/locality context, host lifecycle. NOT provider identity, NOT
  capability identity, NOT one WorkerRuntime (LAW_R22_031).
- WorkerRuntimeId — one executable runtime endpoint (local/remote/bounded
  backend). Independent of PhysicalHostId (LAW_R22_032); absent host for
  remote SaaS.
- DeviceId — device (CPU/GPU/MEDIA_ACCELERATOR/OTHER_ACCELERATOR).
- ProviderImplementationId — provider runtime/adapter implementation.

Frozen shapes:
```
PhysicalHostDescriptor { PhysicalHostId; static host characteristics;
  DeviceDescriptor references; trust zone; supported isolation environments }
WorkerRuntimeDescriptor { WorkerRuntimeId; PhysicalHostId?; ProviderImplementationId;
  RuntimeLifecycleKind; IsolationProfile }
```
One PhysicalHost may host MANY WorkerRuntimes. One WorkerRuntime may expose
one or more compatible provider-runtime capabilities where contract permits.
Do NOT assume WorkerRuntime == PhysicalHost. No giant universal object.

### Runtime lifecycle kind (frozen)

- EPHEMERAL_TASK (FFmpeg process, Blender batch, OpenCV batch)
- RESIDENT_RUNTIME (persistent model server, render daemon)
- REMOTE_RUNTIME (SaaS API)
- REUSABLE_SESSION: DEFERRED (no real semantic/runtime distinction remains
  after repository inspection; adopt only if explicit provider/session
  semantics justify it later)

## 8. Shared Worker Fabric (frozen)

SHARED_WORKER_FABRIC_V1:
- PHYSICAL_WORKERS_ARE_NOT_PERMANENTLY_PARTITIONED_BY_PRODUCT_FUNCTION_V1
- WORKER_GROUPING_IS_BY_RESOURCE_CAPABILITY_AND_ISOLATION_REQUIREMENT_V1
- MULTI_CAPABILITY_WORKER_IS_NORMAL_V1
- MULTI_PROVIDER_WORKER_IS_ALLOWED_WHEN_RUNTIME_ISOLATION_ALLOWS_V1
- RESOURCE_POOLS_ARE_LOGICAL_SCHEDULING_CONSTRUCTS_V1
- DEDICATED_WORKER_POOLS_ARE_POLICY_OPTIMIZATIONS_NOT_DOMAIN_ARCHITECTURE_V1
- Do NOT create canonical RenderWorker/TranscodeWorker/BlenderWorker as
  mutually exclusive physical machine classes.

### Resource pools (frozen)

Logical scheduling labels/views: CPU_GENERAL, CPU_HEAVY, HIGH_MEMORY,
INTEL_MEDIA, NVIDIA_GPU, HIGH_VRAM, PRIVATE_TRUST_ZONE, INTERACTIVE, BATCH.
One PhysicalHost may participate in multiple pools. Pool membership does not
redefine host identity.

## 9. Resource Truth Model (frozen)

Three distinct resource authorities, never collapsed (LAW_R22_033/034):

1. **CAPACITY** — static/fingerprinted: CPU, RAM, temporary storage, network,
   GPU, VRAM, encoder/decoder/video engines, device features.
2. **RESERVED** — scheduler/runtime committed: active task reservations,
   resident runtime reservations, device reservations, safety headroom.
3. **OBSERVED** — telemetry: CPU/RAM/GPU/VRAM utilization, video engine
   utilization, disk IO, network IO, temperature, power.

SCHEDULABLE_CAPACITY = STATIC_CAPACITY - ACTIVE_RESERVATIONS -
RESIDENT_RESERVATIONS - SAFETY_HEADROOM. Observed telemetry does NOT replace
this calculation.

RESERVATION_FIRST_TELEMETRY_SECOND_V1: hard admission/feasibility uses
capacity minus reservations minus safety headroom. Telemetry initially used
for: health veto, overload veto, candidate scoring, anomaly detection,
capacity calibration. NOT "Prometheus says GPU 20% therefore 80% is free".

DEVICE_CAPACITY_IS_FIRST_CLASS_AND_NOT_INFERRED_FROM_CPU_LOAD_V1: preserve
architecture space for GPU (VRAM, compute/encoder/decoder/video engines);
do not design a CPU-only generic resource model that prevents typed device
accounting. First implementation need not include every dimension.

GPU sharing: EXCLUSIVE_DEVICE_RESERVATION early safe policy for Blender GPU,
large AI inference, Houdini GPU, other unknown high-risk GPU workloads.
SAFE_RESOURCE_ACCOUNTING_BEFORE_AGGRESSIVE_UTILIZATION_V1. More granular
sharing later.

## 10. Host Resource Agent Boundary (frozen)

HOST_RESOURCE_AGENT_BOUNDARY_V1: media-platform-owned HostResourceAgent may:
fingerprint host/device capacity, report WorkerRuntime availability, report
Provider runtime availability, track media-platform reservations, track
resident runtime reservations, associate running tasks, project standard
telemetry into typed snapshots, report runtime health. It does NOT replace
Prometheus/node_exporter/cAdvisor/DCGM/intel_gpu_top/OTel — those remain
observability mechanics.

HostResourceSnapshot (bounded composition, no god object):
```
HostResourceSnapshot { PhysicalHostId; CapacitySnapshot; ReservationSnapshot;
  ObservedUsageSnapshot; DeviceSnapshot[]; WorkerRuntimeSnapshot[];
  trust/locality metadata where relevant }
```

Observability sources (node_exporter, cAdvisor, DCGM Exporter, Intel GPU
telemetry, OTel) project into ObservedUsageSnapshot. They MUST NOT directly
mutate the reservation ledger. They MUST NOT define Capability or Provider
support.

## 11. Local vs Remote Resource Feasibility (frozen)

- LocalResourceFeasibility uses host/device capacity + reservations.
- RemoteProviderFeasibility uses quota, concurrency, rate limit, region,
  remote availability, policy, budget/cost constraints where applicable.
- Shared concept: admission / reservation / availability. Different concrete
  resource dimensions. NO UniversalResourceGodObject.

## 12. Reservation Model (frozen)

Bounded Reservation authority for #22: ReservationId; owner task/attempt/
runtime; host/runtime/device target; resource quantities/classes; reservation
lifecycle (active/released/expired); resident reservation; safety headroom
treatment. Reservation is runtime scheduling state — it does NOT enter
canonical media semantics. RESIDENT_RESERVATION_IS_FIRST_CLASS_V1: a resident
runtime may consume reserved capacity even when telemetry is low (persistent
model server reserves VRAM/RAM/GPU fraction/exclusive device/CPU); scheduler
must see reservation, never infer availability from low observed utilization.

## 13. Dispatch Backends and OpenCue (frozen)

OPEN_CUE_ROLE_V1=REPLACEABLE_DISTRIBUTED_FARM_SCHEDULING_AND_DISPATCH_BACKEND.
OpenCue MUST NOT own: Capability semantics, Provider feasibility, Constraint
Kernel, Artifact compatibility, semantic fusion, canonical Worker semantics,
canonical Device semantics, media-platform scheduling policy (LAW_R22_036).
Media-platform remains authority for: hard legality, resource semantics,
reservation semantics, Provider compatibility, trust/privacy, ExecutableTask
semantics.

Frozen peer backends:
```
DispatchBackend
├── LocalDispatchBackend
├── OpenCueFarmDispatchBackend
└── RemoteProviderDispatchBackend
```
Remote SaaS Provider calls MUST NOT be forced into fake physical
WorkerRuntime or fake OpenCue jobs when no such runtime exists
(LAW_R22_035: remote provider resource feasibility need not use physical
host model).

#22 owns: DispatchBackend abstraction, LocalDispatchBackend,
FarmDispatchBackendPort, RemoteProviderDispatchBackend, OpenCue adapter
contract, bounded OpenCue mapping/conformance POC if useful. #23 owns:
distributed placement policy, fleet-wide scheduling, global queue
optimization, global resource fragmentation optimization, cross-region
scheduling, global cost/latency optimization. OpenCue existence does NOT
authorize #22 to implement #23.

OPEN_CUE_ADAPTER_SIDECAR_FIRST_V1; OPEN_CUE_FORK=DEFER_UNTIL_CONCRETE_MISSING_MECHANICS_EVIDENCE.
Do not fork merely because media-platform has richer semantics; map through
adapters.

## 14. Reconciled #22 Pipeline (frozen, unambiguous)

```
PhysicalExecutionPlan
        ↓
Provider candidate discovery
        ↓
CompatibilityKernel
        ↓
ProviderCompatibilityGraph
        ↓
Provider-local composition feasibility (ProviderLocalCompositionEvaluator)
        ↓
Provider-bound ExecutableTask memberships/tasks
        ↓
RuntimeEligibilityEvaluator
        ↓
PhysicalHost / WorkerRuntime / Device feasibility
        ↓
reservation-aware bounded placement
        ↓
ExecutionAssignment
        ↓
DispatchBackend
        ↓
ExecutionAttempt
```
Exact order: static composition feasibility is evaluated BEFORE ETG
construction (it is fully static, independent of runtime/host state, §17);
RuntimeEligibility follows ETG construction. No circular authority.

## 15. ExecutionAssignment (revised)

```
ExecutionAssignment {
  ExecutableTaskId
  ProviderImplementationId
  WorkerRuntimeId
  PhysicalHostId?        // optional for remote
  DeviceId(s)
  ReservationId(s)
}
```
Immutable ETG vs mutable/runtime assignment remain distinct. No assignment
field enters ETG digest.

## 16. ETG Digest After Coalescing (frozen)

ProviderBoundExecutableTaskGraph remains PROVIDER_BOUND, WORKER_RUNTIME_UNBOUND,
PHYSICAL_HOST_UNBOUND, DEVICE_ASSIGNMENT_UNBOUND until runtime placement.
Digest preserves: provider binding (ProviderBindingPin), all physical-unit
memberships (canonical), dependency mapping, BoundaryActions, mandatory
Artifact boundaries, immutable input Artifact pins, provider-local composition
decision, all semantics needed to reproduce lowering. Digest excludes: host
assignment, WorkerRuntime assignment, Device assignment, reservation, probe,
telemetry, attempt, lease, heartbeat, queue, runtime timestamps.

## 17. Module Boundary (revised, no cycle)

Default split retained:
- media-execution-plan-module = immutable execution/provider binding layer.
  Owns: static ProviderLocalCompositionContract (fully static composition
  legality, independent of runtime/host state); ProviderLocalCompositionEvaluator
  static core; everything previously frozen there.
- worker-fabric-module = mutable runtime layer. Owns: PhysicalHost/Host
  resources, WorkerRuntime, Device runtime, Reservation, HostResourceAgent
  contracts, RuntimeEligibility, ExecutionAssignment, DispatchBackend ports,
  runtime lifecycle, runtime composition feasibility constraints (consumes
  the static contract).

STATIC_PROVIDER_LOCAL_COMPOSITION vs RUNTIME_DEVICE_FEASIBILITY remain
distinct (§52 of handoff): FFmpeg QSV may theoretically coalesce
Decode+Scale+Encode but current WorkerRuntime lacks QSV → STATIC_COMPOSITION=
ALLOWED, RUNTIME_ELIGIBILITY=INELIGIBLE. Static legality is never
contaminated by mutable device availability.

MODULE_DEPENDENCY_DIRECTION retained: worker-fabric-module DEPENDS ON
media-execution-plan-module; reverse forbidden. No dependency cycle.

## 18. #22/#23 Resource Boundary (retained)

#22 may own: typed capacity, typed reservation, bounded admission, bounded
local placement, runtime eligibility, dispatch. #22 MUST NOT own:
fleet-global optimizer, global fragmentation optimizer, global queue
balancing, multi-cluster placement, global cost optimizer (all #23).

## 19. Infrastructure as Validation Input (no mutation)

Existing environments are architecture test cases only; nothing deployed or
mutated by this docs-only amendment:
- Z8: CPU heavy, high memory, software media, Blender CPU, OpenCV CPU.
- pve-1: infrastructure / CI / ephemeral worker lab.
- pve-2: Intel DG1, VAAPI proven, QSV package-gap historically, media
  acceleration node.
- AWS NVIDIA: future ephemeral conformance.
PVE_HOSTS_REMAIN_HYPERVISOR_ONLY_V1; DEFAULT_PVE_GUEST_OS=DEBIAN_13;
PVE2_DG1_ACCESS=DRI_DEVICE_PASSTHROUGH_TO_LXC_FIRST. No new VM/LXC.

## 20. OpenCue Implementation Timing

#22 early: DispatchBackend abstraction + LocalDispatchBackend.
#22 mid/late: OpenCue adapter contract + bounded mapping/conformance POC.
#23: actual distributed/global scheduling policy integration.
Do not install/fork OpenCue during Decision Recovery.

## 21. Formal Laws (amendment)

Retained: LAW_R22_001..008, 010..024 (except 009 superseded below).
Superseded: LAW_R22_009 (strict 1:1) — replaced by membership model (§4) and
coalescing legality (§6).
Added:
- LAW_R22_025 EVERY_PHYSICAL_PLAN_UNIT_HAS_EXACTLY_ONE_EXECUTABLE_TASK_MEMBERSHIP
- LAW_R22_026 EXECUTABLE_TASK_MAY_CONTAIN_MULTIPLE_PHYSICAL_UNIT_MEMBERSHIPS_ONLY_WHEN_PROVIDER_LOCAL_COMPOSITION_IS_PROVEN
- LAW_R22_027 PROVIDER_LOCAL_COALESCING_NEVER_REWRITES_PHYSICAL_EXECUTION_PLAN
- LAW_R22_028 MANDATORY_ARTIFACT_BOUNDARY_BLOCKS_COALESCING
- LAW_R22_029 COALESCED_FAILURE_REMAINS_ATTRIBUTABLE_TO_ORIGINAL_PHYSICAL_UNIT_MEMBERSHIP_WHERE_PROVABLE
- LAW_R22_030 UNKNOWN_PROVIDER_LOCAL_COMPOSITION_FAILS_CLOSED
- LAW_R22_031 PHYSICAL_HOST_IDENTITY_IS_INDEPENDENT_OF_PROVIDER_IMPLEMENTATION
- LAW_R22_032 WORKER_RUNTIME_IDENTITY_IS_INDEPENDENT_OF_PHYSICAL_HOST_IDENTITY
- LAW_R22_033 SCHEDULABLE_CAPACITY_USES_RESERVATIONS_BEFORE_OBSERVED_UTILIZATION
- LAW_R22_034 OBSERVED_TELEMETRY_NEVER_CREATES_UNRESERVED_CAPACITY_AUTHORITY
- LAW_R22_035 REMOTE_PROVIDER_RESOURCE_FEASIBILITY_NEED_NOT_USE_PHYSICAL_HOST_MODEL
- LAW_R22_036 OPEN_CUE_NEVER_BECOMES_PLATFORM_SCHEDULING_SEMANTIC_AUTHORITY

## 22. Identity Matrix Amendment

New/recovered identities (module: worker-fabric-module unless noted):

| Identity | AUTHORITY | BUSINESS_IDENTITY | STABLE | MUTABLE | PERSISTED | ETG_DIGEST_PARTICIPATION | RUNTIME_ASSIGNMENT_PARTICIPATION | PROVENANCE_PARTICIPATION |
|---|---|---|---|---|---|---|---|---|
| PhysicalHostId | #22 worker fabric | YES | YES | NO | YES | NO | YES (assignment) | YES |
| WorkerRuntimeId | #22 worker fabric | YES | YES | NO | YES | NO | YES (assignment) | YES |
| ReservationId | #22 worker fabric | YES | NO | YES | transient | NO | YES (assignment) | YES |
| ResourcePoolId | #22 worker fabric | YES | YES | NO | YES | NO | NO (logical view) | optional |
| HostResourceSnapshotId | #22 worker fabric | YES | NO | YES | transient | NO | NO | optional (only if justified) |

WorkerRuntimeId is NOT overloaded with PhysicalHostId. Neither enters ETG
digest. Both participate in runtime assignment + provenance only.

## 23. Mutability Matrix Amendment

| Class | Types |
|---|---|
| IMMUTABLE_EXECUTABLE_BINDING | (unchanged from main contract: ProviderBindingPin, ProviderBoundExecutableTaskGraph, ExecutableTaskId, ProviderCompatibilityGraph, PlanLowerer output spec, immutable input Artifact pins) + static ProviderLocalCompositionContract |
| STATIC_HOST_CAPACITY | PhysicalHostDescriptor, DeviceDescriptor, CapacitySnapshot (fingerprinted) |
| MUTABLE_RESERVATION_STATE | Reservation ledger (active/released/expired), resident reservations, safety headroom accounting |
| MUTABLE_RUNTIME_AVAILABILITY | WorkerRuntimeState, DeviceRuntimeState, ProviderProbeResult, RuntimeEligibleCandidateView, HostResourceSnapshot observed parts |
| OBSERVED_TELEMETRY | ObservedUsageSnapshot (projection; never authority) |
| EXECUTION_ASSIGNMENT | ExecutionAssignment (runtime placement incl. WorkerRuntimeId/PhysicalHostId/DeviceId/ReservationId) |
| EXECUTION_ATTEMPT | ExecutionAttempt lifecycle |
| PROVENANCE | attempt ids, failure reasons, worker/host/device identity copies, adapter version (only when semantically inert) |

Upstream canonical semantics stay isolated.

## 24. Zero Guard Plan — Amendment (future implementation)

STRICT_ONE_PHYSICAL_UNIT_ONE_EXECUTABLE_TASK_AUTHORITY_COUNT=0,
EXECUTABLE_TASK_MEMBERSHIP_MISSING_PHYSICAL_UNIT_COUNT=0,
COALESCING_REWRITES_PHYSICAL_PLAN_COUNT=0,
COALESCING_CROSSES_MANDATORY_ARTIFACT_BOUNDARY_COUNT=0,
COALESCING_UNKNOWN_COMPATIBILITY_ACCEPTANCE_COUNT=0,
WORKER_RUNTIME_EQUALS_PHYSICAL_HOST_AUTHORITY_COUNT=0,
PROVIDER_IMPLEMENTATION_EQUALS_PHYSICAL_HOST_AUTHORITY_COUNT=0,
TELEMETRY_AS_CAPACITY_AUTHORITY_COUNT=0,
UNRESERVED_CAPACITY_FROM_UTILIZATION_COUNT=0,
REMOTE_PROVIDER_FORCED_PHYSICAL_HOST_COUNT=0,
OPEN_CUE_PLATFORM_SEMANTIC_AUTHORITY_COUNT=0,
OPEN_CUE_CONSTRAINT_KERNEL_AUTHORITY_COUNT=0,
OPEN_CUE_PROVIDER_FEASIBILITY_AUTHORITY_COUNT=0,
WORKER_FABRIC_GLOBAL_OPTIMIZER_COUNT=0.
All prior #22 zero-guard plans retained.

## 25. Revised Implementation Phase Plan (proposal; do NOT start)

PHASE_0 CLEAN_FORWARD legacy provider/runtime shadows
→ PHASE_1 immutable Provider contract/profile/binding foundations
→ PHASE_2 PhysicalHost/WorkerRuntime/Device identity + descriptor foundations
→ PHASE_3 resource capacity + reservation primitives
→ PHASE_4 CompatibilityKernel/ProviderCompatibilityGraph
→ PHASE_5 provider-local composition legality + membership model
→ PHASE_6 ProviderBoundExecutableTaskGraph + deterministic ids/digest
→ PHASE_7 RuntimeEligibility + HostResourceAgent ports/snapshots
→ PHASE_8 ExecutionAssignment + reservation-aware bounded placement
→ PHASE_9 PlanLowerer/RuntimeAdapter/DispatchBackend
→ PHASE_10 retry/attempt/lease/heartbeat/cancellation
→ PHASE_11 Artifact boundary + staging
→ PHASE_12 sandbox/isolation
→ PHASE_13 FAOF-2 Lean4 + Coq
→ PHASE_14 FFmpeg CPU conformance
→ PHASE_15 Intel VAAPI/QSV conformance
→ PHASE_16 OpenCue bounded adapter POC
→ PHASE_17 NVIDIA/cloud conformance
→ PHASE_18 candidate freeze / FCV / independent review

## 26. Revised Parallel Lanes (future; Hermes owns final DAG)

- Lane A: Provider immutable contract/profile/binding
- Lane B: PhysicalHost/WorkerRuntime/Device model
- Lane C: FAOF-2 proof POC
Reservation model depends on Lane B. Provider-local composition depends on
Lane A + #21 PhysicalPlan types. ETG convergence depends on provider
composition decisions. Parallel only after ChatGPT GO, with disjoint write
sets and same frozen base.

## 27. Success Conditions (this amendment)

OLD strict ETG cardinality conflict removed=YES; provider-local coalescing
boundary explicit=YES; semantic fusion deferred (FAOF-4)=YES; #21
PhysicalPlan unchanged=YES; membership/failure/materialization preservation
frozen=YES; PhysicalHost != WorkerRuntime != ProviderImplementation !=
Device=YES; shared worker fabric explicit=YES; runtime lifecycle model
explicit=YES; Capacity/Reserved/Observed separated=YES; reservation-first
frozen=YES; device capacity first-class=YES; HostResourceAgent boundary
frozen=YES; remote Provider resource model needs no fake physical host=YES;
OpenCue replaceable backend only=YES; #22/#23 boundary intact=YES;
implementation phase DAG updated=YES; blockers=0; architecture escalation=NONE.

## 28. Final Amendment Status

ROADMAP_22_AMENDMENT_1=PASS (draft, pending ChatGPT review)
ROADMAP_22_DECISION_RECOVERY=PASS (as amended)
READY_FOR_CHATGPT_FINAL_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
ROADMAP_23=NOT_STARTED
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE

NEXT_ACTION=CHATGPT_ROADMAP_22_DECISION_RECOVERY_AMENDMENT_1_FINAL_REVIEW
