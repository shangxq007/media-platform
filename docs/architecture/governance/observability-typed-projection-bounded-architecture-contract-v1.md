---
type: bounded-architecture-contract
name: OBSERVABILITY_TYPED_PROJECTION_BOUNDED_ARCHITECTURE_CONTRACT_V1
status: FROZEN
date: 2026-08-29
base: e02579181ba3049ae65ed81080c93a7212f5833d
base_tree: b67136e3a4b4e08688091bad0c4dad30d841978d
authority: OBSERVABILITY_TYPED_PROJECTION_AND_RUNTIME_VISIBILITY_DECISION_RECOVERY_V1
implementation_authorization: NO_GO
---

# OBSERVABILITY_TYPED_PROJECTION_BOUNDED_ARCHITECTURE_CONTRACT_V1

H9_DECISION_RECOVERY=PASS  
OBSERVABILITY_PROJECTION_CONTRACT=FROZEN  
IMPLEMENTATION_AUTHORIZATION=NO_GO  
BLOCKED_ON_H1_FREEZE=YES  
READY_FOR_IMPLEMENTATION_AFTER_H1=YES  
CROSS_LANE_RECONCILIATION_REQUIRED=YES

This contract freezes the observability boundary and the shape of the read-side
projection work. It does not freeze, create, or rename any H1 runtime fact. It
does not authorize production, test, build, configuration, migration, UI, or
runtime changes. Hermes owns candidate commit and freeze. H1 remains the source
of runtime truth; H5 remains the source of commercial truth.

## 1. Frozen laws

The following laws are normative and are not implementation suggestions:

1. `OBSERVABILITY_IS_DERIVED_PROJECTION_NOT_RUNTIME_AUTHORITY_V1`.
2. `METRIC_IS_NOT_CANONICAL_RUNTIME_STATE_V1`.
3. `HEARTBEAT_IS_NOT_RESOURCE_CAPACITY_AUTHORITY_V1`.
4. `PROBE_IS_BOUNDED_OBSERVATION_NOT_AVAILABILITY_AUTHORITY_V1`.
5. `LOG_STATUS_STRING_IS_NOT_DOMAIN_STATE_V1`.
6. `AGGREGATION_MAY_NOT_REINTERPRET_SOURCE_FACT_V1`.
7. `UNKNOWN_OR_STALE_RUNTIME_FACT_FAILS_CLOSED_IN_H1_NOT_IN_OBSERVABILITY_V1`.
8. `CAPACITY_RESERVED_AND_OBSERVED_ARE_THREE_DISTINCT_DIMENSIONS_V1`.
9. `OBSERVABILITY_NEVER_REBINDS_PROVIDER_WORKER_HOST_DEVICE_OR_EXECUTION_V1`.
10. `PROVIDER_PRIVATE_RUNTIME_INTERNALS_ARE_DIAGNOSTIC_ONLY_V1`.

Observability may read, join, filter, redact, localize, count, and display
stable typed facts. It may not create eligibility, availability, compatibility,
capacity, reservation, assignment, retry, pricing, quota, entitlement, or
billing decisions.

## 2. Scope, evidence baseline, and instruction precedence

The inspected worktree was the authorized linked worktree on
`agent/observability-runtime-projection-decision-recovery` at exact
`BASE_SHA=e02579181ba3049ae65ed81080c93a7212f5833d` and
`BASE_TREE=b67136e3a4b4e08688091bad0c4dad30d841978d`.

Only repository-root `AGENTS.md` applies to the changed document path. Its
governance-only scope, non-destructive Git rules, evidence requirements, and
change-scope rules agree with the Owner packet. No nested instruction file
applies and no instruction conflict was found. One pre-existing stash belongs
to another branch and is untouched.

Inspected surfaces include `observability-module`, `worker-fabric-module`,
`remote-render-worker`, `provider-plugin-runtime-module`, `render-module`,
`scheduler-module`, `cloud-resource-module`, `sandbox-isolation-module`,
`audit-compliance-module`, `user-analytics-module`, `platform-app`, frontend
observability/operator surfaces, and the concrete FFmpeg runtime probe used to
test the provider-private diagnostics boundary.

The mechanical inventory and disposition ledger is:

`docs/architecture/governance/observability-runtime-concept-disposition-ledger-v1.tsv`

Ledger row identity is a semantically cohesive concept, not every textual
occurrence. A source symbol may have two rows only when it carries two distinct
roles that require different classifications; for example, heartbeat receipt
is a `HEALTH_SIGNAL`, while CPU/GPU/free-disk fields sent in that heartbeat are
a `SHADOW_RESOURCE_AUTHORITY`. Every inventoried concept has exactly one
classification per row.

## 3. Repository reality inventory

| Surface | Repository reality | Frozen disposition |
|---|---|---|
| observability-module | Trace correlation is bounded. Overview is an untyped map. Provider SLA, circuit, incident, and usage records mix metrics, health labels, runtime control, and H5 cost/quota fields. | Retain trace/log/metric mechanics; replace provider/runtime and commercial shadows with typed inputs. |
| worker-fabric-module | Typed WorkerRuntime, PhysicalHost, Device, Capacity, Reservation, ObservedUsage, probe, eligibility, assignment, attempt, and observation sources exist. | Treat only as H1-owned inputs; wait for the exact H1 freeze before projection code. |
| remote-render-worker | Owns a second in-memory worker registry, heartbeat, concurrency arithmetic, worker status, and string render lifecycle. | Clean-forward migration to H1 facts and render application status. |
| provider-plugin-runtime-module | Typed contribution retains provider binding and runtime-support requirement; host owns plugin load/disable/unload lifecycle. | H1 consumes provider contribution facts; operator view may show host lifecycle only as application diagnostics. |
| render-module | Canonical render application state exists, but legacy provider health/selection, farm worker/lease, remote dispatcher, resource, retry, queue, quota, metering, and pricing authorities remain. | Preserve render application facts; eliminate or reduce H1/H5 shadows after upstream freezes. |
| scheduler-module | Scheduled job/run lifecycle plus overview counts. | Application status and metrics only; never WorkerRuntime or execution-attempt truth. |
| cloud-resource-module | Catalog of buckets, queues, functions, CDN, provider codes; `CloudBucket.status` is a raw application field. | Keep separate from H1 compute capacity/device/reservation views. |
| sandbox-isolation-module | Probe-created sandbox capabilities and bounded process/cleanup observations; typed failure codes. | Bounded observation and failure inputs only; no availability, device identity, or capacity authority. |
| audit-compliance-module | Audit logs/events are projections. Usage anomaly service invents GPU cost, tenant tier, and execution-degrade recommendations. | Retain audit projection; consume H5 facts and remove commercial decision shadow. |
| user-analytics-module | User behavior event log and ingestion counter. | Log/metric only; no runtime state reconstruction. |
| platform-app | Health/readiness signals, raw remote callback maps, status-count metrics, and operator controllers. | Keep health signals bounded; type callback/projection APIs; never promote health endpoint strings to H1 facts. |
| frontend | Multiple incompatible render status vocabularies, unbounded `unknown` diagnostic payloads, metric models, and a fabricated latency histogram. | One typed projection API; real measured histograms; bounded diagnostic references. |
| ffmpeg-provider-module | Sandboxed `ffmpeg -version` produces exact version/build evidence and an H1-shaped probe result. | Provider-private version/configuration is diagnostic; only the H1-normalized observation may affect H1 eligibility. |

Representative evidence:

- Worker/host/device identities are explicitly distinct in
  `docs/architecture/governance/roadmap-22-shared-worker-fabric-provider-local-composition-amendment-1.md:107`.
- Capacity, reservation, and observation are explicitly distinct at the same
  contract's lines 158-176; observability sources may project telemetry but may
  not mutate reservation or provider support at lines 188-208.
- `ExecutionAssignment` omits provider rebinding in
  `worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/ExecutionAssignment.java:10`.
- H1 runtime eligibility is typed and fail-closed in
  `worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/RuntimeEligibilityDecision.java:10`.
- The current frontend invents histogram buckets from one average in
  `frontend/src/observability/components/SystemMetrics.tsx:95`.
- The current dispatcher makes callback strings change terminality and free
  slots in `render-module/src/main/java/com/example/platform/render/infrastructure/remote/RemoteRenderDispatcher.java:104`.

## 4. Classification and shadow ledger summary

The ledger has `TOTAL_ROWS=86` and the following mechanically derived counts:

| Classification | Count |
|---|---:|
| CANONICAL_FACT_FROM_H1 | 14 |
| DERIVED_PROJECTION | 5 |
| METRIC | 9 |
| TRACE | 4 |
| LOG | 5 |
| HEALTH_SIGNAL | 5 |
| BOUNDED_OBSERVATION | 5 |
| APPLICATION_STATUS | 9 |
| SHADOW_RUNTIME_AUTHORITY | 16 |
| SHADOW_RESOURCE_AUTHORITY | 5 |
| COMMERCIAL_SHADOW | 7 |
| DEFER | 2 |
| UNCLASSIFIED | 0 |

`STRING_STATUS_SEMANTIC_AUTHORITY_COUNT=9`. This is an orthogonal ledger flag,
not a second classification. It identifies raw strings that currently control
or embody readiness, incident state, provider circuit state, remote worker/job
state, callback terminality, retry/fallback, or commercial mitigation.

### 4.1 Runtime authority shadows

The 16 runtime shadows are not all inside `observability-module`. They include:

- the hard-coded provider catalog, SLA-to-health promotion, and circuit state
  in observability;
- render-local provider maturity/compatibility, health registries, health
  cooldowns, selection, fallback, and the second ProviderRuntimeEngine;
- render-farm worker lifecycle, lease/attempt/provider eligibility;
- remote worker registry, remote render lifecycle, dispatcher worker/job
  status, and raw callback transport;
- exception-message parsing that decides retry or provider fallback.

The count is an inventory count, not an implementation authorization. No row
is deleted or migrated in H9.

### 4.2 Resource authority shadows

The five resource shadows are legacy CPU/RAM/GPU/disk declarations on render
worker registration and heartbeat, plus three independent
`maxConcurrentJobs - activeJobs` availability calculations. Heartbeat may
carry observation, but cannot author static capacity or reservation state.
`activeJobs < maxConcurrentJobs` is concurrency bookkeeping, not proof of
schedulable CPU, RAM, GPU, VRAM, encoder, decoder, disk, or reservation
availability.

### 4.3 String status rule

Strings may be labels, localized messages, vendor payloads, log attributes, or
transport values. Before a string can influence a platform decision, the
owning domain must normalize and validate it into its typed state or failure
algebra. Observability must never compare a log/callback string to decide that a
worker is available, an execution is terminal, a failure is retryable, or a
provider is compatible.

## 5. H1 ownership map

H1 exclusively owns the canonical definitions and semantics below. The names
`RuntimeDependencyRequirement`, `RuntimeDependencyObservation`,
`RuntimeDependencyFingerprint`, and `ProviderRuntimeBundleId` are upstream H1
freeze inputs named by the Owner packet; H9 deliberately does not invent their
fields.

| H1 fact family | Current repository evidence | Allowed H9 consumer |
|---|---|---|
| RuntimeDependencyRequirement | Worker runtime support requirement and sandbox/process requirements are current partial inputs. | ProviderRuntimeView, CompatibilityExplanationView after exact H1 type freeze. |
| RuntimeDependencyObservation | ProviderProbeResult, SandboxRuntimeCapabilities, bounded FFmpeg probe are current observations. | Show source, outcome, observed-at, freshness, and diagnostic ref without deciding availability. |
| RuntimeDependencyFingerprint | Current worker support evidence and FFmpeg exact build evidence demonstrate the need; exact H1 identity is not frozen here. | Display opaque fingerprint/reference only. |
| ProviderRuntimeBundleId | ProviderPluginContribution retains an exact ProviderBindingPin and creates a runtime binding. | ProviderRuntimeView may reference the H1 bundle ID; it may not compose a new ID. |
| WorkerRuntime | WorkerRuntimeId, incarnation, descriptor, support advertisement, availability. | WorkerRuntimeView. |
| PhysicalHost | PhysicalHostId, incarnation, descriptor, location, host resource snapshot. | WorkerRuntimeView host reference and DeviceView host reference. |
| Device | DeviceId, descriptor, availability, device capacity, reservation, observed usage. | DeviceView. |
| Capacity | CapacitySnapshot and SchedulableCapacity arithmetic. | Show static and H1-derived schedulable values with provenance; never recompute from metrics. |
| Reservation | Reservation, ReservedResources, recovery hold, assignment reservation IDs. | WorkerRuntimeView, DeviceView, ExecutionView. |
| ObservedUsage | ObservedUsage and typed CPU/memory/device usage. | WorkerRuntimeView and DeviceView as observation, clearly separated from capacity/reserved. |
| RuntimeEligibility | RuntimeEligibilityDecision and RuntimeEligibilityReason. | ProviderRuntimeView and CompatibilityExplanationView copy the typed decision/reasons. |
| Provider compatibility | ProviderBindingPin → runtime support requirement/advertisement → RuntimeEligibility. | CompatibilityExplanationView; observability does not rank or choose providers. |
| Assignment and execution | ExecutionAssignment, ExecutionAttempt, ExecutionObservation. | ExecutionView and RuntimeFailureView. |

The H1 freeze must settle exact upstream types, identifiers, status/reason
algebras, freshness semantics, and event/read ports. Until then, projection
implementation is blocked. Existing worker-fabric classes are evidence and
candidate sources, not authorization for H9 to declare H1 frozen.

## 6. H5 ownership map

Commercial usage, billable usage, provider cost, price, quota, billing,
budget, tier, discount, and entitlement are H5 facts. H9 may define where a
commercial projection joins the operator screen, but not its source schema or
calculation.

| Current shadow | Why it is H5 | H9 disposition |
|---|---|---|
| ProviderUsageMetric cost/quota fields and ThirdPartyProviderHealthService accumulators | Locally estimates cost and mutates quota. | Split operational request metric from an H5-sourced commercial view. |
| RenderQuotaService/QuotaUsageRepository | Owns default limit and usage ledger in render. | Replace with H5 quota fact/decision. |
| Render-local PricingEngine | Owns provider price, GPU/effect multipliers, discounts, currency, final price. | Delete in favor of H5 pricing/billing authority. |
| Render-local MeteringService | Owns in-memory consumption facts. | Emit through the H5 usage boundary or retain only a non-billable operational metric with explicit name. |
| Audit UsageAnomalyDetectionService | Invents GPU cost, tenant tier, and changes recommended preset. | Consume H5 facts; anomaly output may remain a read-side/risk projection only. |
| RenderJobExecutionService default cost and local quota | Hard-coded reservation estimate and local quota consumption affect execution. | Delegate exclusively to H5 typed decisions. |

Runtime projection implementation may begin after H1 freeze without adding
commercial widgets. Any price/quota/billing/entitlement widget additionally
waits for the H5 freeze.

## 7. Frozen typed projections

These are repository-conventional read models to implement only after H1
freeze. Exact Java package, API transport, persistence, and field types must
reuse the frozen upstream types. The descriptions below freeze information
content and authority direction, not duplicate domain classes.

### 7.1 ProviderRuntimeView

Purpose: answer which provider runtime bundle is being considered, what exact
runtime dependency requirement applies, what was observed, whether the
observation is fresh, and what H1 eligibility decision/reasons resulted.

Required content: H1 ProviderRuntimeBundleId/reference; ProviderBindingPin;
requirement reference; observation reference/outcome/observed-at; fingerprint
reference; H1 RuntimeEligibility decision and reasons; bounded diagnostic
references. It contains no locally computed `healthy`, `available`,
`compatible`, circuit state, price, or quota.

### 7.2 WorkerRuntimeView

Purpose: answer whether the stable worker runtime and its current incarnation
are known, reachable per H1, bound to a physical host when applicable, assigned
to an execution, and covered by current resource/reservation evidence.

Required content: WorkerRuntimeId and incarnation; lifecycle kind; optional
PhysicalHostId/incarnation; H1 availability fact and observed-at/freshness;
static support evidence reference; current assignment references; host resource
snapshot generation; reservation summary reference. It must not sum provider
advertisements into host capacity.

### 7.3 DeviceView

Purpose: show exact device identity and answer whether the execution is bound
to it and which distinct resource facts apply.

Required content: DeviceId, kind/vendor/model; PhysicalHostId/incarnation; H1
availability; static capacity; reserved resources including recovery hold;
observed usage and timestamp; assignment references. The three resource
sections must remain separate. `freeGpuPercent` or `freeVram` derived solely
from utilization is forbidden.

### 7.4 ExecutionView

Purpose: show provider-bound task, backend-neutral attempt, assignment,
ownership generation, worker/host/device/reservation bindings, observed
backend state, and timestamps.

Required content: render job/task/attempt IDs; ProviderBindingPin reference;
ExecutionAttemptState; backend kind and safe handle reference; assignment and
ownership generation; worker/host/device/reservation IDs; normalized execution
observation; application correlation IDs. It cannot use callback status as a
second execution state machine.

### 7.5 RenderProgressView

Purpose: show render application lifecycle and stage progress without
reconstructing execution truth from logs or metrics.

Required content: RenderJobStatus; ordered typed stages/steps; start/completion
timestamps; active stage; artifact/completion reference when authoritative;
typed failure reference. A percentage is permitted only when the owning render
contract supplies an explicit, stable numerator/denominator. Arbitrary callback
percentages and synthetic progress are diagnostics, not canonical progress.

### 7.6 CompatibilityExplanationView

Purpose: answer “why unavailable or ineligible?” without becoming the decision
maker.

Required content: exact bound provider; H1 requirement/observation/fingerprint
references; H1 RuntimeEligibility status; ordered typed reasons; relevant
worker/host/device/probe/reservation fact references; localized safe messages
derived one-to-one from typed reasons. Aggregation may group equal typed reasons
but may not replace, collapse, reorder semantically, or invent a broader reason.

### 7.7 RuntimeFailureView

Purpose: preserve failure layer, typed identity, retry disposition from its
owning authority, and safe diagnostics.

Required content: correlation and attempt IDs; failure layer; owning typed
failure code/reason; source timestamp; terminal/non-terminal fact from the
owning state machine; typed retry disposition when one exists; bounded,
redacted diagnostic reference. It must distinguish provider compatibility,
runtime eligibility, worker, host, device, reservation, sandbox, backend,
render application, and H5 commercial-policy failures. It may not infer retry
from exception text.

## 8. H4 and operator UI decisions

All requested operator questions are justified, with these authority-safe
answers:

| Operator question | Projection answer | Required caveat |
|---|---|---|
| Observable? | ProviderRuntimeView plus WorkerRuntimeView show source facts and freshness. | Absence is `UNKNOWN`, never healthy/available by default. |
| Why unavailable? | CompatibilityExplanationView shows the exact H1 decision and typed reasons. | UI prose is explanatory only. |
| Worker/device bound? | ExecutionView links exact assignment to WorkerRuntimeId, PhysicalHostId, and DeviceId set. | No inference from hostname, log tag, or provider name. |
| Execution stage/failure? | RenderProgressView plus RuntimeFailureView. | Render application status and backend attempt status remain distinct. |
| Probe stale? | ProviderRuntimeView shows probe outcome, observed-at, H1 freshness, and reason. | The UI does not calculate a new freshness threshold. |
| Reserved/observed resource? | WorkerRuntimeView/DeviceView render Capacity, Reserved, and Observed sections. | No “free” value from telemetry; H1-derived schedulable capacity may be shown verbatim. |

Recommended screen composition:

1. Runtime availability panel: ProviderRuntimeView +
   CompatibilityExplanationView.
2. Worker/device binding panel: ExecutionView + WorkerRuntimeView + DeviceView.
3. Execution panel: ExecutionView + RenderProgressView + RuntimeFailureView.
4. Resource panel: separate capacity, reserved/recovery-hold, schedulable, and
   observed sections with timestamps and provenance.
5. Diagnostics drawer: trace/log/probe references, redacted and access
   controlled; never the default state source.

The existing frontend status mismatch (`PROCESSING`, `RUNNING`, and
`EXECUTING` appear in separate surfaces) must be removed by one server-owned
typed mapper. UI filters, polling, colors, and actions consume the same
application projection vocabulary.

## 9. Status and error taxonomy

There is no universal `Status` or `Health` mega-enum.

| Layer | Status/failure authority | Projection rule |
|---|---|---|
| Render application | RenderJobStatus and typed render-step state | Copy exact value; do not substitute backend attempt state. |
| Backend execution | H1 ExecutionAttemptState and ObservedExecutionState | Show separately from render progress. |
| Runtime eligibility | H1 RuntimeEligibilityDecision/Reason | Copy status and full reason set; unknown fails closed upstream. |
| Worker/host/device | H1 availability and incarnation facts | Always display identity, incarnation, timestamp, freshness. |
| Provider probe | H1 normalized probe outcome | Probe result is evidence, not availability. |
| Sandbox | SandboxFailureCode and bounded observation | Label failure layer `SANDBOX`; exit zero is not completion proof. |
| Render orchestration | RenderResultFailureReason | Label failure layer `RENDER_APPLICATION` or the frozen owning layer. |
| Commercial policy | H5 typed denial/failure | No local parsing or reclassification. |
| Incident management | Typed incident application status | Never substitute incident open/resolved for runtime eligibility. |

Human message, stack trace, stdout/stderr, provider response body, log level,
HTTP status, and vendor code are diagnostics. If an operator action depends on
them, the owning boundary must first normalize them into a frozen typed code.

## 10. Metric versus state rules

Every metric series/read model must state name, scope, unit, aggregation,
window, measured-at time, and source. Counters, gauges, histograms, summaries,
and ratios retain their metric kind.

- A success rate cannot become provider availability.
- Queue depth cannot become capacity.
- CPU/GPU utilization cannot become schedulable/free resource.
- `activeJobs` cannot substitute for reservations.
- A heartbeat timestamp cannot replace host/runtime incarnation or capacity.
- A probe result cannot become compatibility without the H1 evaluator.
- A count grouped by string status is a metric projection, not a new state
  authority.
- An average cannot be expanded into a fabricated histogram. The existing
  frontend behavior is explicitly scheduled for deletion.
- Metrics may alert or annotate. Any fail-closed runtime veto must occur through
  an H1-owned typed policy over H1 observations, not in the metric aggregator.

Aggregation is limited to truthful set/count/sum/min/max/quantile/window
operations defined for the source series. It may not merge distinct reason
codes, change units silently, treat missing as zero, turn stale into healthy,
or turn observations into reservations/capacity.

## 11. Logs, traces, and events

### Logs

Logs are append-only diagnostics. Required correlation keys are trace,
request, tenant, project, job, workflow, task/attempt where available. Secret,
credential, raw private provider payload, host path, and unrestricted stdout/
stderr are forbidden. Log text is never replayed into domain state.

### Traces

Traces record causal timing and already-made typed decisions. Provider
decision traces may list candidates and the selected binding as historical
diagnostics, but cannot be read back as provider selection authority. State
transition traces derive from the owning typed transition event.

### Events

Domain/application events remain facts of their owning domain. Audit and user
analytics handlers produce log/projection records only. Observability events
may carry a typed source fact reference and alert metadata; they cannot author
render completion, execution terminality, runtime availability, reservation
release, billing, or entitlement.

Delivery duplication, delay, loss, or reordering must not change canonical
state. Projection consumers are idempotent and expose freshness/lag rather than
guessing the missing state.

## 12. Provider-private diagnostics

FFmpeg version line and build configuration, BMF operator graph/node details,
vendor process flags, raw command lines, provider stderr, and provider-native
response payloads are private implementation diagnostics.

- H1 may normalize a bounded provider observation/fingerprint from them.
- Operator UI may show a redacted diagnostic reference under explicit access.
- They do not enter provider compatibility reason text directly.
- BMF is deferred because no current production BMF runtime fact source exists;
  BMF operators remain internal as already recorded by
  `docs/architecture/adr/ADR-006-bmf-integration.md:24`.
- FFmpeg's current exact probe correctly states that it is never eligibility or
  capacity authority at
  `ffmpeg-provider-module/src/main/java/com/example/platform/ffmpeg/FfmpegRuntimeProbeResult.java:7`.

## 13. Authorized future phases — not executed

No phase below is authorized by this contract alone.

### Phase 0 — upstream freeze gates

Require exact H1 freeze for all runtime fact types, IDs, reasons, freshness,
read/event ports, and ProviderRuntimeBundleId. Require H5 freeze before any
commercial projection. Freeze an exact candidate SHA before verification.

### Phase 1 — pure projection contracts

Add bounded read models equivalent to the seven views in Section 7. Reuse H1/
H5 types or explicit read-only references. No write ports and no state machine.

### Phase 2 — source adapters

Consume H1 typed facts/events and render application facts. Normalize no raw
status in observability. Add freshness/lag metadata without new eligibility
policy.

### Phase 3 — API and authorization

Expose typed operator endpoints with tenant/role filtering, pagination,
redaction, bounded diagnostics, explicit unknown/stale behavior, and stable
error contracts.

### Phase 4 — H4 frontend

Replace incompatible status vocabularies and unbounded `unknown` payloads.
Implement the panels in Section 8 and consume only the typed API.

### Phase 5 — clean-forward shadow retirement

Move callers to H1/H5 sources, prove zero consumers, then delete obsolete
provider health/selection, worker registry, resource arithmetic, remote
callback lifecycle, message retry parsing, quota, pricing, and metering
shadows. No dual authority or compatibility track remains.

### Phase 6 — metrics/traces/logs

Wire truthful measured series and bounded traces/logs. Replace the synthetic
latency histogram with real histogram data or remove it. Verify that telemetry
loss changes only observability freshness, not canonical state.

## 14. Required implementation guards

Future implementation must add repository-conventional automated guards for:

1. Zero definitions in observability/frontend projection code for
   RuntimeDependencyRequirement, RuntimeDependencyObservation,
   RuntimeDependencyFingerprint, ProviderRuntimeBundleId, WorkerRuntime,
   PhysicalHost, Device, Capacity, Reservation, ObservedUsage,
   RuntimeEligibility, or Provider compatibility.
2. Zero observability writes to H1/H5/render state repositories, reservation
   ledgers, assignment boundaries, completion boundaries, or billing ledgers.
3. Zero `Map<String,Object>` or raw `status`/`state` strings on operator runtime
   APIs.
4. Zero log/exception-message parsing for status, retry, fallback,
   compatibility, availability, or completion.
5. Zero resource capacity/free calculation from heartbeat, queue depth,
   active-job count, or utilization metric.
6. Zero metric-to-state and probe-to-availability conversions outside H1.
7. Capacity, reserved/recovery-hold, schedulable, and observed fields remain
   distinct in schema, mapper, API, and UI.
8. Unknown, absent, stale, and failed observations remain distinguishable.
9. Typed source reason sets survive aggregation and localization unchanged.
10. Provider-private BMF/FFmpeg details appear only behind bounded, redacted
    diagnostic references.
11. Commercial fields originate only from H5 typed facts.
12. Ledger `UNCLASSIFIED=0`, duplicate row ID count zero, exact path count
    equal total row count, and classification count arithmetic equal total.

Acceptance after implementation also requires the H1/H5 owners to confirm
that no projection type has become a write or decision authority.

## 15. Freeze decision and blockers

The bounded projection architecture is frozen. Implementation is `NO_GO`
because H1 has not frozen the exact types named by the Owner packet. Runtime
projection implementation is ready to begin after that exact freeze and a new
explicit implementation authorization. Commercial projection work separately
waits for H5.

Cross-lane reconciliation is required for:

- H1 exact type/name/freshness/event/read-port alignment;
- clean-forward ownership of the 16 runtime and five resource shadows;
- H5 ownership and retirement of seven commercial shadows;
- H4 operator schema and UI replacement of incompatible status vocabularies;
- provider-plugin runtime bundle alignment and provider-private diagnostic
  redaction.

No production phase was executed. No production source, test source, build
file, application configuration, database migration, generated file, skill,
memory, or agent configuration was changed.
