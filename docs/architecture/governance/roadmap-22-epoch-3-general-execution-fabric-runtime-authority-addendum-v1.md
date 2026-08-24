# Roadmap #22 Epoch 3 — General Execution Fabric Runtime Authority Addendum V1

STATUS=FROZEN_EPOCH_SCOPED_ADDENDUM
EPOCH_2=CLOSED
EPOCH_3=GO
DOES_NOT_REOPEN=EPOCH_2 (PhysicalExecutionPlan / ProviderCompatibilityGraph /
  ExecutableTask / ProviderBindingPin / provider-local composition /
  ExecutionArtifactBoundary / #21/#22 static digests)

BASE_SHA=964271ebdc03037429d2a6821e33edb62558a8b9
CANONICAL_MAIN=036f21f7f94f61da92faa2e91934675d024d99e8

## 1. Frozen principles

- EXECUTION_FABRIC_IS_CROSS_DOMAIN_RUNTIME_AUTHORITY_V1
- RENDER_IS_ONE_EXECUTION_PRODUCER_NOT_EXECUTION_RUNTIME_AUTHORITY_V1
- EXECUTABLE_TASK_IS_CROSS_DOMAIN_V1
- EXECUTION_BACKEND_IS_CROSS_DOMAIN_EXECUTION_MECHANICS_V1
- PLACEMENT_AUTHORITY_IS_EXPLICIT_PER_EXECUTION_BACKEND_V1
- WORKER_RUNTIME_IS_EXECUTION_ENDPOINT_NOT_HOST_CAPACITY_AUTHORITY_V1
- PHYSICAL_HOST_IS_HOST_CAPACITY_SCOPE_V1
- DEVICE_CAPACITY_IS_DEVICE_ID_SCOPED_NOT_WORKER_SCOPED_V1
- CAPACITY_RESERVATION_AND_OBSERVATION_ARE_DISTINCT_AUTHORITIES_V1
- PLATFORM_MANAGED_BACKENDS_REQUIRE_HOST_LEVEL_RESOURCE_TRUTH_V1
- DELEGATED_BACKENDS_MAY_KEEP_INTERNAL_PLACEMENT_AND_LOAD_OPAQUE_V1
- BACKEND_OPACITY_IS_ALLOWED_WHEN_PLACEMENT_AUTHORITY_IS_DELEGATED_V1
- PROVIDER_IS_EXECUTION_IMPLEMENTATION_NOT_SCHEDULER_V1
- OPEN_CUE_IS_A_SPECIALIZED_FARM_EXECUTION_BACKEND_V1
- OPEN_CUE_IS_NOT_CAPABILITY_OR_PROVIDER_AUTHORITY_V1
- OPEN_CUE_OWNS_ONLY_BACKEND_LOCAL_FARM_PLACEMENT_V1
- PLATFORM_OWNS_EXECUTABLE_TASK_ATTEMPT_GENERATION_AND_COMPLETION_V1
- BACKEND_LOCAL_RETRY_IS_NOT_PLATFORM_EXECUTION_ATTEMPT_V1
- ONE_PLATFORM_ATTEMPT_MAPS_TO_ONE_OPEN_CUE_SUBMISSION_V1
- BACKEND_INTERNAL_HOSTS_NEED_NOT_BECOME_CANONICAL_PLATFORM_WORKERS_V1
- HEAVY_OR_LONG_RUNNING_COMPUTE_EXECUTES_ON_WORKER_FABRIC_V1
- LIGHTWEIGHT_STATELESS_INTEGRATION_MAY_EXECUTE_ON_SERVERLESS_V1
- REMOTE_STATUS_OBSERVATION_MAY_BE_DELEGATED_TO_EDGE_OR_SERVERLESS_V1
- SERVERLESS_INTEGRATION_IS_NOT_EXECUTION_STATE_AUTHORITY_V1
- CORE_SERVICES_MUST_NOT_BLOCK_ON_LONG_RUNNING_REMOTE_EXECUTION_V1
- PROVIDER_INTERACTION_MECHANICS_ARE_NOT_EXECUTION_AUTHORITY_V1
- SYNC_CALLBACK_POLL_AND_STREAM_CONVERGE_TO_EXECUTION_OBSERVATIONS_V1
- REMOTE_POLLERS_ARE_EPHEMERAL_OBSERVERS_NOT_TASK_AUTHORITIES_V1
- WEBHOOK_INGRESS_IS_A_TRUST_AND_NORMALIZATION_BOUNDARY_V1
- REMOTE_STATUS_OBSERVATIONS_MUST_BE_ATTEMPT_AND_GENERATION_FENCED_V1
- POLLING_POLICY_IS_INTEGRATION_RUNTIME_POLICY_NOT_CAPABILITY_SEMANTICS_V1
- CAMEL_IS_INTEGRATION_MECHANICS_NOT_EXECUTION_AUTHORITY_V1
- DATABASE_IS_AUTHORITATIVE_EXECUTION_LIFECYCLE_STATE_V1
- ARTIFACT_AUTHORITY_IS_AUTHORITATIVE_OUTPUT_DATA_STATE_V1
- MESSAGE_QUEUE_IS_DURABLE_DELIVERY_MECHANICS_NOT_EXECUTION_STATE_AUTHORITY_V1
- AT_LEAST_ONCE_DELIVERY_WITH_IDEMPOTENT_FENCED_CONSUMERS_V1
- TRANSACTIONAL_OUTBOX_BRIDGES_DATABASE_STATE_TO_ASYNC_DELIVERY_V1
- ASYNC_DELEGATION_MUST_SURVIVE_PROCESS_AND_DEPLOYMENT_RESTART_V1

## 2. REMOTE_RUNTIME reality decision

REMOTE_RUNTIME_REAL_CONSUMER_COUNT=0 (only worker-fabric tests reference it;
  zero production callers)
REMOTE_RUNTIME_DECISION=KEEP_NARROW_PLATFORM_MANAGED
  (existing tests prove fail-closed semantics: REMOTE_RUNTIME cannot participate
   in LocalWorkerRuntimeIncarnationBinding capacity; keep as the narrow
   platform-managed remote runtime endpoint placeholder for Epoch 3
   RemoteProviderBackend contract boundary; MUST NOT represent arbitrary
   SaaS/OpenCue infrastructure)

## 3. Runtime disposition table (bounded, Epoch-3 scope)

| Surface | Classification | Rationale |
|---|---|---|
| render-module farm lease mechanics (5 structurally detected files) | RETIRED_INERT_MECHANICS | controller removed; farm services/repositories and stale compensation are not Spring components or scheduled consumers; active registration/heartbeat/claim/completion/expiry consumer count is zero; #22 TaskLease is canonical |
| render-module legacy queue/lease execution path (4 files) | RETIRED_INERT_MECHANICS | WorkerScheduler, RenderWorkerService, JobLeaseRepository, and RenderJobQueue are no longer Spring components/scheduled consumers; active queue lifecycle authority count is zero |
| outbox-event-module PlatformTask delivery data/coordination (4 structurally detected files) | DELIVERY_COORDINATION_ONLY | PlatformTask dispatcher and all lease/complete/fail/expiry mutators removed; remaining surfaces create/read immutable delivery intents and cannot mutate execution lifecycle state |
| render-module MultiProviderPipelineService | NON_OVERLAPPING_KEEP | render-domain pipeline routing, not runtime authority |
| extension-module PlatformPluginPoints/ThirdPartyRenderProviderExtension | NON_OVERLAPPING_KEEP | extension SPI, not runtime authority |
| docs/deprecated/javacv/ (deleted Epoch 1) | N/A | already removed |
| media-execution-plan-module execution domain (Epoch 1/2 canonical) | REUSE_AS_CANONICAL | immutable static layer |
| worker-fabric-module domain (Epoch 1/2 canonical) | REUSE_AS_CANONICAL | runtime foundation |

UNCLASSIFIED_RUNTIME_ROWS=0

## 4. Placement authority mapping (frozen, non-mutable)

NATIVE_PULL_WORKER → PLATFORM_MANAGED
OPEN_CUE_FARM → BACKEND_DELEGATED
REMOTE_PROVIDER → REMOTE_PROVIDER_MANAGED

## 5. Database authority

PostgreSQL remains canonical relational storage (repository-standard). No new
database product. Authoritative runtime state (assignment/reservation/lease/
attempt/generation) persisted durably; in-memory/queue/worker-cache never canonical.

## 6. Epoch-3 implementation phases (E3-0..E3-15)

E3-0 reality sweep + packet (this addendum)
E3-1 HostResourceSnapshot + HostResourceAgent + freshness
E3-2 SchedulableCapacity host-binding closure
E3-3 ExecutionBackend + PlacementAuthorityScope + bounded eligibility
E3-4 RuntimeEligibilityEvaluator
E3-5 RequestWork + idempotency
E3-6 CentralWorkMatcher
E3-7 ExecutionAssignment / ExecutionAttempt / ExecutionOwnershipGeneration
E3-8 atomic Reservation + TaskLease + assignment grant
E3-9 heartbeat / expiry / disconnect / RECOVERY_HOLD
E3-10 local admission + decline + reconciliation
E3-11 retry / cancel / stale/duplicate fencing
E3-12 BackendExecutionHandle + completion fence foundation
E3-13 ExecutionObservation + Integration/Observer Plane contract
E3-14 CLEAN FORWARD legacy runtime shadow removal
E3-15 fresh tests / full suite / independent review

## 7. Exclusions (explicitly deferred)

Phase 15+ (real PlanLowerer/RuntimeAdapter), Phase 16 (production Artifact
staging), Phase 17 (full sandbox), Phase 18 (FAOF-2), Phase 19-23 (FFmpeg/VAAPI/
OpenCue/remote/NVIDIA conformance), #23 global optimization, production Camel/
Cloudflare/Lambda/K8s/Ray/Volcano/Slurm/Temporal, worker-fabric-module rename.

## 8. Escalation conditions

STOP + ARCHITECTURE_ESCALATION_REQUIRED if any: (1) concrete external/persisted
compatibility requires keeping a runtime shadow; (2) production data depends on
legacy RenderJobLease/PlatformTask semantics that cannot CLEAN FORWARD migrate;
(3) worker-fabric→media-execution-plan direction cannot support canonical runtime
without cycle; (4) atomic grant not representable in current relational
transaction architecture; (5) OpenCue requires dual host-level placement
authority; (6) RemoteProvider requires fake WorkerRuntime for non-mechanical
reason; (7) host capacity cannot bind exact incarnation without changing Epoch-1
identities; (8) Epoch-3 requires changing Epoch-2 ETG identity/digest rules.
