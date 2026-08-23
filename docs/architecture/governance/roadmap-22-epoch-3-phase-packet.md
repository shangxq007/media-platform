# ROADMAP_22_EPOCH_3_PHASE_PACKET

BASE_SHA=964271ebdc03037429d2a6821e33edb62558a8b9
BASE_TREE=acead782446c2a9a424c1b518dc1a6dca6ca7e0b
CANONICAL_MAIN=036f21f7f94f61da92faa2e91934675d024d99e8
EPOCH_2_STATUS=CLOSED
EPOCH_3_STATUS=GO

MASTER_SPEC_SHA256_PREFIX=729a83af
CORRECTION_1_PACKET_SHA256=60a9e805dd5fb4453f0e1f72f6b3ce52cf87c0ba12e62f9e2c03e9cf7fbfe6dc
CORRECTION_2_PACKET_SHA256=10a9b7127a46cfd6b3738c3bb134fb6dfce785aaa5f26fcc25c0dd8a0a2bd160
ADDENDUM=docs/architecture/governance/roadmap-22-epoch-3-general-execution-fabric-runtime-authority-addendum-v1.md

## Implementation scope (bounded, per ChatGPT Epoch-3 prompt §38)

Phase 7  HostResourceAgent / HostResourceSnapshot / RuntimeEligibility
Phase 8  ExecutionBackend authority / PlacementAuthorityScope / bounded backend eligibility
Phase 9  Native RequestWork / freshness / idempotency
Phase 10 CentralWorkMatcher bounded deterministic matching
Phase 11 ExecutionAssignment + Reservation + TaskLease + ExecutionAttempt +
         ExecutionOwnershipGeneration atomic grant
Phase 12 lease fencing / heartbeat / expiry / disconnect / RECOVERY_HOLD
Phase 13 local admission / decline / reconciliation
Phase 14 retry / cancel / stale/late/duplicate fencing
PLUS     completion fence foundation, BackendExecutionHandle foundation,
         OpenCue bounded contract, RemoteProvider bounded contract,
         normalized ExecutionObservation contract, Integration/Observer Plane
         contract, runtime CLEAN FORWARD shadow cleanup

## Exclusions (do NOT implement)

Phase 15-23 (real provider lowering/conformance), #23 global optimization,
production Camel/Cloudflare/Lambda/K8s/Ray/Volcano/Slurm/Temporal, new message
broker, worker-fabric-module rename, FAOF-2, production Artifact staging.

## Task boundaries (bounded coding sessions)

A  Host truth / HostResourceSnapshot / schedulable-capacity closure (worker-fabric-module)
B  ExecutionBackend / PlacementAuthorityScope / RuntimeEligibility (worker-fabric-module)
C  RequestWork / CentralWorkMatcher (worker-fabric-module)
D  assignment / reservation / lease / attempt / generation transaction
   (worker-fabric-module + persistence)
E  failure / recovery / admission / retry fencing (worker-fabric-module)
F  BackendExecutionHandle / Observation / Integration contracts (worker-fabric-module)
G  CLEAN FORWARD + tests + guards (worker-fabric-module + tests)

Model routing: gpt-5.6-sol / high for all architecture-sensitive tasks.
Final auto-review: codex-auto-review gpt-5.6-sol/high READ_ONLY over FULL
Epoch-3 diff from 964271eb to EPOCH_3_CANDIDATE_TIP.

## Gate requirements (§57)

Fresh: identity tests, HostResourceSnapshot, SchedulableCapacity,
RuntimeEligibility, ExecutionBackend, RequestWork, CentralWorkMatcher,
assignment transaction, Reservation/TaskLease, ExecutionAttempt/generation,
admission/reconciliation, retry/cancel/stale, observation/completion-fence,
architecture guards, CLEAN FORWARD zero guards, Modulith, drift, full serial
suite, bootJar, pfirr1, CI-equivalent. Do NOT reuse 7936/301 as Epoch-3 evidence.

## Test matrix (H1-H14, B1-B12, N1-N13, L1-L10, A1-A9, O1-O8, C1-C6, D1-D6)

See ChatGPT Epoch-3 prompt §40-49 for full matrix. All adversarial cases
required with exact evidence.

## Zero guards (§47)

RENDER_SPECIFIC_RUNTIME_AUTHORITY_COUNT=0, WORKER_RUNTIME_HOST_CAPACITY_AUTHORITY_COUNT=0,
WORKER_REPORTED_CAPACITY_OVERRIDES_CENTRAL_RESERVATION_COUNT=0,
PER_RUNTIME_HOST_CAPACITY_DUPLICATION_COUNT=0,
DEVICE_CAPACITY_WORKER_SCOPED_DUPLICATION_COUNT=0,
HOST_RESOURCE_SNAPSHOT_WITHOUT_HOST_IDENTITY_COUNT=0,
HOST_RESOURCE_SNAPSHOT_WITHOUT_HOST_INCARNATION_COUNT=0,
SCHEDULABLE_CAPACITY_CROSS_HOST_SNAPSHOT_ACCEPTANCE_COUNT=0,
STALE_HOST_INCARNATION_ASSIGNMENT_ACCEPTANCE_COUNT=0,
STALE_RUNTIME_INCARNATION_ASSIGNMENT_ACCEPTANCE_COUNT=0,
MULTIPLE_ACTIVE_PLACEMENT_AUTHORITIES_PER_TASK_COUNT=0,
OPEN_CUE_PLATFORM_HOST_PLACEMENT_AUTHORITY_COUNT=0,
OPEN_CUE_RQD_AS_CANONICAL_WORKER_RUNTIME_COUNT=0,
REMOTE_PROVIDER_FAKE_PHYSICAL_HOST_COUNT=0,
REMOTE_PROVIDER_FAKE_WORKER_RUNTIME_COUNT=0,
REMOTE_PROVIDER_NATIVE_TASK_LEASE_COUNT=0,
EXECUTION_ASSIGNMENT_PROVIDER_REBIND_COUNT=0,
DUPLICATE_ACTIVE_NATIVE_LEASE_COUNT=0,
LEASE_EXPIRY_IMMEDIATE_RESOURCE_RELEASE_COUNT=0,
RECOVERY_HOLD_SCHEDULABLE_CAPACITY_COUNT=0,
STALE_GENERATION_AUTHORITATIVE_COMPLETION_COUNT=0,
BACKEND_SUCCESS_EQUALS_TASK_COMPLETED_COUNT=0,
MESSAGE_QUEUE_EXECUTION_STATE_AUTHORITY_COUNT=0,
SERVERLESS_EXECUTION_STATE_AUTHORITY_COUNT=0,
CAMEL_EXECUTION_STATE_AUTHORITY_COUNT=0,
EXECUTION_OBSERVATION_DIRECT_DB_AUTHORITY_COUNT=0,
ETG_RUNTIME_STATE_FIELD_COUNT=0,
PHYSICAL_EXECUTION_PLAN_RUNTIME_STATE_FIELD_COUNT=0,
PROVIDER_COMPATIBILITY_GRAPH_RUNTIME_STATE_FIELD_COUNT=0,
ROADMAP_23_GLOBAL_OPTIMIZER_IMPLEMENTATION_COUNT=0.
Plus legacy shadow counts (§48): LEGACY_RENDER_WORKER_RUNTIME_AUTHORITY_COUNT,
LEGACY_RENDER_JOB_LEASE_AUTHORITY_COUNT, LEGACY_PLATFORM_TASK_EXECUTION_AUTHORITY_COUNT,
LEGACY_DUPLICATE_EXECUTION_ATTEMPT_AUTHORITY_COUNT,
LEGACY_DUPLICATE_RESERVATION_AUTHORITY_COUNT,
LEGACY_GENERIC_RUNTIME_PROVIDER_GOD_INTERFACE_COUNT.

## Escalation conditions (§55)

8 conditions (compatibility evidence, legacy data, module cycle, atomic grant
representation, OpenCue dual placement, RemoteProvider fake identity, host
incarnation binding, ETG identity/digest change). STOP + report on any proof.

## Success definition (§62)

33 conditions all simultaneously true (host scope, runtime endpoint, device
scoping, snapshot binding, no capacity multiplication, observation never
overrides reservation, placement authority mapping, no dual placement, RequestWork
idempotent, no worker self-scheduling, atomic grant, one lease, no false release,
RECOVERY_HOLD unschedulable, no stale incarnation work, no stale generation
completion, retry semantics, backend-local retry isolation, completion fence,
OpenCue/RQD non-canonical, RemoteProvider no fake identity, observation is
evidence, integration mechanics only, DB authoritative, Artifact authority,
CLEAN FORWARD, static digests untouched, no #23, fresh gates green, main unchanged).
