# Roadmap #22 Runtime Ownership, Resource Hold and Backend-Neutral Completion — Amendment 3

STATUS=FROZEN_AMENDMENT (docs-only; supersedes conflicting A2 clauses)
ROADMAP_22_IMPLEMENTATION=NO_GO
SUPERSEDES (A2 clauses only): lease-expiry resource release semantics;
  ExecutionAssignment provider field authority; ExecutionAttempt lifecycle
  generalization; platform-wide completion fencing; host/runtime incarnation
  handling
HISTORICAL=A1 and A2 remain authoritative for all non-conflicting content;
  Amendment 3 wins ONLY for the areas listed in §44 of the handoff

## 1. Amendment Basis

ROADMAP_22_DECISION_RECOVERY_AMENDMENT_2_FINAL_REVIEW=FAIL with four
blockers: (1) LEASE_EXPIRY_RESOURCE_ACCOUNTING, (2)
EXECUTION_ASSIGNMENT_PROVIDER_AUTHORITY, (3) BACKEND_NEUTRAL_ATTEMPT_LIFECYCLE,
(4) CROSS_BACKEND_COMPLETION_FENCING. A2 architecture is otherwise ACCEPTED
(ExecutionBackend single peer authority, DispatchBackend superseded, ETG
dispatch neutrality, NativePull default, RequestWork idempotency,
HostResourceSnapshot freshness, CentralWorkMatcher bounded role, atomic
AssignmentGrant, TaskLease mandatory for Native Pull + fencing, at-most-one
active lease, local admission fail-closed, OpenCue Role V2,
RemoteProviderBackend, one workload one placement authority, provider-local
coalescing independent of backend, #22/#23 boundary). DO NOT REOPEN those.

Governance classification: ROADMAP_22_AMENDMENT (not Correction 5, not A1/A2
redesign, not #21 reopen). ARCHITECTURE_ESCALATION=NONE (no closed upstream
authority requires change).

## 2. Core Ownership Principle (frozen)

EXECUTION OWNERSHIP != PHYSICAL RESOURCE OCCUPANCY.
LEASE_OWNERSHIP_LOST != PHYSICAL_RESOURCE_RELEASE_CONFIRMED.

Loss of task ownership does NOT prove process/device resource release.

## 3. Lease Expiry — Corrected Semantics (frozen, supersedes A2 wording)

On TaskLease expiry:
1. TaskLease becomes invalid immediately.
2. ExecutionAttempt loses authoritative execution ownership immediately.
3. Old attempt may NEVER authoritatively complete the task.
4. Task may become logically reschedulable.
5. BUT resources associated with the lost attempt MUST NOT automatically
   become schedulable until physical/runtime release is confirmed.

## 4. Reservation Recovery Hold (frozen)

Extend Reservation lifecycle with a typed recovery state:
ACTIVE | RECOVERY_HOLD | RELEASED | EXPIRED (if still distinct/useful),
or a semantically equivalent algebra. RECOVERY_HOLD means: task ownership has
been lost; previous reservation owner may no longer execute authoritatively;
physical process/resource release is NOT yet confirmed; reserved capacity
remains unavailable to new placement; HostResourceAgent / RuntimeAdapter
reconciliation is required. RECOVERY_HOLD remains part of the existing
Reservation authority — no second competing reservation authority.

### Triggers (enter RECOVERY_HOLD when)

TaskLease expires or is revoked AND runtime/process/device release cannot be
proven. Examples: worker disconnected, network partition, kill acknowledgement
missing, container/runtime unreachable, GPU process state uncertain, remote
worker state unknown.

### Release (RECOVERY_HOLD → RELEASED only after typed evidence)

Evidence may include: process terminated; container stopped; runtime adapter
termination acknowledged; WorkerRuntime reconnected and reports no old
process; HostResourceAgent confirms no running platform task/resource
occupancy; device reset / worker removed from eligibility; other explicitly
typed safe reconciliation evidence. Do NOT release because telemetry appears
low.

### Host disconnect

If PhysicalHost / WorkerRuntime is disconnected and physical execution state
is unknown: mark affected runtime/host unavailable for new Native Pull
assignments (or retain equivalent capacity safety hold). Do NOT release
Reservation AND continue treating host capacity as schedulable at the same
time.

### Rescheduling after lease loss

A lost task MAY be rescheduled to another eligible WorkerRuntime/host while
the original host remains under RECOVERY_HOLD. This may intentionally cause
temporary duplicate physical execution. But only the current ownership
generation may complete authoritatively; the orphaned execution remains
non-authoritative.

### Host loss / host incarnation

If host is fully unreachable: its schedulable capacity becomes zero/
unavailable centrally; reservation recovery hold may remain until host returns
and reconciles OR host incarnation is declared dead/replaced per runtime
policy. Do not count unreachable host resources as available.

Freeze host/runtime incarnation: PhysicalHostIncarnationId and/or
WorkerRuntimeIncarnationId — runtime identity, NOT stable
PhysicalHostId/WorkerRuntimeId. A restart generates a new incarnation. Old
heartbeat/lease messages from a prior incarnation fail closed. Native
RequestWork should identify the current WorkerRuntime incarnation where
adopted; RequestWork idempotency scope includes WorkerRuntime incarnation
identity — a stale pre-restart RequestWork replay must not produce a grant
for a new runtime incarnation.

## 5. Resource Laws (frozen)

- LAW_R22_053 LEASE_OWNERSHIP_LOSS_DOES_NOT_IMPLY_PHYSICAL_RESOURCE_RELEASE
- LAW_R22_054 UNCERTAIN_ORPHANED_EXECUTION_RETAINS_RESOURCE_RECOVERY_HOLD_UNTIL_RECONCILED
- LAW_R22_055 RECOVERY_HOLD_CAPACITY_IS_NOT_SCHEDULABLE

These reinforce RESERVATION_FIRST_TELEMETRY_SECOND.

## 6. ExecutionAssignment Provider Authority (frozen)

ETG is already provider-bound; ProviderBindingPin is the ONLY immutable
provider selection authority. ExecutionAssignment MUST NOT independently
select ProviderImplementationId.

ASSIGNMENT_NEVER_REBINDS_PROVIDER_V1 (LAW_R22_056:
RUNTIME_PLACEMENT_NEVER_REBINDS_PROVIDER_IMPLEMENTATION).

ExecutionAssignment V2 authoritative shape:
```
ExecutionAssignment {
  ExecutionAssignmentId
  ExecutableTaskId
  WorkerRuntimeId
  PhysicalHostId
  DeviceId(s)
}
```
Reservation(s) remain separate records/references. ProviderImplementation is
obtained through: ExecutableTask → ProviderBindingPin →
ProviderImplementationId. Optional denormalized ProviderImplementationId field
on ExecutionAssignment (query/index/projection reasons only) MUST be
DERIVED_NON_AUTHORITY and MUST satisfy
assignment.providerImplementationId == executableTask.providerBindingPin.providerImplementationId,
fail closed on mismatch. It MUST NOT participate in selecting/rebinding
provider implementation. Prefer omitting the field in V1 unless concrete need.

### WorkerRuntime provider compatibility (correct direction)

ETG selects ProviderImplementation ↓ RuntimeEligibility finds a compatible
WorkerRuntime. FORBIDDEN: WorkerRuntime selection silently changes
ProviderImplementation.

## 7. Backend-Neutral ExecutionAttempt (frozen)

ExecutionAttempt is a platform-wide attempt identity and lifecycle. It MUST
work for NativePullWorkerBackend, OpenCueFarmBackend, RemoteProviderBackend
without fake Native lease semantics.

### Common backend-neutral minimum state machine

CREATED | RUNNING | SUCCEEDED | FAILED | CANCELLED | ABANDONED.
ABANDONED means: platform execution ownership is no longer valid, while the
underlying backend/process may still exist.

### Native-specific control state stays separate

Native Pull semantics remain in separate state authorities: TaskLeaseState
(ACTIVE/RELEASED/EXPIRED/REVOKED), LocalAdmissionDecision/AdmissionState
(PENDING/ACCEPTED/DECLINED), ReservationState (ACTIVE/RECOVERY_HOLD/RELEASED/...).
ExecutionAttempt does NOT need LEASED_PENDING_ADMISSION / LEASE_LOST /
ADMISSION_DECLINED as universal backend states. Instead represent: Attempt
CREATED + Lease ACTIVE + Admission PENDING; on decline: Attempt ABANDONED
reason=ADMISSION_DECLINED (or another explicitly frozen backend-neutral
terminal representation).

### ExecutionAttemptTerminationReason (typed, not a state authority)

Possible values: ADMISSION_DECLINED, LEASE_LOST, RUNTIME_FAILURE,
BACKEND_SUBMISSION_FAILED, CANCELLED_BY_CALLER, REMOTE_OWNERSHIP_LOST,
OPEN_CUE_SUBMISSION_LOST, etc. Reason values themselves are NOT state-machine
authorities.

### Backend relations retained

OpenCue: ExecutableTask → ExecutionBackendSelection(OPEN_CUE_FARM) →
ExecutionAttempt → BackendExecutionHandle(OpenCueJobRef). No TaskLease /
LocalAdmission / Native ExecutionAssignment required.
Remote: ExecutableTask → ExecutionBackendSelection(REMOTE_PROVIDER) →
ExecutionAttempt → BackendExecutionHandle(RemoteRequestRef). No PhysicalHost /
WorkerRuntime / Native TaskLease / Native LocalAdmission required.

## 8. Platform-Wide Completion Fence (frozen)

Native TaskLease fencing is NOT sufficient for OpenCue/Remote. Freeze ONE
canonical backend-neutral concept: **ExecutionOwnershipGeneration** (scoped to
the ExecutableTask authoritative execution stream).

Each new platform ExecutionAttempt receives a monotonically increasing (or
otherwise totally ordered) ExecutionOwnershipGeneration for that
ExecutableTask. Only the currently authoritative generation may transition
the task to authoritative completion; older generations are stale.

Do NOT create two unrelated fencing authorities. Native TaskLease fencing
token/generation MUST derive from / bind exactly to the platform
ExecutionOwnershipGeneration: TaskLease.executionOwnershipGeneration. TaskLease
may have its own LeaseId, but the authoritative completion fence is
platform-wide.

### Backend handle fences

- BackendExecutionHandle(OpenCueJobRef) MUST reference ExecutionAttemptId +
  ExecutionOwnershipGeneration. If an old OpenCue submission reports
  completion after a newer platform attempt owns the task: reject
  authoritative completion; output remains diagnostic/provenance Artifact
  evidence only per policy.
- BackendExecutionHandle(RemoteRequestRef) MUST reference ExecutionAttemptId +
  ExecutionOwnershipGeneration. Remote timeout followed by resubmission: old
  request may still complete but stale generation MUST NOT satisfy the task.
  No dependence on remote provider supporting cancellation perfectly.

## 9. Authoritative Completion Gate (frozen)

AUTHORITATIVE_EXECUTION_COMPLETION_V1: to mark ExecutableTask successfully
completed:
1. ExecutionAttempt is the currently authoritative attempt
2. ExecutionOwnershipGeneration is current
3. expected outputs are validated
4. immutable Artifact commit succeeds
5. completion transition atomically verifies ownership generation
Otherwise: completion fails closed.

Artifact vs completion (retained from A2): BYTES_EXIST != ARTIFACT_COMMITTED
!= EXECUTABLE_TASK_COMPLETED. A stale attempt MAY produce and even immutably
commit an Artifact; that Artifact does NOT by itself satisfy ExecutableTask
completion.

## 10. Completion Laws (frozen)

- LAW_R22_057 ALL_EXECUTION_BACKENDS_SHARE_PLATFORM_EXECUTION_OWNERSHIP_GENERATION
- LAW_R22_058 STALE_EXECUTION_OWNERSHIP_GENERATION_CANNOT_AUTHORITATIVELY_COMPLETE_TASK
- LAW_R22_059 NATIVE_TASK_LEASE_FENCE_BINDS_TO_PLATFORM_EXECUTION_OWNERSHIP_GENERATION
- LAW_R22_060 BACKEND_EXECUTION_HANDLE_CARRIES_PLATFORM_COMPLETION_FENCE

## 11. One Active Authority — Refined Scope

ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1 retained.
PLACEMENT_AUTHORITY_SCOPE=ONE_EXECUTABLE_TASK. A ProviderBoundExecutableTaskGraph
may have different ExecutableTasks using different backends concurrently; each
individual task has exactly one currently authoritative backend execution
generation.

Backend-internal retries (LAW_R22_051 retained): may remain inside one
platform ExecutionAttempt only if the backend submission remains under the
same current ExecutionOwnershipGeneration. If platform declares the attempt
abandoned and creates a new attempt: new generation required.

## 12. Native Lease Expiry Flow — Final (frozen)

```
Native attempt running
        ↓
TaskLease heartbeat lost
        ↓
lease expiry
        ↓
TaskLease EXPIRED
        ↓
ExecutionAttempt ABANDONED  (reason=LEASE_LOST)
        ↓
old ExecutionOwnershipGeneration stale
        ↓
resource release confirmed?
    ├── YES → Reservation RELEASED
    └── NO  → Reservation RECOVERY_HOLD
              WorkerRuntime/host capacity blocked as appropriate
              reconciliation required
Meanwhile: ExecutableTask may receive new attempt with NEW
ExecutionOwnershipGeneration on another eligible capacity.
Old attempt may NEVER authoritatively complete.
```

## 13. Local Admission Decline Flow — Final (frozen)

For admission decline, process has NOT started. Therefore: Attempt ABANDONED
(reason=ADMISSION_DECLINED); TaskLease RELEASED/REVOKED; Reservation RELEASED.
No RECOVERY_HOLD required unless local runtime reports uncertain resource
consumption despite pre-start decline.

## 14. Explicit Process Termination (frozen)

When lease is revoked/expired during running execution: RuntimeAdapter /
HostResourceAgent SHOULD attempt process/container termination where
supported. But successful task correctness MUST NOT depend on guaranteed
termination. Fencing protects semantic completion; RECOVERY_HOLD protects
resource accounting.

## 15. Adversarial Scenarios (frozen reasoning)

A3-S1 (lease expires but old FFmpeg process continues consuming CPU):
Attempt ABANDONED; reservation RECOVERY_HOLD (no termination proof); capacity
not schedulable until HostResourceAgent confirms release.
A3-S2 (lease expires while old process still owns exclusive GPU): reservation
RECOVERY_HOLD; device marked unavailable for new placement; reconciliation via
device reset / process termination evidence.
A3-S3 (host disconnects, central cannot confirm termination): RECOVERY_HOLD;
host capacity zero/unavailable centrally; task reschedulable elsewhere with
new generation; orphaned execution non-authoritative.
A3-S4 (host reboots, reconnects with same stable PhysicalHostId): new
PhysicalHostIncarnationId; old heartbeat/lease messages fail closed; stale
RequestWork replay rejected.
A3-S5 (ETG binds ProviderImplementation A but WorkerRuntime advertises B):
RuntimeEligibility rejects (worker incompatible with bound provider); no
silent provider rebind (LAW_R22_056).
A3-S6 (ExecutionAssignment accidentally requests ProviderImplementation B):
forbidden — assignment carries no provider authority; mismatch fails closed
(derived field equality check if present).
A3-S7 (OpenCue submission A times out; platform creates attempt B; A later
succeeds): A's generation stale; B authoritative; A output diagnostic only
(LAW_R22_058/060).
A3-S8 (remote request A times out; resubmit B; A later returns success): same
fence; stale generation cannot complete (no reliance on remote cancellation).
A3-S9 (Native lease generation 7 expires; generation 8 succeeds; gen 7 sends
late completion): fencing token bound to platform generation; late completion
rejected (LAW_R22_058/059).
A3-S10 (stale attempt commits Artifact before fenced completion): Artifact may
be committed immutably but does not satisfy task completion; completion gate
requires current generation + validation + atomic fence check.

Every scenario preserves: single provider binding authority, single
authoritative completion generation, no double resource allocation from
uncertain old process, no #21 mutation, immutable Artifact correctness.

## 16. Module Ownership (frozen)

worker-fabric-module owns: ExecutionAssignment, Reservation, Reservation
recovery state, TaskLease, ExecutionAttempt, ExecutionOwnershipGeneration,
BackendExecutionHandle, LocalAdmission, Host/Worker runtime reconciliation,
completion fencing. media-execution-plan-module remains unchanged.
Dependency: worker-fabric-module → media-execution-plan-module; never reverse.

## 17. Zero Guards — A3 (future implementation)

LEASE_EXPIRY_IMMEDIATE_RESOURCE_RELEASE_WITHOUT_TERMINATION_PROOF_COUNT=0,
RECOVERY_HOLD_TREATED_AS_SCHEDULABLE_CAPACITY_COUNT=0,
LOST_OWNERSHIP_IMPLIES_RESOURCE_RELEASE_COUNT=0,
EXECUTION_ASSIGNMENT_PROVIDER_REBIND_AUTHORITY_COUNT=0,
EXECUTION_ASSIGNMENT_PROVIDER_BINDING_MISMATCH_COUNT=0,
NATIVE_SPECIFIC_STATE_REQUIRED_BY_OPEN_CUE_ATTEMPT_COUNT=0,
NATIVE_SPECIFIC_STATE_REQUIRED_BY_REMOTE_ATTEMPT_COUNT=0,
BACKEND_WITHOUT_PLATFORM_COMPLETION_FENCE_COUNT=0,
STALE_PLATFORM_GENERATION_AUTHORITATIVE_COMPLETION_COUNT=0,
NATIVE_LEASE_FENCE_DIVERGES_FROM_PLATFORM_GENERATION_COUNT=0,
UNREACHABLE_HOST_SCHEDULABLE_CAPACITY_COUNT=0,
STALE_WORKER_INCARNATION_REQUEST_ACCEPTANCE_COUNT=0.
All prior #22 guards remain.

## 18. Phase Plan Refinement (A3; no redesign of PHASE_0..24)

PHASE_3: Capacity/Reservation/Observed MUST include Reservation RECOVERY_HOLD
semantics.
PHASE_11: atomic AssignmentGrant MUST preserve ProviderBindingPin authority
and create ExecutionOwnershipGeneration.
PHASE_12: lease fencing/expiry/disconnect recovery MUST include RECOVERY_HOLD
and host/runtime incarnation handling.
PHASE_14: late completion/duplicate execution MUST use platform-wide
ExecutionOwnershipGeneration.
PHASE_21/22: OpenCue/Remote conformance MUST test backend-neutral completion
fencing.

## 19. Report Placeholder Normalization (mandatory)

A3 final report MUST contain concrete values for every mechanically knowable
field; presentation placeholders (masked-count markers, "UNKNOWN", or "N/A")
are forbidden where mechanically knowable. Normalized
values (mechanically verified from the ledger):
C4_DUPLICATE_SURFACE_KEYS_CORRECTED_REPORT_VALUE=0,
DISPATCH_BACKEND_PEER_AUTHORITY=SUPERSEDED,
ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY=PASS,
CENTRAL_RESERVATION_AUTHORITY_PRESERVED=YES,
HIDDEN_BACKEND_TEMP_PATH_AUTHORITY=NO,
NATIVE_TASK_LEASE_FENCING=PASS,
DUPLICATE_SURFACE_KEY_COUNT=0,
LEGACY_TARGET_AUTHORITY_COUNT=0,
PLACEHOLDER_REQUIRED_FIELD_COUNT=0.

## 20. Ledger

Do NOT reopen C4. A3 does not change any existing TARGET_AUTHORITY /
MIGRATION_ACTION and discovers no materially relevant new source surface →
LEDGER_CHANGED_BY_A3=NO. Machine-addressability invariants remain mandatory.

## 21. Final Amendment 3 Status

ROADMAP_22_AMENDMENT_3=PASS (draft, pending ChatGPT review)
ROADMAP_22_DECISION_RECOVERY=PASS (as amended)
READY_FOR_CHATGPT_FINAL_REVIEW=YES
ROADMAP_22_IMPLEMENTATION=NO_GO
ROADMAP_23=NOT_STARTED
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE

NEXT_ACTION=CHATGPT_ROADMAP_22_DECISION_RECOVERY_AMENDMENT_3_FINAL_REVIEW
