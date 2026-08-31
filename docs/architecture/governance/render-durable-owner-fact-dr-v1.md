# Render Durable Owner-Fact Decision Review V1

## 1. Review status and frozen endpoint

This document materializes the already-decided Render durable owner-fact contract for review. It reconciles the frozen correlation, progress, failure, and schema-impact decisions into one self-contained repository document; it does not make a new architecture decision or authorize implementation.

The governing contracts are frozen:

- `RENDER_OWNS_RENDER_SIDE_EXECUTION_CORRELATION_V1`
- `RENDER_OWNS_DURABLE_PROGRESS_FACT_V1`
- `RENDER_OWNS_DURABLE_FAILURE_FACT_V1`
- `PROGRESS_PERCENTAGE_REQUIRES_OWNER_DENOMINATOR_V1`
- `RAW_RENDER_STRING_IS_NOT_TYPED_FAILURE_AUTHORITY_V1`

The current durability verdicts remain:

- `RENDER_CORRELATION_DURABILITY=OWNER_FACT_NOT_DURABLY_AVAILABLE`
- `RENDER_PROGRESS_DURABILITY=STATUS_DURABLE__NUMERIC_PROGRESS_UNKNOWN__COMPLETION_CONTRACT_INCOMPLETE`
- `RENDER_FAILURE_DURABILITY=RAW_STRING_PRESENT_BUT_TYPED_OWNER_FACT_ABSENT`
- `P04_RENDER_CORRELATION_DECISION=NOT_DURABLY_AVAILABLE__CONTRACT_FROZEN__SCHEMA_REQUIRED`
- `P05_RENDER_PROGRESS_DECISION=OWNER_DENOMINATOR_ABSENT__CONTRACT_FROZEN__SCHEMA_REQUIRED`
- `P07_RENDER_FAILURE_DECISION=RAW_STRING_PRESENT_BUT_TYPED_OWNER_FACT_ABSENT__CONTRACT_FROZEN__SCHEMA_REQUIRED`
- `CONTRACT_FROZEN=YES`
- `READY_FOR_RENDER_OWNER_FACT_IMPLEMENTATION=NO`

Readiness is `NO` only because schema and implementation are not authorized here and the central V1 local lock has not been released. The contracts are frozen for a separately authorized implementation.

## 2. Render identity and H1 execution correlation

### 2.1 Render-owned identities

The durable owner contract distinguishes three Render identities:

1. `RenderJobId` identifies one Render job. The current database has only the untyped durable `render_job.id` string. A retry created through `base_job_id` is a new job and therefore has a different `RenderJobId`.
2. `RenderTaskId` identifies one Render-owned unit within a job. No such production type or durable owner relation exists today. A `PipelineTask.taskId` is plan topology and is not a `RenderTaskId` unless a future owner implementation deliberately materializes that mapping.
3. `RenderAttemptId` identifies one Render-owned attempt for a particular Render task. No such production type or durable owner relation exists today. A lease id and a mutable attempt ordinal are not a `RenderAttemptId`.

The identity relationship is exactly one Render job to its Render-owned tasks, and one Render task to its Render-owned attempts. The primary correlation key is `(RenderJobId, RenderTaskId, RenderAttemptId)`. These identities must not be reconstructed from plan, lease, provider, trace, timing, or latest-row data.

### 2.2 Exact H1 references

H1 owns `ExecutableTaskId`, `ExecutionAttemptId`, `ExecutionOwnershipGeneration`, `ExecutionAttemptState`, `WorkerRuntimeId`, `DeviceId`, and `ReservationId`, plus assignment, runtime, device, and reservation facts. Render references H1; it does not duplicate H1's mutable/runtime authority.

No `ProviderRuntimeBundleId` is defined or used by this contract.

Each correlated Render attempt stores the exact H1 tuple:

- `ExecutableTaskId executableTaskId`
- `ExecutionAttemptId executionAttemptId`
- `ExecutionOwnershipGeneration ownershipGeneration`

`ProviderBindingPin` is resolved from the exact immutable `ExecutableTaskId` through the H1/ETG task owner. `render_job.selected_provider` and `trace_id` are raw observability strings and must never reconstruct the pin or establish execution correlation. `ExecutionAttemptState`, `WorkerRuntimeId`, `DeviceId`, and `ReservationId` are H1 reads and are never persisted as Render authority.

The immutable future `RenderExecutionCorrelationSnapshot` contains:

- `RenderJobId renderJobId`
- `RenderTaskId renderTaskId`
- `RenderAttemptId renderAttemptId`
- `ExecutableTaskId executableTaskId`
- `ExecutionAttemptId executionAttemptId`
- `ExecutionOwnershipGeneration ownershipGeneration`
- `ProviderBindingPin providerBindingPin`, resolved exactly from the H1/ETG task owner
- `ExecutionAttemptState executionAttemptState`, read from H1
- optional `WorkerRuntimeId workerRuntimeId`, read from H1
- canonically ordered `Set<DeviceId> deviceIds`, read from H1
- canonically ordered `Set<ReservationId> reservationIds`, read from H1
- `Instant correlatedAt`, the Render owner time when the exact relation was established

The read service joins by the exact `(executionAttemptId, executableTaskId, ownershipGeneration)` tuple. Missing, stale, or inconsistent H1 data fails closed. It must not select `MAX(generation)`, latest-by-time, current active lease, or any other fallback. H1 `ExecutionAttemptState.FAILED` does not itself make a Render job failed; Render records its own typed outcome.

The future Render-owned relation is `render_execution_correlation`:

| Column | Contract |
|---|---|
| `render_job_id` | `varchar(64) not null`, foreign key to `render_job(id)` with delete restricted |
| `render_task_id` | `varchar(128) not null` |
| `render_attempt_id` | `varchar(128) not null` |
| `executable_task_id` | `char(64) not null`, constrained to lowercase SHA-256 |
| `execution_attempt_id` | `varchar(128) not null` |
| `execution_ownership_generation` | `bigint not null`, greater than zero |
| `correlated_at` | `timestamptz not null` |

Its primary key is `(render_job_id, render_task_id, render_attempt_id)`. `execution_attempt_id` is unique. The exact H1 tuple has a foreign key to `wf_execution_attempt(attempt_id, task_id, generation)`. There are deliberately no columns for execution state, worker runtime, device, reservation, provider placement, provider health, or availability. Central implementation must provide the durable H1/ETG task-to-binding read authority before the full snapshot can be served.

## 3. Durable Render status and progress

### 3.1 Typed status authority and transition order

`RenderJobStatus` is the canonical Render job lifecycle algebra:

`QUEUED -> SELECTING_PROVIDER -> PROVIDER_SELECTED -> EXECUTING -> COMPLETING -> COMPLETED`

The exact non-idempotent transition graph is:

| From | Allowed targets |
|---|---|
| `QUEUED` | `SELECTING_PROVIDER`, `CANCELLED`, `REJECTED` |
| `SELECTING_PROVIDER` | `PROVIDER_SELECTED`, `FAILED`, `CANCELLED` |
| `PROVIDER_SELECTED` | `EXECUTING`, `FAILED`, `CANCELLED` |
| `EXECUTING` | `COMPLETING`, `FAILED`, `CANCELLED` |
| `COMPLETING` | `COMPLETED`, `FAILED` |
| `COMPLETED` | none |
| `FAILED` | none |
| `CANCELLED` | none |
| `REJECTED` | none |

`render_job.status` is durable current status, but its database column is currently an unconstrained string. `render_job_status_history` is not authoritative replay history: some writers bypass it, `occurred_at` alone does not totally order equal timestamps, and its status values are unconstrained strings.

The future owner history assigns a strictly increasing `transition_sequence`, unique within each `render_job_id`; uses the exact typed from/to status domain; and records `timestamptz occurred_at`. The current-status update and history append are one atomic transaction.

### 3.2 Stage model and active semantics

There is no separate canonical progress-stage type. The lawful V1 stage projection is status-based and ordered as follows:

- pending: `QUEUED`
- active semantic stages: `SELECTING_PROVIDER`, `PROVIDER_SELECTED`, `EXECUTING`, `COMPLETING`
- terminal outcomes, not active stages: `COMPLETED`, `FAILED`, `CANCELLED`, `REJECTED`

`PipelineStage` open string names, `PipelineTaskType` plan topology, and persisted pipeline/wave JSON are partial best-effort operational telemetry. They are not canonical progress stages and cannot invent job progress, ordering, completion, or failure authority.

### 3.3 Owner timestamps

The future job snapshot has owner-written `createdAt`, optional `startedAt`, `updatedAt`, optional `terminalAt`, and optional `completedAt`.

- `startedAt` is fixed on the first transition into execution work and is never recomputed.
- `terminalAt` is present for `COMPLETED`, `FAILED`, `CANCELLED`, and `REJECTED`, and absent for pending or active status.
- `completedAt` exists only for `COMPLETED`; every non-completed status forbids it.
- A terminal failure uses the typed failure occurrence time as its owner terminal time.

### 3.4 Completion and canonical Artifact reference

`RenderJobStatus.COMPLETED` is the semantic successful terminal status, but the current row has neither `completed_at` nor a required canonical output `ArtifactId`. The legacy `artifact_uri`, a pipeline-stage artifact string, optional `artifact.render_job_id` provenance, provider return value, or event publication cannot establish authoritative completion.

Completion requires this order:

1. Canonical Artifact authority commits the output successfully and yields an `ArtifactId`.
2. Render durably binds that exact `ArtifactId` as `output_artifact_id` for the job.
3. Render atomically records `COMPLETING -> COMPLETED`, the transition sequence, and the same owner-consistent `completed_at` and `terminal_at`.

`FAILED`, `CANCELLED`, and `REJECTED` have `terminalAt` but never `completedAt`. An artifact commit failure occurs before completion and cannot coexist with `completedAt`.

### 3.5 Percentage availability

`PROGRESS_PERCENTAGE_REQUIRES_OWNER_DENOMINATOR_V1` applies, and the current endpoint is:

`RENDER_PROGRESS_PERCENTAGE=UNKNOWN`

Render currently has neither a durable numerator nor a durable owner denominator. `plan.tasks().size()`, `PipelineResult.stages().size()`, `stageCount`, status ordinal, wave index, elapsed time, and hardcoded stage counts are forbidden numerators or denominators.

The optional future `RenderProgressMeasure` is valid only when Render freezes an attempt-scoped work manifest and durably records:

- `RenderAttemptId renderAttemptId`
- `long completedWorkUnits`, where `0 <= completedWorkUnits <= totalWorkUnits`
- immutable `long totalWorkUnits`, where `totalWorkUnits > 0`
- `String workUnitContractVersion`, identifying the owner-defined unit semantics and immutable within the attempt
- `long progressSequence`, strictly increasing per attempt
- `Instant measuredAt`

H9 may calculate a percentage from that exact fraction only when the measure exists and belongs to the exact attempt. When it is absent, malformed, or belongs to another attempt, H9 returns `UNKNOWN`. There is no fallback, string inference, status-to-percentage table, partial-stage count, or heuristic progress.

The proposed optional persistence consists of `render_attempt_progress_current`, keyed by `render_attempt_id`, and append-only `render_attempt_progress_history`, keyed by `(render_attempt_id, progress_sequence)`. Both carry the exact Render job/task/attempt relation, completed and total work units, contract version, sequence, and `timestamptz measured_at`; both enforce the fraction constraints and reference the exact `render_execution_correlation` attempt key.

## 4. Durable Render failure

### 4.1 Typed failure authority

The closed Render job-owner failure algebra is:

- `RENDER_EXTENT_UNPROVEN`
- `RENDER_EXTENT_NOT_ACHIEVED`
- `FONT_PREFLIGHT_FAILED`
- `SCRIPT_RESOLUTION_FAILED`
- `PROVIDER_SELECTION_FAILED`
- `STEP_FAILED`
- `ORCHESTRATION_ERROR`
- `ARTIFACT_COMMIT_FAILED`
- `STALE_EXECUTION_TIMEOUT`
- `WORKER_EXECUTION_LOST`
- `LEASE_EXPIRED`
- `UNCLASSIFIED`

`UNCLASSIFIED` is selected explicitly at write time when the owner lacks a narrower typed cause. It is not reconstructed from text. Provider-native and H1 failure types stay with their owners and may be referenced as detail; substring, regex, exception-class-name, and message-text conversion are forbidden.

Current typed values such as `RenderResultFailureReason`, `ProviderBindingFailureReason`, `RenderExecutionPlanFailureReason`, and `LocalExecutionPlanFailureReason` are ephemeral or belong to narrower scopes. They are not the durable Render job-failure algebra. `FailureClassificationEngine.FailureType` is heuristic and is prohibited for owner-fact recovery.

### 4.2 Failure identity, code, reason, and terminality

The immutable future `RenderFailureSnapshot` contains:

- `RenderFailureId failureId`
- `RenderJobId renderJobId`
- optional `RenderTaskId renderTaskId`
- optional `RenderAttemptId renderAttemptId`
- `RenderFailureType failureType`
- `boolean causedJobTerminal`
- `Instant occurredAt`
- optional raw `detailCode`
- optional safe explanatory `reason`

`failureId` identifies the append-only owner failure fact. `failureType` is the authoritative closed code. `detailCode` and `reason` are optional non-authoritative detail and never select or override the type.

The terminality rules are:

1. A job-level `causedJobTerminal=true` failure and the `RenderJobStatus.FAILED` transition are written atomically with the same owner time and transition sequence.
2. At most one failure is the terminal cause for a Render job. Earlier task/attempt failures remain append-only evidence and may precede retry.
3. Task/attempt failures reference the exact Render identities from the correlation contract.
4. `COMPLETED` and a terminal Render failure are mutually exclusive.
5. `REJECTED` and `CANCELLED` are distinct terminal outcomes, not failure types. Commercial admission rejection requires its own typed rejection reason when projected and must not masquerade as execution failure.

Future persistence uses `RenderFailureType.name()` under a database check constraint containing exactly the closed values above. Readers use exact value lookup only, making type-to-persisted mapping injective and persisted-to-type mapping total for supported rows. Raw detail never participates in type selection.

The proposed `render_job_failure` owner table has `failure_id` as its primary key; required `render_job_id`; optional exact `render_task_id` and `render_attempt_id`; positive `failure_sequence` unique within the job; constrained `failure_type`; required `caused_job_terminal` and `timestamptz occurred_at`; and optional `detail_code` and `safe_reason`. A partial unique index permits only one terminal-cause row per job. Present task/attempt keys reference the exact correlation relation. Terminal failure append and the job's `FAILED` transition use one transaction and one owner time.

### 4.3 Existing raw values

Existing raw strings remain raw detail. Legacy rows without a new constrained failure row expose typed failure as absent. No backfill may classify `error_message`, `error_code`, `reason`, `failure_reason`, lifecycle-event text, or any other string. Known literals do not make an unconstrained column typed.

## 5. Exact clean-forward disposition

`DEFAULT_DISPOSITION=CLEAN_FORWARD`

The following ledger covers every current raw status/error persistence surface identified by the frozen current-state record. The disposition governs the surface's authority, not a text-to-type backfill.

| Current persistence surface | Exact disposition | Clean-forward target |
|---|---|---|
| `render_job.status` raw `varchar(32)` | `REUSE_AS_CANONICAL` | This is the durable current-status surface and preserves the `RenderJobStatus` semantic algebra. Canonical reuse requires the exact database constraint and atomic current-status plus ordered-history writes; unconstrained strings do not create any additional status values. |
| `render_job_status_history.from_status` / `to_status` | `MIGRATE_REDESIGN` | Constrain both to the exact status domain and add owner `transition_sequence` for total per-job ordering. The current partial history is not replay authority. |
| `render_job_status_history.error_code` / `reason` | `MIGRATE_REDESIGN` | Treat existing values only as raw diagnostic detail. Typed failure moves to constrained `render_job_failure`; no value is inferred or backfilled from these strings. |
| `render_job.error_message` | `MIGRATE_REDESIGN` | Preserve legacy content only as raw detail with typed failure absent; future authoritative failure is the constrained owner record with optional safe reason. |
| `render_job_lease.failure_error_code` / `failure_reason` | `MIGRATE_REDESIGN` | Keep legacy values only as lease-local raw diagnostics. They neither identify a Render attempt nor establish the Render failure type or terminal outcome. |
| `render_job_lifecycle_events.reason_code` / `reason` | `MIGRATE_REDESIGN` | Keep existing best-effort event text non-authoritative; canonical status, transition order, and typed failure come only from the owner records. |
| `render_job.pipeline_execution_json` raw/open stage telemetry | `MIGRATE_REDESIGN` | Retain only bounded operational telemetry. It cannot supply canonical stages, progress units, completion, failure type, or execution correlation. |

Only the current-status surface is `REUSE_AS_CANONICAL`, and only for the closed `RenderJobStatus` semantics above. No raw error string is canonical. No duplicate status or failure store is authorized; any later-discovered shadow that cannot be migrated into one of the owner records has disposition `DELETE_SHADOW`. Legacy raw retention is diagnostic data preservation, not a compatibility authority and not typed truth.

## 6. Required schema impact and implementation boundary

Schema work is required in a separately authorized, serialized central change:

- add `render_execution_correlation` with the exact Render/H1 keys and constraints in section 2;
- harden `render_job.status` and add `started_at`, `terminal_at`, `completed_at`, and `output_artifact_id`;
- require `COMPLETED` to have `completed_at`, `terminal_at`, and `output_artifact_id`; forbid `completed_at` otherwise; require `terminal_at` for every terminal outcome and forbid it for pending/active status;
- harden status history with typed statuses, `timestamptz occurred_at`, and positive per-job `transition_sequence`;
- optionally add current and history attempt-progress authority with an immutable attempt denominator and unit contract; and
- add `render_job_failure` with exact closed failure typing, ordering, correlation, and one terminal-cause constraint.

The exact proposed `render_job` additions are:

| Column | Contract |
|---|---|
| `started_at` | `timestamptz null` |
| `terminal_at` | `timestamptz null` |
| `completed_at` | `timestamptz null` |
| `output_artifact_id` | `varchar(64) null`, foreign key to `artifact(id)` with delete restricted |

The `status` check contains exactly `QUEUED`, `SELECTING_PROVIDER`, `PROVIDER_SELECTED`, `EXECUTING`, `COMPLETING`, `COMPLETED`, `FAILED`, `CANCELLED`, and `REJECTED`. `COMPLETED` requires `completed_at`, `terminal_at`, and `output_artifact_id`; non-`COMPLETED` rows forbid `completed_at`; terminal outcomes require `terminal_at`; and pending/active rows forbid `terminal_at`.

The exact proposed status-history hardening adds positive `bigint transition_sequence`, requires `timestamptz occurred_at`, enforces uniqueness on `(job_id, transition_sequence)`, and constrains `from_status` and `to_status` to the exact status domain. Current-status update and history append remain one transaction.

The exact proposed `render_attempt_progress_current` columns are:

| Column | Contract |
|---|---|
| `render_attempt_id` | `varchar(128)`, primary key |
| `render_job_id` | `varchar(64) not null` |
| `render_task_id` | `varchar(128) not null` |
| `completed_work_units` | `bigint not null`, at least zero and no greater than `total_work_units` |
| `total_work_units` | `bigint not null`, greater than zero and immutable within the attempt |
| `work_unit_contract_version` | `varchar(64) not null`, immutable within the attempt |
| `progress_sequence` | `bigint not null`, greater than zero and strictly increasing per attempt |
| `measured_at` | `timestamptz not null` |

It has an exact attempt-key foreign key to `render_execution_correlation`. `render_attempt_progress_history` carries the same measure and uses `(render_attempt_id, progress_sequence)` as its primary key. There is no status-ordinal, stage-count, elapsed-time, or wave-index fallback.

The exact proposed `render_job_failure` columns and constraints are:

| Column | Contract |
|---|---|
| `failure_id` | `varchar(128)`, primary key |
| `render_job_id` | `varchar(64) not null`, foreign key to `render_job(id)` with delete restricted |
| `render_task_id` | `varchar(128) null` |
| `render_attempt_id` | `varchar(128) null` |
| `failure_sequence` | `bigint not null`, greater than zero |
| `failure_type` | `varchar(64) not null`, checked against exactly the closed failure algebra in section 4 |
| `caused_job_terminal` | `boolean not null` |
| `occurred_at` | `timestamptz not null` |
| `detail_code` | `varchar(128) null` |
| `safe_reason` | `text null` |

It enforces uniqueness on `(render_job_id, failure_sequence)`, a partial unique index permitting one `caused_job_terminal` row per job, and an exact correlation foreign key when task/attempt identity is present. Terminal-failure append and the `FAILED` transition remain one transaction and one owner time.

This review lane freezes the following implementation boundary:

- `RENDER_SCHEMA_CHANGE_REQUIRED=YES`
- `CENTRAL_V1_JOOQ_LOCAL_LOCK=WAIT_FOR_STORAGE_RELEASE`
- `IMPLEMENTATION=WAIT_FOR_CENTRAL_V1_LOCAL_LOCK`
- `R1_IMPLEMENTATION=WAIT_FOR_CENTRAL_V1_LOCAL_LOCK`
- `R2_IMPLEMENTATION=WAIT_FOR_CENTRAL_V1_LOCAL_LOCK`
- `R3_IMPLEMENTATION=WAIT_FOR_CENTRAL_V1_LOCAL_LOCK`
- `RENDER_SCHEMA_SOURCE_CHANGE_COUNT=0`
- `RENDER_JOOQ_CHANGE_COUNT=0`
- `RENDER_PRODUCTION_DURABILITY_IMPLEMENTATION_COUNT=0`
- `H9_PRODUCTION_PROJECTION_IMPLEMENTATION_COUNT=0`
- `H7_MODIFICATION_COUNT=0`

The central implementation, only after lock release and separate authorization, must modify the accepted schema through the serialized V1 process, regenerate jOOQ rather than hand-edit generated code, add owner-side typed readers/writers with transactional constraints and tests, and coordinate the exact H1/ETG binding authority and foreign keys.

For this materialization, modification counts are also zero for `V1__initial_schema.sql`, generated jOOQ, all production source, all test source, build/configuration, H7, and H9 projections. Introduced counts are zero for fallback selection, string inference, heuristic progress, and raw-string failure authority. No public API or runtime behavior changes are authorized.
