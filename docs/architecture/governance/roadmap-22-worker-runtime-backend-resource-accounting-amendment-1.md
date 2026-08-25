# Roadmap #22 Worker Runtime, Backend, and Resource Accounting — Amendment 1

TASK_ID=ROADMAP_22_PHASE_16_CLOSURE_AND_CROSS_CUTTING_GOVERNANCE_PERSISTENCE
MODE=APPEND_FORWARD_DOCS_ONLY_GOVERNANCE
STATUS=ADOPTED_REPOSITORY_PERSISTED
IMPLEMENTATION=NOT_STARTED_OR_DEFERRED_AS_INDEXED
ARCHITECTURE_ESCALATION=NONE

## 1. Scope and precedence

This amendment freezes a cross-cutting clarification inside Roadmap #22. It
does not supersede the Provider, ExecutableTask, Native Pull,
ExecutionBackend, Artifact, Workflow, or Roadmap #23 authority contracts. It
does not start Phase 17–21 and adds no runtime implementation.

Where an older immutable record already has an equivalent stable rule, that
rule is reaffirmed instead of being renamed. In particular:

- `OPEN_CUE_IS_A_SPECIALIZED_FARM_EXECUTION_BACKEND_V1` is the existing
  equivalent of the proposed OpenCue-as-backend-plugin rule.
- `OPEN_CUE_IS_NOT_CAPABILITY_OR_PROVIDER_AUTHORITY_V1` is the existing
  stronger equivalent of OpenCue-is-not-media-provider.
- `WORKER_RUNTIME_IS_EXECUTION_ENDPOINT_NOT_HOST_CAPACITY_AUTHORITY_V1` and
  `PHYSICAL_HOST_IS_HOST_CAPACITY_SCOPE_V1` already freeze Worker != machine.
- `CAPACITY_RESERVATION_AND_OBSERVATION_ARE_DISTINCT_AUTHORITIES_V1` already
  freezes worker observation as non-authoritative for schedulable capacity.
- `PLACEMENT_AUTHORITY_IS_EXPLICIT_PER_EXECUTION_BACKEND_V1`,
  `DELEGATED_BACKENDS_MAY_KEEP_INTERNAL_PLACEMENT_AND_LOAD_OPAQUE_V1`, and
  `NO_MULTIPLE_ACTIVE_PLACEMENT_AUTHORITIES_FOR_ONE_EXECUTION_SCOPE_V1`
  already freeze the delegated backend boundary.
- `DEDICATED_WORKER_POOLS_ARE_POLICY_OPTIMIZATIONS_NOT_DOMAIN_ARCHITECTURE_V1`
  is retained from the shared-worker-fabric amendment.

## 2. Semantic implementation and execution management are distinct

`Provider` describes a semantic provider family and `ProviderImplementation`
describes the concrete semantic implementation, compatibility profile,
version, probes, and native lowering/runtime adapter. `ExecutionBackend`
describes how an already provider-bound `ExecutableTask` is managed for
execution:

```
ExecutionBackend
├── NativePullWorkerBackend
├── OpenCueFarmBackend
└── RemoteProviderBackend
```

For Native Pull, the execution relationship is:

```
WorkerRuntime -> PhysicalHost -> Device
```

These concepts and identities remain distinct:

- `WorkerRuntime` and `WorkerRuntimeIncarnation` — executable endpoint and
  one live generation of that endpoint;
- `PhysicalHost` and `PhysicalHostIncarnation` — capacity/device owner and
  one live host generation;
- `Device` — typed physical or partitioned resource;
- `RuntimeInstallation` — installed/probed ProviderImplementation and version
  on a runtime/host context;
- `WorkerPool` — placement-policy membership/view;
- `ResourceAccountingDomain` — the boundary inside which physical capacity
  and reservations must be counted exactly once;
- `BackendManagementSurface` — backend-provided native operational UI/API
  link or embedded surface, never canonical platform authority.

No one of these identities may be overloaded to stand for another.

## 3. OpenCue authority boundary

OpenCue is an optional specialized VFX/offline `ExecutionBackend` plugin, not
a media Provider and not the default general worker fabric. Generic execution
authority remains in the platform worker fabric; OpenCue implementation
authority remains inside OpenCue.

OpenCue retains Cuebot, RQD, its hosts/allocations/services, internal
scheduling, internal retry, and native administration. RQD is not a platform
`WorkerRuntime`; an OpenCue host is not a platform `PhysicalHost`. Selecting
the OpenCue backend delegates internal placement at the adapter/submission
boundary. The platform must not create fake platform workers, hosts,
reservations, or Native Pull leases to mirror backend-internal objects.

The global platform scheduler stops at the delegated backend boundary. It may
select an eligible backend and enforce platform legality, trust, provider
compatibility, Artifact, attempt, generation, and completion contracts. It
does not co-schedule OpenCue internal hosts after delegation. Roadmap #23
optimization may compare or select backends in the future, but it must never
invade OpenCue internal placement authority.

An ExecutionBackend plugin may contribute typed deep links or a
`BackendManagementSurface` to its native console. Pluginization must preserve
the backend's native control plane; it must not flatten Cuebot/RQD operations
into fake platform objects or reproduce the farm UI. The platform supplies a
coherent entry point, bounded identity correlation, authorization, and links;
OpenCue remains the farm-management surface.

## 4. WorkerRuntime support advertisement

`WORKER_RUNTIME_SUPPORT_ADVERTISEMENT_V1` is adopted, with implementation
deferred as a blocking Phase 19 gate for the first real Provider runtime.
Worker self-advertisement alone is insufficient authority.

`CAN_RUN` is a server-validated intersection of:

- requested Capability semantics and immutable task/provider binding;
- exact `ProviderImplementation` and compatibility/profile version;
- `RuntimeInstallation` presence and trusted probe evidence;
- current `WorkerRuntimeIncarnation` and, where applicable,
  `PhysicalHostIncarnation`;
- compatible typed `Device` features;
- Resource Accounting Domain capacity, safety headroom, active reservations,
  and current health vetoes;
- trust-zone, isolation, tenant, entitlement, and policy constraints.

Advertisement is typed candidate evidence. The central authority evaluates
the intersection and may reject stale, incomplete, untrusted, incompatible,
or unresourced advertisements.

## 5. Resource Accounting Domain

`RESOURCE_ACCOUNTING_DOMAIN_V1` is adopted. A Resource Accounting Domain is
the smallest authority boundary inside which physical capacity and active
reservations must be reconciled exactly once. Its default is
`PhysicalHost`. Future domains may be VM, cgroup, MIG partition, SR-IOV
virtual function, or another proven isolation/accounting boundary.

For one domain:

```
SchedulableCapacity = PhysicalCapacity - SafetyHeadroom - ActiveReservations
```

Capacity, reserved capacity, and observed usage are distinct. Observed usage
may veto unhealthy or overloaded candidates, calibrate capacity, and inform a
future optimizer. It does not create schedulable capacity and does not replace
the reservation ledger.

A machine may host multiple platform WorkerRuntimes and multiple runtime
incarnations. They must share the same applicable Resource Accounting Domain;
each runtime must not advertise the host's full physical capacity as its own.
No sum over WorkerRuntime advertisements may duplicate physical CPU, memory,
temporary storage, bandwidth, GPU, VRAM, encoder/decoder engines, or device
partitions.

## 6. WorkerPool, admission, heartbeat, and scheduler boundaries

`WorkerPool` is placement policy: labels, eligibility, isolation, trust,
locality, batching, or administrative grouping. It creates no canonical
Video/AI/Heavy worker taxonomy and no new resource or semantic authority. A
host/runtime may participate in several pools without multiplying capacity.

Local admission and heartbeat are distinct:

- heartbeat/advertisement reports typed, generation-bound candidate evidence;
- local admission is the final runtime-local accept/decline safety check for
  an already granted assignment;
- neither grants global placement authority to the worker.

Maintenance schedulers, cron cleanup, pending-row cleanup, orphan cleanup, and
similar scheduler-module jobs are lifecycle mechanics. They are not
worker-fabric runtime ownership, workflow/Temporal durable orchestration,
backend-internal scheduling, or Roadmap #23 global optimization. These five
authorities remain separate.

Cache locality may be a bounded placement/scoring optimization. It never
changes Artifact existence, digest, authorization, reuse validation, or
execution completion authority.

## 7. Implementation and phase gates

- Phase 19: first real FFmpeg Native Pull Provider vertical slice, including
  blocking WorkerRuntime support advertisement and server validation.
- Phase 20: implement Resource Accounting Domain before production permits
  two platform runtime incarnations to share physical capacity.
- Phase 21: bounded OpenCue specialized backend plugin POC and mapping; retain
  OpenCue native control plane and expose a native management surface only
  when a real delegated console exists.
- Roadmap #23: optimizer policy may consume platform-visible evidence but
  never assumes delegated-backend internal placement authority.

No implementation or phase start is claimed by this amendment.
