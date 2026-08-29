# Roadmap #22 Phase 20 Resource Accounting and Hardware Provider Conformance — Bounded Architecture Contract V1

TASK=ROADMAP_22_PHASE20_RESOURCE_ACCOUNTING_AND_HARDWARE_PROVIDER_CONFORMANCE_DECISION_RECOVERY_V2
CONTRACT=ROADMAP_22_PHASE20_RESOURCE_ACCOUNTING_AND_HARDWARE_PROVIDER_CONFORMANCE_BOUNDED_ARCHITECTURE_CONTRACT_V1
BASE_SHA=e02579181ba3049ae65ed81080c93a7212f5833d
BASE_TREE=b67136e3a4b4e08688091bad0c4dad30d841978d
MODE=FAST_DELIVERY_MODE+CLEAN_FORWARD+FAIL_CLOSED+APPEND_FORWARD_HISTORY+NO_HISTORY_REWRITE+EVIDENCE_DRIVEN+MINIMUM_CHANGE
ARCHITECTURE_STATUS=FROZEN
INDEPENDENT_REVIEW_STATUS=PENDING
IMPLEMENTATION_STATUS=NOT_STARTED
IMPLEMENTATION_AUTHORIZATION=NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE
ROADMAP_22_PHASE20_DECISION_RECOVERY=PASS
PHASE20_BOUNDED_ARCHITECTURE_CONTRACT=FROZEN
READY_FOR_PHASE20_IMPLEMENTATION=YES
IMPLEMENTATION_COMPLETE=NO
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
CROSS_LANE_RECONCILIATION_REQUIRED=NONE_AT_FREEZE

## 1. Scope and precedence

This contract is a bounded Decision Recovery / Architecture Contract Freeze. It does not implement Phase 20. It reconciles accepted Roadmap #22 runtime/resource code and prior amendments with the Phase20 V2 requirements at the exact canonical base above.

Phase19 is closed and is not reopened. The historical 45-capability ledger remains evidence, not an implementation backlog. `RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0` remains mandatory.

The authority questions remain:

- `ROADMAP_22 = CAN_RUN?`
- `ROADMAP_23 = WHICH_FEASIBLE_RUNTIME_IS_BEST?`

No global/fleet optimizer, global cost optimizer, OpenCue implementation, BMF implementation, provider-local BMF graph, FAOF-3 production solver, general package manager, general device scheduler, staging deployment, GraphQL work, or unrelated roadmap work is authorized.

Repository-reality authority:

- `roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-repository-reality-inventory-v1.json`
- `roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-disposition-ledger-v1.json`

The disposition ledger has 36 bounded authority-family rows and requires `UNCLASSIFIED=0`.

## C1 — Phase boundary and decision meaning

Phase20 owns bounded technical/resource feasibility and the resource authority needed to answer `CAN_THIS_PROVIDER_IMPLEMENTATION_RUN_HERE?`. It may use simple deterministic filtering sufficient for correctness. It does not answer which feasible choice is globally best.

`CanRunDecision` is not a score. It is a fail-closed typed decision over exact provider implementation requirements and exact runtime/host/device evidence. Cost, global fairness, global locality and optimization cannot convert an incompatible candidate into a compatible candidate.

## C2 — Resource authority taxonomy

The following authorities are distinct and must never be collapsed into one generic resource object:

`ExecutionRequirement != Capacity != Reservation != ObservedUsage != Quota != Cost`

Each authority has a distinct owner, lifecycle, persistence and decision role. A shared unit such as bytes or millicores does not imply shared authority.

## C3 — ExecutionRequirement authority and lifecycle

`ExecutionRequirement` is an immutable, provider-neutral declaration/derivation of what an execution plan needs. It may include CPU architecture, minimum memory, device class, codec/hardware feature, runtime capability, sandbox requirement and provider runtime dependency requirement.

Owner: media execution planning/provider contract boundary.
Lifecycle: created from frozen planning semantics; immutable for one executable binding; versioned with its contract.
It is not mutable current capacity and contains no WorkerRuntimeId, PhysicalHostId, DeviceId, probe timestamp, current free resource, reservation, quota or cost.

The current planning `ExecutionRequirement` is canonical. The zero-consumer `ExecutionResourceRequirement` is a CLEAN FORWARD delete-shadow candidate. `RuntimeResourceDemand` is a runtime projection derived from the canonical requirement, not a second semantic requirement authority.

## C4 — Capacity authority and lifecycle

`Capacity` is the theoretical typed resource exposure of a Resource Accounting Domain: CPU, memory, temporary storage and first-class device dimensions such as VRAM, compute units and media engines.

Owner: worker-fabric host/resource authority.
Default accounting scope: `PhysicalHostId`.
Lifecycle: fingerprinted/static snapshot for a host incarnation; changes create new observation/generation, never mutate semantic media state.
Capacity is not current free capacity, observed utilization, quota, reservation or cost.

A separate `ResourceAccountingDomainId` is deferred until a VM/cgroup/MIG/SR-IOV or other isolated domain proves an independent capacity and reservation lifecycle. The default PhysicalHost scope is sufficient today.

## C5 — Reservation authority and lifecycle

`Reservation` is a bounded, temporary, typed claim against capacity for an exact task/attempt/runtime/host/device ownership context. It is durable runtime scheduling state with explicit states such as active, recovery hold, resident, released or expired.

Owner: worker-fabric atomic assignment/reservation boundary.
Lifecycle: atomically created with assignment ownership; fenced; explicitly released/expired/reconciled. Reservation is not actual observed usage and not billing `CostReservation`.

Schedulable capacity remains:

`STATIC_CAPACITY - ACTIVE_RESERVATIONS - RECOVERY_HOLD_RESERVATIONS - RESIDENT_RESERVATIONS - SAFETY_HEADROOM`

Observed usage may veto contradictory/unsafe state but cannot create unreserved capacity.

## C6 — ObservedUsage authority and lifecycle

Worker-fabric `ObservedUsage` is mutable, timestamped host/device telemetry. It reports measured consumption and health evidence.

Owner: bounded HostResourceAgent observation pipeline.
Lifecycle: probe/capture -> freshness classification -> eligibility/local admission evidence -> expiration.
It is not capacity, reservation, quota, cost or billing usage authority. It never enters canonical media, revision, RenderGraph, LogicalExecutionGraph, PhysicalExecutionPlan or ETG semantic digests.

Billing `UsageRecord` is a different append-only business consumption fact. Observability metrics are projections only. Neither replaces host telemetry.

## C7 — Quota authority and lifecycle

Quota is a workspace/user/tenant/business policy limit, owned by entitlement quota authority. It is not hardware capacity and cannot assert that a runtime has resources.

A quota decision may block admission after technical `CAN_RUN` is established, but the result is typed as `POLICY_QUOTA_DENIED`, not hardware incompatibility. Deprecated in-memory quota-billing authority is a CLEAN FORWARD delete-shadow after caller migration.

## C8 — Cost authority and lifecycle

Cost is derived accounting, pricing or future optimization input owned by billing/cost authority. It is not feasibility authority and must not alter static compatibility, hardware eligibility, capacity or reservation arithmetic.

A hard budget policy may independently deny an otherwise technically feasible admission. That is a business-policy result, not `CAN_RUN=NO`. Roadmap #23 may compare cost only over the already feasible candidate set.

## C9 — Runtime, host, device and implementation identities

The following identities are pairwise distinct:

`WorkerRuntimeId != PhysicalHostId != ProviderImplementationId != DeviceId`

- WorkerRuntime: executable endpoint and incarnation.
- PhysicalHost: capacity/device owner and host incarnation.
- ProviderImplementationId: one provider runtime/adapter implementation identity.
- DeviceId: one provider-neutral physical/partitioned device identity.

One physical host may expose multiple devices and host multiple worker runtimes. A provider implementation may be installed/available on multiple runtimes. A runtime may host multiple compatible provider implementations. No universal worker/provider/device identity or giant nullable object is allowed.

## C10 — Provider and capability identity separation

`ProviderId != ProviderImplementationId` and `CapabilityId != CapabilityImplementationId` remain frozen.

Provider identity does not define semantic capability identity. Capability implementation identity does not become provider implementation identity. The existing `ProviderBindingPin` remains the single immutable executable-binding authority. Runtime installation/observation identity is not silently folded into these IDs.

## C11 — Provider-local runtime dependency ownership

`PROVIDER_RUNTIME_DEPENDENCY_SET_IS_IMPLEMENTATION_LOCAL_V1` is authoritative.

Each concrete ProviderImplementation owns the compatibility requirements for its runtime dependency bundle. FFmpeg CPU, FFmpeg NVIDIA, FFmpeg Intel QSV/VAAPI, BMF CPU/GPU and GStreamer implementations may require different and mutually unequal native dependency bundles.

No dependency equality across providers is required. Only declared requirement versus observed bundle conformance is required.

## C12 — Minimum typed runtime dependency model

Phase20 implementation may introduce only the following bounded types (names may adapt without changing authority):

1. `RuntimeDependencyRequirement` — immutable implementation-local requirement containing a typed dependency coordinate, version/compatibility constraint, optional ABI constraint, required feature set and required build/runtime flag set.
2. `RuntimeDependencyObservation` — freshness-bound probe observation tied to exact ProviderImplementationId, WorkerRuntimeId and optional DeviceId.
3. `RuntimeDependencyFingerprint` — canonical operational fingerprint of the observed implementation-local bundle, including exact dependency versions/ABI/features/build flags and probe schema.
4. `ProviderRuntimeBundleId` only if multiple stable installed bundles on one runtime require independent identity. It is not a package identity registry and is not automatically part of ProviderBindingPin.

The fingerprint digest is operational evidence. It is excluded from upstream semantic digests and ETG digest unless a later independently reviewed immutable binding contract explicitly pins a stable bundle identity. Mutable observations never participate.

The platform does not resolve, download, install or upgrade packages and does not become a general-purpose package manager.

## C13 — No global native-tool version authority

The following are forbidden:

- `GLOBAL_FFMPEG_VERSION`
- `GLOBAL_CUDA_VERSION`
- `GLOBAL_GSTREAMER_VERSION`
- `GLOBAL_BMF_VERSION`
- any global native-toolchain version authority

`NO_GLOBAL_NATIVE_TOOL_VERSION_AUTHORITY_V1` and `CONFORMANCE_NOT_VERSION_UNIFICATION_IS_THE_CROSS_PROVIDER_CONTRACT_V1` are frozen.

Cross-provider conformance is expressed through capability/contract compatibility, artifact boundaries and typed provider-local requirement/observation matching, never global version equality.

## C14 — Hardware/provider eligibility requirement dimensions

A provider implementation may declare a typed minimum requirement set over:

- CPU architecture;
- device/accelerator class;
- device vendor/model constraints only where implementation-specific and justified;
- accelerator availability and exact DeviceId at assignment time;
- driver/runtime API and ABI compatibility;
- device exposure and isolation mode;
- sandbox device/resource permissions;
- provider build features and enabled modules;
- codec, filter and hardware-acceleration feature availability;
- minimum reservable CPU, memory, temporary storage, VRAM, compute/media engines;
- implementation-local runtime dependency requirements.

Requirements are declared/frozen inputs. They are not probe observations and not current capacity.

## C15 — Runtime observation and probe boundary

A probe reports bounded evidence. It never becomes canonical domain authority.

- HostResourceAgent fingerprints static host/device capacity and reports observed usage.
- Provider probe reports exact implementation-local dependency/features/health observations.
- Sandbox capability detection reports enforceability.
- Heartbeat reports liveness/lease renewal evidence only.

A heartbeat is not free-resource authority. A sandbox limit is not resource-accounting authority. A process-level probe is not global provider availability authority. Unknown, stale, incomplete or mismatched critical evidence fails closed or requests a re-probe.

## C16 — Capability availability, runtime availability and compatibility decision

The following are separate:

- declared semantic capability requirement;
- declared ProviderImplementation capability/runtime requirements;
- observed runtime dependency/device/sandbox state;
- static compatibility decision;
- mutable runtime eligibility decision;
- policy admission decision.

Existing Stage 1 `CompatibilityKernel` remains pure over frozen plan/provider inputs. Existing Stage 2 `RuntimeEligibilityEvaluator` consumes exact Stage-1 proof plus mutable runtime/host/device/resource/sandbox/probe evidence.

The bounded conceptual `CompatibilityKernel` is the composition of these decision stages, not a new giant class and not an optimizer.

## C17 — Typed decision and incompatibility explanation

The technical output is a typed algebra:

`CAN_RUN | CANNOT_RUN | UNKNOWN_FAIL_CLOSED`

It carries stage and exact reasons. Required reason families include:

- static semantic/capability/contract/artifact incompatibility;
- `CPU_ARCHITECTURE_INCOMPATIBLE`;
- `DEVICE_CLASS_UNAVAILABLE`;
- `DEVICE_FEATURE_UNAVAILABLE`;
- `DRIVER_RUNTIME_INCOMPATIBLE`;
- `RUNTIME_DEPENDENCY_MISSING`;
- `RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE`;
- `RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE`;
- `PROVIDER_BUILD_FEATURE_MISSING`;
- `CODEC_OR_FILTER_FEATURE_MISSING`;
- `DEVICE_NOT_EXPOSED`;
- `SANDBOX_PERMISSION_UNAVAILABLE`;
- `INSUFFICIENT_RESERVABLE_RESOURCE`;
- `STALE_OBSERVATION`, `PROBE_UNKNOWN`, `PROBE_FAILED`;
- `RUNTIME_UNAVAILABLE` or `DEVICE_UNAVAILABLE`.

Quota/budget/trust policy denials are separate admission-policy reasons and never masquerade as technical incompatibility. Explanations must identify exact requirement/observation evidence without leaking secrets or host-local paths.

## C18 — Semantic digest exclusion

Mutable observations and runtime decisions are excluded from semantic digests, including:

- WorkerRuntimeId/host/device assignment;
- host/resource snapshot generation or timestamp;
- current capacity/free resource projection;
- reservation/lease/attempt;
- probe result/freshness;
- runtime dependency observation/fingerprint;
- heartbeat, queue, locality, utilization and telemetry;
- quota and cost.

They may be recorded in assignment/provenance/evidence with their own operational identity, but cannot mutate Timeline, OperationPlan, RenderPlan/RenderGraph, LogicalExecutionGraph, PhysicalExecutionPlan or ETG semantic identities.

## C19 — Sandbox, device exposure and resource authority boundary

Sandbox `ResourceEnforcementLimits` is an enforcement projection only. It does not grant capacity or create reservations. `DeviceExposurePolicy` carries only pre-authorized opaque device references and does not discover or own DeviceId.

Required flow: worker-fabric identifies eligible exact device and creates reservation/assignment; trust/policy authorizes exposure; sandbox proves and enforces the granted exposure and limits; local admission may still decline. Sandbox success cannot make an otherwise ineligible provider implementation eligible.

## C20 — Canonical provider feasibility flow

The canonical bounded flow is:

`ProviderImplementation frozen requirements`
-> `Stage-1 static CompatibilityKernel`
-> `compatible provider candidate`
-> `WorkerRuntime/PhysicalHost/Device and provider dependency observations`
-> `freshness + exact-identity validation`
-> `reservation-aware RuntimeEligibilityEvaluator`
-> `typed CAN_RUN decision`
-> `separate quota/trust/policy admission gates`
-> `bounded assignment/reservation`

Selection/optimization may only consume candidates whose compatibility and eligibility are proven. `PROVIDER_SELECTION_FAILS_CLOSED_ON_INCOMPATIBILITY_V1` remains mandatory.

## C21 — Constraint Kernel boundary

`PROVIDER_COMPOSITION_IS_CONSTRAINT_SOLVING_NOT_UNIVERSAL_INTEROPERABILITY_V1`, `PARTIAL_PROVIDER_COMPOSABILITY_IS_NORMAL_V1`, `CROSS_PROVIDER_OPTIMIZATION_OPERATES_ONLY_OVER_FEASIBLE_COMPATIBILITY_GRAPH_V1`, and `OPTIMIZATION_NEVER_CREATES_SEMANTIC_COMPATIBILITY_V1` are frozen.

Phase20 owns feasibility filtering and typed explanations. It does not own ranking, global score, fleet policy or optimal placement. Any compatibility graph is derived, bounded to one solve and ephemeral.

## C22 — One graph per authority boundary

`ONE_GRAPH_PER_AUTHORITY_BOUNDARY_V1` and `NO_PROVIDER_LOCAL_GRAPH_MIRRORING_V1` are frozen.

A platform graph exists only when it owns a distinct stable boundary. Derived candidate relations are views, not automatically canonical graph authorities. Provider-private execution structures remain inside provider implementations.

## C23 — Canonical graph simplification

The target graph stack is:

- Timeline / OperationPlan: semantic composition/process inputs, not provider runtime graphs.
- RenderGraph: provider-neutral render WHAT.
- LogicalExecutionGraph: last provider-neutral execution/dependency graph.
- ExecutableTaskGraph: coarse cross-provider/runtime dispatch, materialization, retry and observability boundary.

No additional platform graph is justified merely because a transformation can be drawn as nodes and edges.

## C24 — Provider-private internals are not canonical platform graphs

FFmpeg filtergraphs, BMF graphs, GStreamer pipelines, provider-local CUDA DAGs, provider-internal scheduler graphs and backend-internal fanout remain provider-private. They may be produced by PlanLowerer/ProviderNativeExecutionPlan and referenced as opaque provider execution evidence.

They must not be mirrored node-for-node into LogicalExecutionGraph, PhysicalExecutionPlan, ExecutableTaskGraph or a new canonical provider graph. `ProviderLocalTaskDependency` is traceability to original logical dependencies only, not a provider-private executable DAG authority.

## C25 — PhysicalExecutionPlan review result

Repository evidence shows the current PhysicalExecutionPlan:

- is provider/runtime/worker/device neutral;
- preserves one LogicalExecutionNode to one PhysicalPlanUnit;
- has its own schema/digest but no independent provider/resource binding;
- is consumed by 15 production paths with 43 external occurrences.

Decision:

`PHYSICAL_EXECUTION_PLAN_REVIEW_RESULT=PHYSICAL_EXECUTION_PLAN_COLLAPSE_OR_DOWNGRADE_CANDIDATE`

It is not deleted, rewritten or retroactively removed from Roadmap #21 in this task. Before later reconciliation, an independent architecture migration must prove whether it has invariants/transformation value not already owned by LogicalExecutionGraph. If not, consumers migrate to a downgraded projection/view or direct LogicalExecutionGraph input. Historical digests remain historical evidence; no history rewrite.

## C26 — ProviderCompatibilityGraph review result

The current ProviderCompatibilityGraph has a schema, canonical codec/digest and downstream proof coupling. V2 requires compatibility graphs to be solver-derived ephemeral views, not long-lived canonical authorities.

Decision:

`PROVIDER_COMPATIBILITY_GRAPH_REVIEW_RESULT=MIGRATE_REDESIGN_TO_EPHEMERAL_DERIVED_VIEW`

No immediate deletion is authorized. Implementation must migrate proof consumers and retain typed CompatibilityDecision evidence without preserving a second canonical graph identity or semantic digest.

## C27 — H1/H2 ownership boundary

H1 owns:

- resource taxonomy and accounting;
- WorkerRuntime/PhysicalHost/Device identities;
- implementation-local runtime dependency requirement/observation/fingerprint model;
- hardware/provider eligibility;
- capacity/reservation/observed-usage boundaries;
- bounded probe semantics;
- feasibility/CompatibilityKernel resource inputs and outputs;
- graph-boundary and PhysicalExecutionPlan review decisions in this contract.

H2 owns:

- BMF provider-local lowering;
- BMF private graph/runtime model;
- BMF cross-runtime media/effect conformance evidence.

H2 must consume the frozen H1 types. If H2 needs to change any H1 authority, it records exact evidence and `CROSS_LANE_RECONCILIATION_REQUIRED`; it must not mutate shared authority independently. At this freeze, no concrete H2 requirement has been presented, so `CROSS_LANE_RECONCILIATION_REQUIRED=NONE_AT_FREEZE`.

## C28 — CLEAN FORWARD decisions

The 36-row disposition ledger is authoritative for this bounded recovery:

- `REUSE_AS_CANONICAL=24`
- `MIGRATE_REDESIGN=5`
- `DELETE_SHADOW=2`
- `REUSE_MECHANICS_ONLY=2`
- `DEFER=3`
- `UNCLASSIFIED=0`

No compatibility wrapper is permitted merely to preserve unshipped shadow authority. Real callers migrate first; exact zero-use and persistence/external-compatibility proof precede deletion.

Priority conflicts:

1. unused `ExecutionResourceRequirement` -> delete shadow;
2. runtime support/probe payload -> typed dependency redesign;
3. ProviderCompatibilityGraph -> ephemeral derived view;
4. PhysicalExecutionPlan -> later collapse/downgrade migration decision;
5. legacy render-farm worker/lease -> migrate real callers then delete shadow;
6. deprecated quota-billing -> existing retirement path, no Phase20 dependency.

## C29 — Implementation phase plan and GO/NO-GO

After independent acceptance only:

- P20-I0: verify accepted contract SHA/tree and establish implementation candidate from the accepted canonical base; no architecture drift.
- P20-I1: CLEAN FORWARD requirement authority (`ExecutionRequirement` canonical, RuntimeResourceDemand derived, delete zero-use shadow after proof).
- P20-I2: add bounded implementation-local RuntimeDependencyRequirement/Observation/Fingerprint types and exact matching; no package manager.
- P20-I3: add typed CPU/device/driver/build-feature/codec/sandbox requirement-observation conformance and extend typed failure algebra.
- P20-I4: integrate Stage-1 static proof with Stage-2 runtime/resource/dependency eligibility while preserving reservation-first arithmetic and policy separation.
- P20-I5: migrate ProviderCompatibilityGraph consumers to an ephemeral derived view with typed proof evidence.
- P20-I6: execute separately reviewed PhysicalExecutionPlan collapse/downgrade reconciliation if and only if independent review accepts the migration plan; otherwise retain it with explicit value.
- P20-I7: migrate legacy/shadow callers, add fail-closed guards and targeted tests, prove Phase19 zero-awareness and #22/#23 boundary.
- P20-I8: run justified expensive gates once on frozen candidate, independent review, publication authorization.

`READY_FOR_PHASE20_IMPLEMENTATION=YES` means the bounded implementation phases are specified and blockers are zero. It does not grant execution authorization. `IMPLEMENTATION_AUTHORIZATION=NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE` remains controlling.

## C30 — Architecture escalation conditions

Escalate and stop implementation if any of the following is observed:

1. Provider-local dependency conformance cannot be expressed without a global version/package authority.
2. Hardware eligibility requires mutable observations in any semantic digest.
3. H2 requires shared H1 authority mutation or BMF private graph promotion into a platform graph.
4. PhysicalExecutionPlan has a proven independent invariant/transformation that contradicts collapse/downgrade.
5. Removing ProviderCompatibilityGraph canonical identity breaks a persisted/external contract rather than internal proof mechanics.
6. WorkerRuntime/PhysicalHost/Device identity separation cannot represent a real deployment without identity aliasing.
7. Reservation-first correctness requires observed utilization to mint capacity.
8. Technical CAN_RUN cannot remain separate from quota/cost/global optimization.
9. Phase20 needs global scheduling, distributed optimization, OpenCue internal placement or Roadmap #23 authority.
10. Any implementation change restores concrete FFmpeg awareness to render-module or reopens Phase19.

Current repository review triggers none of these conditions. `ARCHITECTURE_ESCALATION=NONE`.

## 2. Validation obligations for implementation

Cheap gates first:

1. exact base/topology and allowed-path proof;
2. contract/ledger guard and mutation RED suite;
3. module dependency/import guards;
4. global native-version authority zero scan;
5. shadow authority exact-symbol scan;
6. targeted requirement/dependency/hardware/eligibility tests;
7. architecture drift;
8. only then justified compile/integration/full gates on frozen implementation candidate.

Decision Recovery itself must not run the full serial suite solely for docs/governance changes.

Required future invariants include:

- `RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0`
- `GLOBAL_NATIVE_TOOL_VERSION_AUTHORITY_COUNT=0`
- `MUTABLE_OBSERVATION_SEMANTIC_DIGEST_PARTICIPATION_COUNT=0`
- `WORKER_RUNTIME_PHYSICAL_HOST_PROVIDER_IMPLEMENTATION_DEVICE_IDENTITY_COLLAPSE_COUNT=0`
- `QUOTA_AS_CAPACITY_AUTHORITY_COUNT=0`
- `COST_REDEFINES_FEASIBILITY_COUNT=0`
- `HEARTBEAT_FREE_RESOURCE_AUTHORITY_COUNT=0`
- `SANDBOX_LIMIT_RESOURCE_ACCOUNTING_AUTHORITY_COUNT=0`
- `PROVIDER_PRIVATE_GRAPH_CANONICAL_MIRROR_COUNT=0`
- `PROVIDER_COMPATIBILITY_GRAPH_LONG_LIVED_CANONICAL_AUTHORITY_COUNT=0` after its migration phase
- `ROADMAP_23_OPTIMIZER_IN_PHASE20_COUNT=0`

## 3. Decision Recovery final boundary

This candidate freezes architecture only. No Phase20 implementation is complete or authorized. No push or canonical integration is authorized by this document.

Final state for independent review:

`ROADMAP_22_PHASE20_DECISION_RECOVERY=PASS`

`PHASE20_BOUNDED_ARCHITECTURE_CONTRACT=FROZEN`

`READY_FOR_PHASE20_IMPLEMENTATION=YES`

`IMPLEMENTATION_AUTHORIZATION=NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE`

`BLOCKERS=0`

`ARCHITECTURE_ESCALATION=NONE`
