# Roadmap #22 Native Pull Lease Assignment and ExecutionBackend Role V2 — Amendment 2

STATUS=FROZEN_AMENDMENT (docs-only; supersedes conflicting A1 clauses)
ROADMAP_22_IMPLEMENTATION=NO_GO
SUPERSEDES=A1 DispatchBackend peer abstraction; A1 LocalDispatchBackend;
  A1 OPEN_CUE_ROLE_V1; A1 reservation-aware placement → DispatchBackend segment
HISTORICAL=Amendment 1 remains valid for its non-conflicting content; do not
  rewrite A1 file; Amendment 2 wins ONLY for the areas listed in §4 of this doc.

## 1. Amendment Basis

A newer authoritative addendum arrived after Amendment 1. For Worker
assignment / dispatch / OpenCue role conflicts: LATEST_ADDENDUM_WINS.
Amendment 2 supersedes ONLY the conflicting A1 clauses. All non-conflicting
A1 architecture remains intact (provider-local coalescing,
ExecutableTaskMembership, LAW_R22_009 supersession, #21 unchanged, mandatory
Artifact boundary, membership-level failure attribution, PhysicalHost !=
WorkerRuntime != ProviderImplementation != Device, Shared Worker Fabric,
EPHEMERAL_TASK/RESIDENT_RUNTIME/REMOTE_RUNTIME, REUSABLE_SESSION deferred,
Capacity/Reserved/Observed separation, reservation-first, resident
reservation, device capacity first-class, HostResourceAgent boundary,
local-vs-remote resource feasibility, ETG provider-bound and
runtime-placement-unbound, worker-fabric-module -> media-execution-plan-module,
#23 global optimization exclusion).

## 2. Governance Classification

ARCHITECTURE_AMENDMENT_WITHIN_ROADMAP_22 (not #21 reopen, not #22 restart,
not Correction 5, not implementation). ARCHITECTURE_ESCALATION=NONE (none of
the 8 escalation conditions in §84 of the handoff is observed).

## 3. Canonical Backend Terminology — ExecutionBackend (frozen)

A1's DispatchBackend is SUPERSEDED as the cross-backend architecture
authority. Freeze instead:

```
ExecutionBackend
├── NativePullWorkerBackend
├── OpenCueFarmBackend
└── RemoteProviderBackend
```

CLEAN FORWARD: no docs-only compatibility aliases (DispatchBackend ==
ExecutionBackend, LocalDispatchBackend == NativePullWorkerBackend, etc.).
Old terminology may remain only in explicitly marked HISTORICAL/SUPERSEDED
Amendment 1 text. The current effective contract uses canonical V2
terminology. "dispatch" may remain as a lower-level verb or backend-internal
port, NOT as the canonical peer backend identity.

- ExecutionBackend answers: WHICH EXECUTION MECHANICS DOMAIN OWNS THIS WORKLOAD?
- Backend-internal dispatch answers: HOW DOES THAT BACKEND START/PLACE IT?

Typed backend kinds (frozen): NATIVE_PULL_WORKER | OPEN_CUE_FARM |
REMOTE_PROVIDER. No free-form string authority. Future kinds extensible
through controlled typed architecture.

## 4. ExecutableTask Dispatch Neutrality (frozen)

EXECUTABLE_TASK_IS_DISPATCH_MODE_AGNOSTIC_V1 (LAW_R22_044).

ExecutableTask, ExecutableTaskMembership, ProviderBoundExecutableTaskGraph
MUST NOT contain semantic dependence on Native Pull / OpenCue / remote API /
push / pull / queue transport / scheduler transport. ETG remains:
PROVIDER_BOUND=YES, WORKER_RUNTIME_BOUND=NO, PHYSICAL_HOST_BOUND=NO,
DEVICE_ASSIGNMENT_BOUND=NO, EXECUTION_BACKEND_BOUND=NO, TASK_LEASE_BOUND=NO,
RESERVATION_BOUND=NO, EXECUTION_ATTEMPT_BOUND=NO.

Backend selection and assignment are downstream runtime mechanics.

## 5. Provider-Local Coalescing Independence (frozen)

PROVIDER_LOCAL_COALESCING_IS_INDEPENDENT_OF_EXECUTION_BACKEND_V1
(LAW_R22_050). Decode→Scale→Encode may become one legal FFmpeg
ExecutableTask; that SAME ExecutableTask may later execute through an
eligible NativePullWorkerBackend OR OpenCueFarmBackend without changing its
semantic/executable identity. Backend selection MUST NOT alter
PhysicalExecutionPlan, membership semantics, provider-local composition
legality, or ETG digest.

## 6. ExecutionBackend Selection (frozen)

EXECUTION_BACKEND_SELECTION_PRECEDES_BACKEND_INTERNAL_PLACEMENT_V1
(LAW_R22_046). For every executable workload/task: first determine legal
ExecutionBackend candidates; then select ONE active backend authority; only
after selection may backend-internal placement occur. No multiple concurrent
placement authorities for the same workload.

ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1 (LAW_R22_045). Forbidden:
media-platform selects Host A AND OpenCue independently selects Host B for the
same placement domain. Correct: backend=NativePullWorkerBackend → media-
platform native matcher owns placement; OR backend=OpenCueFarmBackend →
OpenCue owns internal farm placement; OR backend=RemoteProviderBackend →
remote backend/provider invocation owns execution mechanics. Never two at
once.

## 7. Backend Eligibility vs Backend Optimization (frozen)

#22 owns: ExecutionBackend eligibility, bounded deterministic/default
selection, hard legal constraints. #23 owns future: global cost comparison,
global latency optimization, fleet economics, global fairness, global deadline
optimization, cross-backend global optimization. #22 MUST NOT choose a backend
using a general global cost optimizer.

## 8. Native Pull — Default General Worker Fabric (frozen)

NATIVE_PULL_WORKER_FABRIC_IS_DEFAULT_GENERAL_RUNTIME_V1 (LAW_R22_047).

TASK_ASSIGNMENT_PROTOCOL_V1 = WORKER_INITIATED + CENTRALLY_MATCHED +
LEASE_BASED + RESERVATION_BACKED + LOCAL_ADMISSION_CHECKED.
Shorthand: WORKER_PULL + CENTRAL_MATCHING + ATOMIC_RESERVATION_AND_LEASE +
LOCAL_ADMISSION. Reject: PURE_PUSH_ONLY; Reject: WORKER_SELF_SCHEDULING
(LAW_R22_037: worker pull does not transfer placement authority to worker;
LAW_R22_038: local resource state is placement input, not placement
authority).

Use cases (normal default when feasible): FFmpeg, OpenCV, MLT, general CPU
tasks, general GPU tasks, short transcode, proxy, thumbnail, generic
preprocessing, ephemeral cloud workers. These are NOT canonical product-
function machine classes; eligibility remains typed and
capability/resource-based.

## 9. CentralWorkMatcher — #22 Bounded Role (frozen)

Do NOT introduce a full #23 scheduler into #22. Freeze a bounded component:

CentralWorkMatcher responsibilities:
- receive RequestWork
- validate fresh Worker/Host evidence
- inspect pending executable work
- apply hard legality
- apply Provider/runtime eligibility
- apply current reservation feasibility
- select one eligible task using bounded deterministic policy (replaceable
  BoundedSelectionPolicy)
- atomically create assignment/reservation/lease (ASSIGNMENT_GRANT_V1)
- return assignment

It MUST NOT own: DRF, global fairness optimizer, deadline optimizer, bin
packing, global resource fragmentation optimization, cross-cluster
optimization, global preemption economics, cost optimizer (all #23).

#22 BoundedSelectionPolicy may use a simple deterministic ordering sufficient
for correctness/testing. It does NOT establish final platform semantics for
tenant fairness / deadline / global priority / cost (those remain #23).

CENTRAL_SCHEDULER_REMAINS_PLACEMENT_AUTHORITY_V1: Worker may say "I AM READY"
and report capacity/runtime availability/device availability/observed
pressure/resident reservations/health. Worker cannot decide which global task
to take, tenant fairness, priority, deadline, global locality, GPU
preservation, cross-provider legality. Worker may only ACCEPT assigned work or
DECLINE assigned work (LAW_R22_043).

## 10. RequestWork Contract (frozen)

Typed RequestWork. Recommended bounded content: RequestWorkId,
WorkerRuntimeId, PhysicalHostId (for local/native workers),
HostResourceSnapshotRefOrValue, WorkerRuntimeAvailability,
DeviceAvailabilitySummary, SnapshotFreshness metadata, optional worker-derived
SchedulableCapacityView. MUST NOT contain: global queue choice, desired TaskId
chosen by Worker, global priority decision.

REQUEST_WORK_IS_IDEMPOTENT_V1 (LAW_R22_039): RequestWorkId identifies one
logical pull request; network retry of the same RequestWorkId MUST NOT create
multiple active assignments. Central response returns the same still-valid
grant or a deterministic terminal/no-work result. No duplicate assignment
caused solely by transport retry.

RequestWork is specific to NativePullWorkerBackend — OpenCue farm hosts and
remote SaaS providers do NOT issue media-platform RequestWork.

## 11. Schedulable Capacity Authority (frozen)

Do NOT create a second capacity authority from RequestWork. Worker MAY send a
derived local SchedulableCapacityView, but CENTRAL_RESERVATION_LEDGER remains
authoritative for media-platform-issued reservations. Worker-derived
schedulable capacity is fresh local evidence / derived projection, NOT central
reservation authority. If central reservation state and worker-reported state
disagree: FAIL CLOSED FOR NEW ASSIGNMENT + trigger reconciliation. Never
silently choose whichever reports more free capacity (LAW_R22_033/034;
WORKER_REPORTED_CAPACITY_OVERRIDES_CENTRAL_RESERVATION_COUNT=0).

## 12. HostResourceSnapshot Freshness (frozen)

HostResourceSnapshot MUST contain enough metadata to determine freshness:
snapshotGeneration, capturedAt, WorkerRuntime/PhysicalHost identity,
schema/version. Exact timeout is runtime policy, not canonical semantic
digest. Freeze HostResourceSnapshotFreshnessPolicy. Critical stale snapshot:
NO_ASSIGNMENT or REPROBE_REQUIRED. UNKNOWN critical state: FAIL CLOSED.

Resource synchronization model (frozen): PERIODIC_HEARTBEAT +
FRESH_SNAPSHOT_ON_REQUEST_WORK + EVENT_DRIVEN_MAJOR_STATE_CHANGE. No
continuous ultra-high-frequency host state streaming. Pull reduces central
mirror freshness requirements; it does NOT eliminate central reservation
ledger, global pending workload, Provider compatibility, or policy.

## 13. Native Pull Match Flow (frozen canonical V1)

```
WorkerRuntime
    ↓
RequestWork
    ↓
validate worker/runtime registration
    ↓
validate HostResourceSnapshot freshness
    ↓
reconcile/check reservation evidence
    ↓
identify pending legal ExecutableTask candidates
    ↓
RuntimeEligibilityEvaluator
    ↓
bounded CentralWorkMatcher
    ↓
ATOMIC ASSIGNMENT GRANT
    ↓
ExecutionAssignment + Reservation(s) + TaskLease + ExecutionAttempt
    ↓
worker LocalAdmission
    ├── ACCEPT → RUNNING
    └── DECLINE → release/close → task eligible again
```

## 14. Atomic Assignment Grant (frozen)

ASSIGNMENT_GRANT_V1 (LAW_R22_040): ONE atomic transaction/boundary that
establishes ExecutionAssignment, required Reservation(s), TaskLease,
ExecutionAttempt identity/initial state (LEASED_PENDING_ADMISSION or
equivalent), and makes the task non-claimable by another Native Pull worker.
All-or-nothing: no task ownership without reservation; no reservation without
ownership; no lease without assignment; no second active lease from race.

ExecutionAttempt is created atomically with AssignmentGrant. Local admission:
ACCEPT → RUNNING; DECLINE → terminal pre-start attempt state
ADMISSION_DECLINED (do NOT pretend a declined lease never existed; preserve
auditability). Exact enum names may differ.

## 15. Distinct Identities (frozen)

ExecutableTaskId != ExecutionAssignmentId != ReservationId !=
ExecutionAttemptId != LeaseId != ArtifactId. They answer different questions.
No ID aliasing. No tuple-as-hidden-identity shortcuts unless explicitly
frozen. (Separate state machines per §19; no giant ExecutionStatus enum.)

## 16. TaskLease — Native Pull (frozen)

TASK_LEASE_IS_MANDATORY_FOR_NATIVE_PULL_ASSIGNMENT_V1 (LAW_R22_041:
at-most-one-active Native Pull lease per ExecutableTask in V1; no speculative
duplicate execution).

TaskLease represents temporary ownership of one Native Pull assignment.
Normalized references: LeaseId, ExecutableTaskId, ExecutionAssignmentId,
ExecutionAttemptId, WorkerRuntimeId, ReservationId(s), lease expiry,
heartbeat/renewal contract, fencing token / lease generation. Do NOT duplicate
full Worker/Host/Reservation state inside TaskLease — reference canonical
runtime records.

### Lease fencing (frozen)

TASK_LEASE_FENCING_V1: each lease obtains a monotonic/totally ordered fencing
token / lease generation scoped to the executable workload ownership stream. A
stale Worker holding an expired/replaced lease MUST NOT mark task complete,
publish authoritative completion, release another attempt's reservation, or
overwrite current attempt state. Late messages with obsolete fencing token
fail closed (LAW_R22_042: stale lease cannot authoritatively complete task).

### TaskLease state (separate machine)

ACTIVE | RELEASED | EXPIRED | REVOKED (minimum). Admission status belongs to
ExecutionAttempt / admission result, not necessarily the Lease state itself.
Exact enum may differ.

### Heartbeat / renewal

WorkerRuntime is responsible for renewing an active Native Pull TaskLease
while the attempt remains valid/running. Heartbeat semantics MUST define:
renewal interval contract, expiry rule, stale heartbeat rule, lease renewal
authorization, fencing-token validation. Heartbeat does NOT create additional
resource capacity.

### Lease expiry / worker disconnect / late completion

On expiry: lease invalid; attempt → LEASE_LOST; reservation released/expired
per atomic recovery rule; task reschedulable; new lease may be granted; old
Worker's subsequent completion fenced out. Do not assume the expired process
actually stopped.

LATE_COMPLETION_FAILS_CLOSED_V1: a stale/expired attempt may have produced
bytes, but it MUST NOT automatically satisfy the ExecutableTask. Authoritative
task completion requires current valid ownership/fencing evidence.
Late-produced immutable Artifact may be quarantined / retained as diagnostic
evidence / garbage-collected per implementation policy; it does NOT silently
become canonical task output.

WorkerRuntime disconnect: heartbeat freshness loss → lease renewal stops →
active leases eventually expire → reservations recover via fenced expiry path
→ tasks reschedulable → worker re-registration does NOT revive expired leases
→ old lease messages remain fenced.

ACCIDENTAL_DUPLICATE_EXECUTION_IS_NOT_A_VALID_V1_OPTIMIZATION: prevent two
active Native Pull leases (concurrent RequestWork), network-retry duplicate
grants, expired-worker late completion stealing task completion, retry racing
old attempt — via atomic assignment, unique active lease constraint, fencing
token, idempotent RequestWork, fenced completion.

### Artifact commit / idempotency

Artifact remains immutable authority. Runtime completion distinguishes: bytes
produced / Artifact committed / ExecutableTask authoritatively completed.
Sequence: provider execution → output validation → immutable Artifact commit →
fenced completion transition. If an obsolete attempt commits an Artifact after
lease loss: Artifact existence does not itself prove task success; do NOT
mutate/overwrite existing immutable Artifact identity.

## 17. Worker Local Admission (frozen)

WORKER_LOCAL_ADMISSION_FAILS_CLOSED_V1: after central AssignmentGrant,
immediately before execution, LocalAdmissionController (or equivalent) uses
freshest local reality. It may ACCEPT or DECLINE(reason). It may NOT select
another global task (LAW_R22_043).

Typed local decline reasons (at minimum): LOCAL_RESOURCE_PRESSURE,
DEVICE_TEMPORARILY_UNAVAILABLE, RUNTIME_UNHEALTHY, LICENSE_UNAVAILABLE,
SCRATCH_STORAGE_PRESSURE, RESIDENT_RESERVATION_CONFLICT, THERMAL_PROTECTION,
LOCAL_POLICY_CHANGED, STALE_OR_INCONSISTENT_LOCAL_STATE. Unknown
safety-critical state: DECLINE. Do not run optimistically.

On DECLINE: ExecutionAttempt → ADMISSION_DECLINED; TaskLease →
RELEASED/REVOKED per contract; Reservation(s) → RELEASED; ExecutableTask →
eligible for future matching if failure policy allows; typed decline reason
recorded; Worker may issue new RequestWork; Worker may NOT choose an alternate
queued task itself.

## 18. Reservation Drift (frozen)

If Worker detects local resource usage/reservation state inconsistent with
central media-platform reservation ledger: do NOT oversubscribe; DECLINE if
needed; report discrepancy through HostResourceAgent; trigger reconciliation.
Central reservation ledger remains platform-issued reservation authority.
Observed/manual load remains local admission evidence.

## 19. State Machine Separation (frozen)

Do NOT create one giant ExecutionStatus enum. Separate at minimum: executable
workload readiness/completion state; ExecutionBackendSelection state (if
needed); ExecutionAssignment state (if needed); Reservation state; TaskLease
state; ExecutionAttempt state; WorkerRuntime state; BackendExecutionHandle /
backend submission state. No parallel competing authorities.

Reservation lifecycle (minimum): ACTIVE | RELEASED | EXPIRED. Independent
from Artifact/task identity.

ExecutionAttempt state (minimum semantic): LEASED_PENDING_ADMISSION | RUNNING |
SUCCEEDED | FAILED | ADMISSION_DECLINED | CANCELLED | LEASE_LOST. Do not
combine lease expiry with generic runtime failure.

## 20. ExecutionBackend Interface and BackendExecutionHandle (frozen)

ExecutionBackend bounded common semantics: backend identity/kind; eligibility
contract; submit/start accepted executable workload; return
BackendExecutionHandle; observe execution state; cancel where supported;
return typed success/failure; produce/commit expected Artifact outputs. No
backend becomes media semantic authority.

BackendExecutionHandle = one active execution submission/ownership reference
for the selected ExecutionBackend:
- Native Pull: backed by Assignment + Reservation + TaskLease.
- OpenCue: references OpenCue job/submission identity.
- Remote Provider: references remote request/job identity.
Do NOT make the common abstraction pretend all internals are identical.

TaskLease scope: mandatory ONLY for NativePullWorkerBackend. Do NOT force a
fake WorkerRuntime/TaskLease model onto OpenCueFarmBackend or
RemoteProviderBackend. All backends MUST satisfy common
SINGLE_ACTIVE_BACKEND_EXECUTION_OWNERSHIP + idempotent/fenced platform
completion semantics.

## 21. OpenCue Role V2 (frozen)

OPEN_CUE_ROLE_V1 (REPLACEABLE_DISTRIBUTED_FARM_SCHEDULING_AND_DISPATCH_BACKEND)
is SUPERSEDED. Freeze:
- OPEN_CUE_ROLE_V2=OPTIONAL_SPECIALIZED_OFFLINE_FARM_EXECUTION_BACKEND
  (LAW_R22_048)
- OPEN_CUE_DEFAULT_GENERAL_WORKER_FABRIC=NO
- OPEN_CUE_ADOPTED=YES; OPEN_CUE_DROPPED=NO

Primary intended eligibility classes: FRAME_FARM, DCC_RENDER_FARM,
LARGE_BATCH, LICENSE_AWARE_BATCH, STUDIO_FARM_OPERATIONS. Examples: Blender
large frame farm, Houdini farm, Arnold/RenderMan-class rendering, large
offline frame fanout, license-aware DCC batch. Do NOT encode product name as
canonical backend selection rule; eligibility derives from typed
runtime/workload requirements.

Once ExecutionBackendSelection = OpenCueFarmBackend, OpenCue MAY own internal
frame/proc placement, farm queue mechanics, host allocation, farm retry,
layer/frame mechanics, operator controls. Media-platform MUST NOT
simultaneously select farm hosts for the same internal placement domain.

Media-platform retains authority over: why ExecutableTask exists, Provider
legality, Provider binding, Artifact contract, trust/privacy, backend
eligibility, cross-backend dependencies, final task success semantics. OpenCue
owns replaceable farm mechanics only.

Submission model (#22 V1): one platform ExecutableTask → one OpenCue backend
submission. OpenCue may internally fan into frames/procs/hosts/farm retries
(backend-private mechanics) that MUST map back to the owning ExecutableTask.
Do NOT let OpenCue internal fanout rewrite the platform ETG. Later aggregation
of multiple platform ExecutableTasks into one OpenCue submission requires a
separately frozen rule (not assumed in V1).

Retries: OpenCue internal frame/proc retry != new media-platform
ExecutionAttempt by default (LAW_R22_051). One platform ExecutionAttempt may
correspond to one OpenCue submission whose internal farm mechanics perform
bounded retries. A full platform-level resubmission creates a new
ExecutionAttempt. Preserve backend execution provenance sufficient to explain
internal retries.

Lease model: do NOT fake NativePull TaskLease for OpenCue workers. OpenCue's
own farm host/proc ownership remains backend-internal mechanics. Platform uses
BackendExecutionHandle + one-active-backend-ownership + fenced completion
semantics at the OpenCue boundary.

OpenCue internal farm scheduling once selected does NOT mean media-platform #22
owns global scheduling semantics. Distinguish: #22 = backend selection +
OpenCue adapter/submission boundary; OpenCue backend = internal farm mechanics
for selected workload; #23 = media-platform future global policy about
workload/backend/fleet choices.

OPEN_CUE_FORK=DEFERRED (adapter sidecar first).

## 22. RemoteProviderBackend (frozen)

REMOTE_PROVIDER_RUNTIME_IS_NOT_FORCED_INTO_WORKER_OR_OPEN_CUE_ABSTRACTIONS_V1
(LAW_R22_049). No fake PhysicalHostId / WorkerRuntimeId / TaskLease when the
remote service has none. Use typed: remote request/job identity,
quota/concurrency state, remote health, rate limits, policy, region,
budget/cost inputs as applicable.

Remote execution still requires: one active platform BackendExecutionHandle;
idempotent submission where provider supports it; duplicate-call protection;
typed timeout/cancel semantics; fenced platform completion. Remote internal
mechanics remain provider-specific.

## 23. Cross-Backend Execution (frozen)

A single ProviderBoundExecutableTaskGraph may have tasks assigned to different
ExecutionBackends (e.g., Blender → OpenCueFarmBackend → Artifact[]; OpenCV
analysis → NativePullWorkerBackend → Artifact; FFmpeg final encode →
NativePullWorkerBackend). Cross-backend coordination uses ExecutableTask
dependencies + immutable typed Artifact boundary (LAW_R22_052). Never hidden
backend-private state.

## 24. ExecutionBackendSelection Record (frozen)

Because ETG is backend-neutral, introduce a downstream runtime record:
ExecutionBackendSelection { ExecutableTaskId; selected ExecutionBackend
kind/implementation; selection reason/policy version where useful }. It is
runtime binding state; it does NOT participate in ETG digest.

## 25. ExecutionAssignment — Native Pull Only (frozen refinement)

ExecutionAssignment is the Native Pull concrete runtime placement:
ExecutionAssignment { ExecutionAssignmentId; ExecutableTaskId;
ProviderImplementationId; WorkerRuntimeId; PhysicalHostId; DeviceId(s) }.
Reservation(s) remain separate references. TaskLease references
ExecutionAssignment. For RemoteProviderBackend and OpenCueFarmBackend: do NOT
invent a fake ExecutionAssignment if no native worker placement exists — use
BackendExecutionHandle.

### Native Pull object relation (frozen)

ExecutableTask → ExecutionBackendSelection(NATIVE_PULL) → ExecutionAssignment
→ Reservation(s) → TaskLease → ExecutionAttempt. AssignmentGrant atomically
creates/links ExecutionAssignment + Reservation(s) + TaskLease +
ExecutionAttempt. No duplicated ownership authority.

### OpenCue object relation

ExecutableTask → ExecutionBackendSelection(OPEN_CUE_FARM) → ExecutionAttempt →
BackendExecutionHandle(OpenCueJobRef) → OpenCue internal farm mechanics. No
Native Pull ExecutionAssignment required.

### Remote object relation

ExecutableTask → ExecutionBackendSelection(REMOTE_PROVIDER) → ExecutionAttempt
→ BackendExecutionHandle(RemoteRequestRef) → remote provider. No
PhysicalHost/WorkerRuntime/TaskLease required.

## 26. Backend-Aware Runtime Eligibility (frozen)

RuntimeEligibilityEvaluator must not assume all tasks map to local
PhysicalHost/WorkerRuntime:
- Native Pull eligibility: WorkerRuntime + PhysicalHost + Device +
  reservations + local runtime health.
- OpenCue eligibility: farm backend availability + provider/workload
  eligibility + farm submission requirements (OpenCue handles host placement
  internally).
- Remote Provider eligibility: remote provider health/quota/concurrency/
  policy.
Do NOT force one universal nullable RuntimeEligibility context.

## 27. Updated Pipeline (frozen, replaces A1's)

```
PhysicalExecutionPlan
        ↓
Provider candidate discovery
        ↓
CompatibilityKernel
        ↓
ProviderCompatibilityGraph
        ↓
ProviderLocalCompositionEvaluator
        ↓
ProviderBoundExecutableTaskGraph
        ↓
ExecutionBackendEligibility
        ↓
ExecutionBackendSelection
        ├── NATIVE_PULL_WORKER: Worker RequestWork → fresh snapshot →
        │    RuntimeEligibilityEvaluator → CentralWorkMatcher → Atomic
        │    AssignmentGrant → ExecutionAssignment + Reservation(s) + TaskLease
        │    + ExecutionAttempt → LocalAdmission → execute
        ├── OPEN_CUE_FARM: farm submission eligibility → ExecutionAttempt →
        │    BackendExecutionHandle → OpenCue internal placement
        └── REMOTE_PROVIDER: remote runtime eligibility → ExecutionAttempt →
             BackendExecutionHandle → provider API invocation
All successful paths: → immutable Artifact → fenced authoritative completion
→ downstream dependencies. No circular authority.
```

## 28. RequestWork / Lease Failure Algebra (frozen)

Typed failure/decline families (do NOT collapse into RUNTIME_FAILED):
NO_ELIGIBLE_WORK, STALE_HOST_RESOURCE_SNAPSHOT, WORKER_RUNTIME_NOT_REGISTERED,
WORKER_RUNTIME_UNHEALTHY, RESERVATION_CONFLICT, DEVICE_UNAVAILABLE,
LOCAL_ADMISSION_DECLINED, LEASE_EXPIRED, LEASE_REVOKED, LEASE_FENCING_REJECTED,
WORKER_DISCONNECTED, DUPLICATE_REQUEST_REPLAYED, BACKEND_NOT_ELIGIBLE,
OPEN_CUE_SUBMISSION_FAILED, REMOTE_PROVIDER_SUBMISSION_FAILED,
LATE_COMPLETION_REJECTED.

## 29. Adversarial Scenario Analysis (frozen reasoning)

SCENARIO_1 (two Workers RequestWork concurrently for one task): central atomic
AssignmentGrant grants at most one (single active lease, LAW_R22_041); the
other gets NO_ELIGIBLE_WORK / duplicate-replay protection (idempotent
RequestWorkId).
SCENARIO_2 (Worker receives lease, network dies before admission): attempt is
LEASED_PENDING_ADMISSION; lease expires without renewal → LEASE_LOST; task
reschedulable; old worker's later admission fenced out.
SCENARIO_3 (Worker accepts and runs, heartbeat lost): lease expires via stale
heartbeat rule; attempt LEASE_LOST; reservation released via fenced expiry;
task reschedulable; old worker's late completion fenced.
SCENARIO_4 (lease expires, task reassigned, old worker later completes):
LATE_COMPLETION_FAILS_CLOSED — bytes may exist but not authoritative;
new attempt's completion is authoritative; old output quarantined/GC'd.
SCENARIO_5 (same RequestWork retried due transport timeout): idempotent
RequestWorkId returns same still-valid grant or terminal no-work; no duplicate
assignment.
SCENARIO_6 (local admission declines because GPU disappeared): typed
DEVICE_TEMPORARILY_UNAVAILABLE; attempt ADMISSION_DECLINED; lease released;
reservation released; task eligible again; typed reason recorded.
SCENARIO_7 (central ledger says free but worker reports resident conflict):
FAIL CLOSED for new assignment; reconciliation triggered; worker-derived view
never overrides central reservation ledger.
SCENARIO_8 (worker reconnects after lease expired): re-registration does NOT
revive expired leases; old lease messages fenced; worker may issue new
RequestWork.
SCENARIO_9 (OpenCue internally retries a frame multiple times): backend-
internal mechanics; one platform ExecutionAttempt; provenance explains retries;
no new platform attempt (LAW_R22_051).
SCENARIO_10 (remote provider API request times out but may still be running):
typed timeout semantics; duplicate-call protection; fenced completion; never
assume success or failure without authoritative remote result.
SCENARIO_11 (OpenCue task produces Artifact consumed by Native Pull FFmpeg
task): cross-backend coordination via immutable Artifact boundary
(LAW_R22_052); no hidden backend temp path.
SCENARIO_12 (provider-local coalesced FFmpeg task executed by Native Pull vs
OpenCue): composition legality independent of backend (LAW_R22_050); ETG
digest unchanged; only ExecutionBackendSelection differs.

Every scenario satisfies: single authority, typed state transition, no
duplicate canonical completion, no hidden Artifact contract, no #21 mutation.

## 30. Module Ownership — Effective V2 (frozen)

media-execution-plan-module owns: ProviderBindingPin, ProviderCapabilityProfile,
ProviderExecutionContract, CompatibilityKernel, ProviderCompatibilityGraph,
ProviderLocalCompositionEvaluator, ExecutableTaskMembership,
ProviderBoundExecutableTaskGraph, PlanLowerer.

worker-fabric-module owns: PhysicalHost, WorkerRuntime, Device runtime,
HostResourceAgent ports, Capacity/Reservation/Observed runtime views,
RuntimeEligibility, ExecutionBackend, ExecutionBackendSelection,
NativePullWorkerBackend port, OpenCueFarmBackend port, RemoteProviderBackend
port, RequestWork, CentralWorkMatcher, ExecutionAssignment, Reservation,
TaskLease, ExecutionAttempt, BackendExecutionHandle, LocalAdmission,
retry/cancellation, lease/heartbeat, RuntimeAdapter.

worker-fabric-module DEPENDS ON media-execution-plan-module; NOT reverse; no
cycle. Concrete adapters (Native worker HTTP/gRPC transport, OpenCue API,
remote provider HTTP) remain outside core runtime semantics; no third-party
backend library becomes a dependency of media-execution-plan canonical
semantics.

## 31. HostResourceAgent Role V2 (retained + clarified)

Retains A1 HostResourceAgent. For Native Pull it supports: periodic WorkerRuntime
heartbeat, fresh HostResourceSnapshot for RequestWork, major state-change
events, local admission, reservation drift reporting, runtime health. It does
NOT decide global task selection. A PhysicalHost may host multiple
WorkerRuntimes; a HostResourceAgent may represent host-wide physical/resource
truth; WorkerRuntime pull registration/request identity remains
WorkerRuntimeId. Avoid duplicate host agents per runtime unless implementation
isolation requires it. Clear ownership: host-wide resource snapshot vs
runtime-specific availability.

Reservation-first retained: SCHEDULABLE_CAPACITY = STATIC_CAPACITY −
ACTIVE_RESERVATIONS − RESIDENT_RESERVATIONS − SAFETY_HEADROOM. Observed
telemetry secondary evidence only. Native Pull local admission adds freshest
final check; it does NOT replace central reservation accounting.

Local non-platform load (manual FFmpeg/Blender, maintenance, thermal
pressure) enters ObservedUsage / health / local admission; it does NOT
magically become media-platform reservation entries unless explicitly adopted
into common accounting. If significant production workload repeatedly bypasses
platform accounting, surface operational debt.

## 32. #22/#23 Final Boundary (frozen)

#22 owns: ExecutionBackend abstraction, backend eligibility, bounded backend
selection, Native Pull protocol, RequestWork + freshness/idempotency,
CentralWorkMatcher, ExecutionAssignment, Reservation, TaskLease, lease
fencing, lease heartbeat/expiry, local admission, typed decline, Worker
disconnect/recovery, ExecutionAttempt, BackendExecutionHandle, OpenCue
boundary, Remote provider boundary, simple deterministic matching sufficient
for execution correctness.

#22 does NOT own: global fairness optimizer, DRF, advanced priority policy,
global deadline optimization, global bin packing, fleet-wide resource
fragmentation optimizer, cross-cluster optimization, global cost/latency
optimization, global preemption economics (all #23).

## 33. Repository Reality (Native Pull mechanics)

Existing mechanics (classified REUSE_MECHANICS_ONLY — no authority):
- RenderFarmWorkerController (render-module/infrastructure/farm/api) —
  /internal/render-workers register/heartbeat/claim endpoints: existing
  worker-pull + central-claim mechanics; pattern evidence for Native Pull
  RequestWork/registration/heartbeat.
- RenderJobLeaseService (render-module/infrastructure/farm) —
  claimNextJob/renewLease/double-claim prevention/lease expiry: existing
  central-lease mechanics; pattern evidence for TaskLease (but legacy
  render-job-scoped; no fencing token — TODO in code).
- docs/architecture/render-farm-readiness-and-worker-lease-design.md — prior
  lease design; historical context only.
None of these become #22 authority; #22 Native Pull protocol is the typed
canonical contract. No fake worker/lease forced onto OpenCue or remote.

## 34. Formal Laws — Amendment 2

Retained: LAW_R22_001..036 except where A2 supersedes an A1 law. A1 laws are
NOT renumbered. No A1 law encodes OpenCue-as-general-default (A1 §13
OPEN_CUE_ROLE_V1 is a clause, superseded by §21 here; the A1 law list has no
OpenCue law — LAW_R22_036 remains). A1's LocalDispatchBackend appears only in
A1 clauses, superseded here.

Added (after LAW_R22_036):
- LAW_R22_037 WORKER_INITIATED_PULL_DOES_NOT_TRANSFER_PLACEMENT_AUTHORITY_TO_WORKER
- LAW_R22_038 LOCAL_RESOURCE_STATE_IS_PLACEMENT_INPUT_NOT_PLACEMENT_AUTHORITY
- LAW_R22_039 REQUEST_WORK_RETRY_NEVER_CREATES_DUPLICATE_ACTIVE_ASSIGNMENT
- LAW_R22_040 NATIVE_PULL_ASSIGNMENT_ATOMICALLY_ESTABLISHES_ASSIGNMENT_RESERVATION_LEASE_AND_ATTEMPT
- LAW_R22_041 NATIVE_PULL_EXECUTABLE_TASK_HAS_AT_MOST_ONE_ACTIVE_LEASE_V1
- LAW_R22_042 STALE_LEASE_CANNOT_AUTHORITATIVELY_COMPLETE_TASK
- LAW_R22_043 LOCAL_ADMISSION_MAY_DECLINE_BUT_NEVER_SELECT_ALTERNATE_GLOBAL_WORK
- LAW_R22_044 EXECUTABLE_TASK_IS_DISPATCH_MODE_AGNOSTIC
- LAW_R22_045 ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY
- LAW_R22_046 EXECUTION_BACKEND_SELECTION_PRECEDES_BACKEND_INTERNAL_PLACEMENT
- LAW_R22_047 NATIVE_PULL_IS_DEFAULT_GENERAL_WORKER_FABRIC
- LAW_R22_048 OPEN_CUE_IS_OPTIONAL_SPECIALIZED_FARM_EXECUTION_BACKEND
- LAW_R22_049 REMOTE_PROVIDER_NEED_NOT_HAVE_PHYSICAL_HOST_WORKER_OR_TASK_LEASE
- LAW_R22_050 PROVIDER_LOCAL_COALESCING_IS_INDEPENDENT_OF_EXECUTION_BACKEND
- LAW_R22_051 BACKEND_INTERNAL_RETRY_DOES_NOT_IMPLY_PLATFORM_EXECUTION_ATTEMPT
- LAW_R22_052 CROSS_BACKEND_DEPENDENCY_REQUIRES_PLATFORM_ARTIFACT_BOUNDARY

## 35. Zero Guards — A2 (future implementation)

ETG_EXECUTION_BACKEND_SEMANTIC_FIELD_COUNT=0, ETG_PULL_PUSH_SEMANTIC_FIELD_COUNT=0,
WORKER_SELF_SCHEDULING_AUTHORITY_COUNT=0, PURE_PUSH_ONLY_GENERAL_WORKER_PROTOCOL_COUNT=0,
NAIVE_NEXT_TASK_WITHOUT_LEASE_COUNT=0, REQUEST_WORK_DUPLICATE_ACTIVE_ASSIGNMENT_COUNT=0,
ASSIGNMENT_WITHOUT_RESERVATION_ATOMICITY_COUNT=0, ASSIGNMENT_WITHOUT_TASK_LEASE_COUNT=0,
TASK_ATTEMPT_LEASE_IDENTITY_CONFLATION_COUNT=0, MULTIPLE_ACTIVE_NATIVE_PULL_LEASE_COUNT=0,
LEASE_WITHOUT_FENCING_AUTHORITY_COUNT=0, STALE_LEASE_AUTHORITATIVE_COMPLETION_COUNT=0,
LOCAL_ADMISSION_ALTERNATE_TASK_SELECTION_COUNT=0,
WORKER_REPORTED_CAPACITY_OVERRIDES_CENTRAL_RESERVATION_COUNT=0,
OPEN_CUE_DEFAULT_GENERAL_SCHEDULER_AUTHORITY_COUNT=0,
OPEN_CUE_FAKE_NATIVE_PULL_WORKER_COUNT=0, REMOTE_PROVIDER_FAKE_WORKER_RUNTIME_COUNT=0,
MULTIPLE_ACTIVE_PLACEMENT_AUTHORITIES_COUNT=0, DISPATCH_BACKEND_PEER_AUTHORITY_COUNT=0,
LEGACY_LOCAL_DISPATCH_BACKEND_AUTHORITY_COUNT=0,
BACKEND_INTERNAL_RETRY_CREATES_PLATFORM_ATTEMPT_COUNT=0,
CROSS_BACKEND_HIDDEN_TEMP_PATH_AUTHORITY_COUNT=0, WORKER_FABRIC_GLOBAL_OPTIMIZER_COUNT=0.
All prior #22 zero-guard plans retained.

## 36. Implementation Phase Plan — Revised (proposal; do NOT start)

PHASE_0 CLEAN_FORWARD legacy provider/runtime/queue shadows
→ PHASE_1 Provider immutable identity/contract/profile/binding
→ PHASE_2 PhysicalHost/WorkerRuntime/Device foundations
→ PHASE_3 Capacity/Reservation/Observed primitives
→ PHASE_4 CompatibilityKernel/ProviderCompatibilityGraph
→ PHASE_5 Provider-local composition + ExecutableTaskMembership
→ PHASE_6 ProviderBoundExecutableTaskGraph + IDs/digest
→ PHASE_7 HostResourceAgent + runtime eligibility
→ PHASE_8 ExecutionBackend + ExecutionBackendSelection contracts
→ PHASE_9 Native Pull RequestWork + registration/freshness/idempotency
→ PHASE_10 CentralWorkMatcher bounded matching
→ PHASE_11 ExecutionAssignment + Reservation + TaskLease + ExecutionAttempt atomic grant
→ PHASE_12 TaskLease fencing/heartbeat/expiry/disconnect recovery
→ PHASE_13 LocalAdmission + typed decline + reservation reconciliation
→ PHASE_14 retry/cancellation/late completion/duplicate-execution fencing
→ PHASE_15 PlanLowerer/RuntimeAdapter
→ PHASE_16 Artifact staging/materialization + fenced output completion
→ PHASE_17 sandbox/isolation
→ PHASE_18 FAOF-2 Lean4 + Coq
→ PHASE_19 FFmpeg CPU Native Pull conformance
→ PHASE_20 Intel VAAPI/QSV Native Pull conformance
→ PHASE_21 OpenCue specialized farm backend bounded POC
→ PHASE_22 RemoteProvider backend conformance
→ PHASE_23 NVIDIA/cloud Native Pull worker conformance
→ PHASE_24 candidate freeze / FCV / independent review

## 37. Future Parallel DAG (recomputed; Hermes owns final DAG)

Early: Lane A (Provider immutable contract/profile/binding), Lane B
(PhysicalHost/WorkerRuntime/Device/Capacity foundations), Lane C (FAOF-2
read-only/proof POC). After foundations: Lane D (ProviderLocalComposition +
ETG membership), Lane E (Native Pull protocol objects/state machines). After
ExecutionBackend interface freezes: Lane F (OpenCue adapter POC), Lane G
(RemoteProvider backend). Do NOT parallelize components with unresolved
ownership/API seams.

## 38. Escalation Conditions (monitored, none triggered)

A. Native Pull correctness requires changing #21 PhysicalExecutionPlan →
NOT OBSERVED. B. TaskLease requires mutable assignment state inside ETG
digest → NOT OBSERVED. C. OpenCue specialization requires redefining Artifact
semantics → NOT OBSERVED. D. Remote Provider cannot coexist without making
fake Worker identity canonical → NOT OBSERVED. E. Provider-local composition
becomes dependent on pull/OpenCue selection → NOT OBSERVED. F. Atomic
lease/reservation cannot be represented without a second Reservation
authority → NOT OBSERVED. G. Worker Pull requires worker self-scheduling →
NOT OBSERVED. H. #22 cannot remain bounded without implementing #23 global
optimizer → NOT OBSERVED.

## 39. Final Amendment 2 Status

ROADMAP_22_AMENDMENT_2=PASS (draft, pending ChatGPT review)
ROADMAP_22_DECISION_RECOVERY=PASS (as amended)
READY_FOR_CHATGPT_FINAL_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
ROADMAP_23=NOT_STARTED
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE

NEXT_ACTION=CHATGPT_ROADMAP_22_DECISION_RECOVERY_AMENDMENT_2_FINAL_REVIEW
