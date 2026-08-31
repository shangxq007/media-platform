# Sandbox durable outcome decision recovery V1

```text
STATUS=CONTRACT_FROZEN
MATERIALIZATION=REVIEW_ONLY
BASE_SHA=f7fe6c0ab9b53694909ddb077420c0792b08e937
SANDBOX_DR=PASS
LANE_STATUS=WAIT_FOR_CENTRAL_V1_LOCAL_LOCK
```

This document materializes an already-decided Sandbox owner contract. It does
not make a new architecture decision, authorize persistence, or implement an
H7 or H9 projection. The contract separates mutable process evidence from the
immutable owner fact recorded for one exact Sandbox execution attempt.

## 1. Authority boundary

```text
EPHEMERAL_SANDBOX_PROCESS_RESULT != DURABLE_SANDBOX_EXECUTION_OUTCOME
SANDBOX_TYPED_FAILURE_EXISTS_EPHEMERALLY_V1=YES
SANDBOX_DURABLE_OUTCOME_IS_OWNER_FACT_V1=YES
SANDBOX_FAILURE_CODE_IS_RECORDED_AT_ORIGIN_NOT_RECONSTRUCTED_V1=YES
```

`SandboxExecutionHandle`, `SandboxExecutionObservation`, and
`SandboxExecutionResult` are ephemeral process mechanics and observations.
The current typed `SandboxFailure`, `SandboxFailureCode`, exact
`missingCapabilities`, and typed cleanup failure exist at the execution
origin, but no durable Sandbox outcome writer or reader exists at this freeze.
Mapping an ephemeral result to an exception does not make it durable.

The Sandbox owner must eventually create one immutable terminal outcome from
the typed resolution and execution values at the owner composition boundary.
This includes resolution rejection and launch or setup failure before a PID
exists. No later adapter, projection, H9 reader, or migration may reconstruct
owner truth from text or mutable observations.

The durable record is Sandbox execution-safety truth only. It does not author
provider compatibility; WorkerRuntime, PhysicalHost, Device, Reservation, or
usage identity; Artifact staging or commit; executable-task completion; reuse
winners; capacity; semantic digests; or H7/H9 projection authority.

## 2. Stable attempt identity and H1 relation

The stable identity is frozen as:

```text
SandboxExecutionAttemptId = (
  executionAttemptReference: existing ExecutionAttemptId value,
  executionCommandSequence: existing ExecutionCommand.sequence
)
```

Its relational identity is the composite primary key
`(execution_attempt_id, command_sequence)`. `execution_attempt_id` is a foreign
key to the H1/Worker Fabric authority `wf_execution_attempt(attempt_id)`.
`command_sequence` is the existing non-negative authored command coordinate
within the already-scoped `RuntimeExecutionBundle`; it is not a database row
number or insertion order.

H1 remains the sole owner of `ExecutionAttemptId` and the Worker Fabric
execution lifecycle. Sandbox references that identity; it does not mint a
Sandbox lifecycle identity, duplicate the H1 attempt, or copy task,
ownership-generation, runtime, device, reservation, or provider-runtime-bundle
authority. `ProviderRuntimeBundleId` is not defined by this contract.

Because `sandbox-isolation-module` is dependency-neutral while Worker Fabric
depends on it, a future Java `SandboxExecutionAttemptId` must be a neutral
reference type. The Worker Fabric composition boundary is the only authorized
constructor from `ExecutionCommand.platformExecutionAttemptId().value()` and
`ExecutionCommand.sequence()`. The database foreign key provides durable
referential proof.

Command sequence must be unique in one `RuntimeExecutionBundle`. A future
implementation rejects duplicates before execution and never lets persistence
order select a winner. A platform retry uses the retry's distinct existing
`ExecutionAttemptId`; it does not silently reuse an earlier composite ID.

The following are explicitly rejected as identity: PID; process launch time;
trace ID; owner start or finish time; the legacy `sandbox_execution_job.id`;
generated row order; a new surrogate; list index; or a duplicate task,
ownership-generation, WorkerRuntime, Device, Reservation, or provider bundle
identifier.

## 3. Immutable terminal outcome

The future owner value is exactly:

```text
SandboxDurableExecutionOutcome
  attemptId: SandboxExecutionAttemptId
  startedAt: Instant
  finishedAt: Instant
  terminalOutcome: SUCCEEDED | FAILED
  exitCode: OptionalInt
  primaryFailureCode: Optional<SandboxFailureCode>
  missingCapabilities: Set<SandboxCapability>
  cleanupOutcome: NOT_REQUIRED | SUCCEEDED | FAILED
  cleanupFailureCode: Optional<SandboxFailureCode>
  diagnosticReference: Optional<SandboxDiagnosticReference>
```

`startedAt` is captured when the Sandbox owner begins processing the exact
attempt and command. It is not `SandboxExecutionHandle.launchedAt`.
`finishedAt` is captured only after primary execution and every required
cleanup obligation have reached the frozen terminal state. Both timestamps
are owner facts, and `finishedAt >= startedAt`. A working directory, elapsed
duration, PID, or timestamp-derived identifier is not durable owner truth.

The terminal invariants are:

- `SUCCEEDED` has neither a primary nor cleanup failure and, when a process
  exit code exists, that code is exactly `0`.
- `FAILED` has a typed primary failure, a typed cleanup failure, or both.
- A prelaunch capability or policy rejection is `FAILED`, preserves its exact
  typed primary failure, has no exit code, and is `cleanupOutcome=NOT_REQUIRED`
  when it created no cleanup obligation.
- A known process exit code is preserved exactly. An unknown exit remains
  absent; sentinel `-1` is never persisted as unknown.
- Process exit `0` is process evidence only. It is not Artifact availability,
  Artifact commit, executable-task completion, or provider success authority.
- The overall terminal outcome is `FAILED` whenever primary execution or
  cleanup failed. Neither failure slot overwrites the other.

## 4. Typed failure durability

`SandboxFailureCode` is a closed enum with these exact values:

```text
SANDBOX_UNAVAILABLE
SANDBOX_CAPABILITY_UNSUPPORTED
SANDBOX_POLICY_UNSATISFIABLE
SANDBOX_SETUP_FAILED
PROCESS_LAUNCH_FAILED
PROCESS_TERMINATED_BY_LIMIT
PROCESS_TIMEOUT
PROCESS_CRASHED
FILESYSTEM_POLICY_VIOLATION
NETWORK_POLICY_VIOLATION
SECRET_INJECTION_FAILED
PRIVILEGE_SETUP_FAILED
DEVICE_EXPOSURE_FAILED
OUTPUT_STAGING_FAILED
SANDBOX_CLEANUP_FAILED
SANDBOX_RUNTIME_LOST
```

The owner writes the exact enum value transactionally when it creates the
terminal outcome. `primary_failure_code` contains any non-cleanup primary
failure and may never contain `SANDBOX_CLEANUP_FAILED`.
`cleanup_failure_code` is absent unless cleanup failed, in which case its only
lawful value is `SANDBOX_CLEANUP_FAILED`. Both codes are retained when both
subfacts fail.

`SandboxFailure.message`, legacy `error_message`, stdout, stderr, exception
class or name, stack traces, diagnostic text, HTTP/status strings, and legacy
raw `status` are not classification inputs. Substring matching, regex, case
folding, error-message parsing, raw-string inference, and fallback
classification are forbidden. There is no dual typed/raw canonical read and
no string fallback.

## 5. Missing capabilities

`missingCapabilities` is an exact typed set. The constrained vocabulary is:

```text
PROCESS_TREE_CONTAINMENT
BEST_EFFORT_DESCENDANT_CLEANUP
WALL_CLOCK_TIMEOUT
FILESYSTEM_PATH_VALIDATION
FILESYSTEM_ACCESS_ISOLATION
NETWORK_NONE
NETWORK_ENDPOINT_ALLOWLIST
ENVIRONMENT_CLEARING
SECRET_INJECTION
BOUNDED_CAPTURE
CPU_COUNT_LIMIT
MEMORY_LIMIT
PROCESS_COUNT_LIMIT
OPEN_FILE_LIMIT
TEMPORARY_STORAGE_LIMIT
OUTPUT_STORAGE_LIMIT
UNPRIVILEGED_EXECUTION
HOST_EXPOSURE_DENIAL
DEVICE_NONE
DEVICE_GRANTS
```

For `SANDBOX_CAPABILITY_UNSUPPORTED`, the set is non-empty and exact. For
`SANDBOX_POLICY_UNSATISFIABLE`, it may be non-empty only when exact
capabilities explain the unsatisfied policy; otherwise it is empty. Every
other primary failure and every success has an empty set. Parent and child
facts are written in one transaction. Relational identity rejects duplicates;
ordering is not semantic, and typed reads return deterministic enum order.

## 6. Cleanup outcome

Cleanup is an independent typed subfact in the same immutable outcome. It is
not another lifecycle row and is not encoded in the primary failure message.

| Primary execution | Cleanup | Overall | `primaryFailureCode` | `cleanupFailureCode` |
|---|---|---|---|---|
| succeeds | succeeds or not required | `SUCCEEDED` | absent | absent |
| fails | succeeds or not required | `FAILED` | exact primary code | absent |
| succeeds | fails | `FAILED` | absent | `SANDBOX_CLEANUP_FAILED` |
| fails | fails | `FAILED` | exact primary code | `SANDBOX_CLEANUP_FAILED` |

`NOT_REQUIRED` means that no cleanup obligation was created and requires an
absent cleanup failure code. `SUCCEEDED` means that every required owner
cleanup obligation completed and also requires an absent cleanup failure
code. `FAILED` means cleanup was not proven complete, requires exactly
`SANDBOX_CLEANUP_FAILED`, and forces overall `FAILED`.

The future writer must form the record directly from both primary processing
and `SandboxExecutionObservation.cleanup.failure`. It must not infer cleanup
from the selected `SandboxExecutionResult.failure` or its message; current
single-slot selection can otherwise hide cleanup failure after a primary
failure.

Detailed `SandboxCleanupObservation` mechanics remain ephemeral: process and
engine PIDs, descendants, survivor lists, detached helpers, container name or
status, capture mechanics, booleans, and failure messages are not persisted as
owner facts. Normal execution finalizes cleanup before authoring the terminal
outcome. This does not allow Sandbox cleanup to overwrite separately fenced
Artifact or completion authority.

## 7. Bounded diagnostics and excluded payloads

`diagnosticReference`, when present, is an opaque nonblank reference of at
most 512 characters to independently governed diagnostic material. It is not
a filesystem path, stdout or stderr payload, `output_preview`,
`SandboxFailure.message`, `error_message`, exception name, stack trace,
container detail, survivor list, or unrestricted URI. Until an independent
diagnostic owner exists, the reference remains absent.

Unrestricted stdout, stderr, process capture, internal working/storage paths,
and raw diagnostic payloads are intentionally absent from the durable owner
record.

## 8. Exact current-table disposition

The existing V1 `sandbox_execution_job` table is unused extension-era logging,
not canonical Sandbox owner authority. Exact repository search at the frozen
base found no non-generated production reader or writer. Its only Java
surfaces are generated jOOQ metadata and records.

```text
CURRENT_TABLE=sandbox_execution_job
CURRENT_TABLE_DISPOSITION=DELETE_SHADOW
CLEAN_FORWARD_REPLACEMENT=sandbox_execution_outcome
```

Every existing column is classified as follows:

| Existing column | Exact disposition |
|---|---|
| `id` | `DELETE_SHADOW`; an unreferenced surrogate must not become attempt identity. |
| `extension_code` | `DELETE_SHADOW`; extension logging metadata is not Sandbox outcome authority. |
| `language` | `DELETE_SHADOW`; request/logging metadata is outside the terminal owner fact. |
| `script_hash` | `DELETE_SHADOW`; it is neither identity nor a semantic digest authority. |
| `status` | `DELETE_SHADOW`; free text is not `terminalOutcome` and must never be a canonical or fallback read. |
| `trace_id` | `DELETE_SHADOW`; correlation is non-unique and non-authoritative. |
| `tenant_id` | `DELETE_SHADOW`; old logging scope is not copied into the frozen attempt outcome. |
| `user_id` | `DELETE_SHADOW`; old logging actor metadata is not Sandbox execution-safety truth. |
| `timeout_ms` | `DELETE_SHADOW`; request configuration is not a terminal outcome field. |
| `started_at` | `DELETE_SHADOW`; no legacy value is carried forward. The replacement records a distinct owner-captured `started_at`. |
| `finished_at` | `DELETE_SHADOW`; no legacy value is carried forward. The replacement records a distinct owner-captured `finished_at` after cleanup. |
| `exit_code` | `DELETE_SHADOW`; no legacy value is copied. The replacement records the optional real typed-process result at origin. |
| `output_preview` | `DELETE_SHADOW`; output payload persistence is forbidden absent an independent need. |
| `error_message` | `DELETE_SHADOW`; messages never classify or backfill typed failure authority. |
| `created_at` | `DELETE_SHADOW`; row-creation mechanics are not the frozen owner timestamps. |

The three indexes `ix_sandbox_job_status`, `ix_sandbox_job_trace`, and
`ix_sandbox_job_extension` are deleted with the shadow table. The generated
`SandboxExecutionJob`, `SandboxExecutionJobRecord`, and associated generated
keys, indexes, and catalog references are removed only by central jOOQ
regeneration after the authorized V1 change.

`REUSE_REDIRECT` is rejected because there is no live reader or writer to
redirect and the table cannot express typed failure, missing-capability, or
cleanup authority. Redesign under the legacy table identity is rejected
because `job` would retain a second lifecycle implication. Clean-forward means
delete the shadow and introduce the purpose-named replacement after
`wf_execution_attempt`; it does not mean reuse, data copy, view, alias,
compatibility wrapper, dual write, dual typed/raw canonical read, fallback
reader, or raw-string classifier.

## 9. Exact proposed schema delta, not implemented

The frozen clean-forward V1 replacement, to be applied only after central lock
and separate implementation authorization, is:

```sql
create table sandbox_execution_outcome (
    execution_attempt_id varchar(128) not null,
    command_sequence int not null check (command_sequence >= 0),
    started_at timestamptz not null,
    finished_at timestamptz not null,
    terminal_outcome varchar(16) not null check (
        terminal_outcome in ('SUCCEEDED','FAILED')),
    exit_code int,
    primary_failure_code varchar(64) check (
        primary_failure_code in (
            'SANDBOX_UNAVAILABLE',
            'SANDBOX_CAPABILITY_UNSUPPORTED',
            'SANDBOX_POLICY_UNSATISFIABLE',
            'SANDBOX_SETUP_FAILED',
            'PROCESS_LAUNCH_FAILED',
            'PROCESS_TERMINATED_BY_LIMIT',
            'PROCESS_TIMEOUT',
            'PROCESS_CRASHED',
            'FILESYSTEM_POLICY_VIOLATION',
            'NETWORK_POLICY_VIOLATION',
            'SECRET_INJECTION_FAILED',
            'PRIVILEGE_SETUP_FAILED',
            'DEVICE_EXPOSURE_FAILED',
            'OUTPUT_STAGING_FAILED',
            'SANDBOX_RUNTIME_LOST')),
    cleanup_outcome varchar(16) not null check (
        cleanup_outcome in ('NOT_REQUIRED','SUCCEEDED','FAILED')),
    cleanup_failure_code varchar(64) check (
        cleanup_failure_code = 'SANDBOX_CLEANUP_FAILED'),
    diagnostic_reference varchar(512) check (
        diagnostic_reference is null or btrim(diagnostic_reference) <> ''),
    primary key (execution_attempt_id, command_sequence),
    foreign key (execution_attempt_id)
        references wf_execution_attempt (attempt_id),
    unique (execution_attempt_id, command_sequence, primary_failure_code),
    check (finished_at >= started_at),
    check ((terminal_outcome = 'SUCCEEDED'
                and primary_failure_code is null
                and cleanup_failure_code is null
                and (exit_code is null or exit_code = 0))
        or (terminal_outcome = 'FAILED'
                and (primary_failure_code is not null
                    or cleanup_failure_code is not null))),
    check ((cleanup_outcome = 'FAILED'
                and cleanup_failure_code = 'SANDBOX_CLEANUP_FAILED')
        or (cleanup_outcome in ('NOT_REQUIRED','SUCCEEDED')
                and cleanup_failure_code is null))
);

create index ix_sandbox_execution_outcome_finished
    on sandbox_execution_outcome (finished_at);
create index ix_sandbox_execution_outcome_terminal
    on sandbox_execution_outcome (terminal_outcome, finished_at);
create index ix_sandbox_execution_outcome_primary_failure
    on sandbox_execution_outcome (primary_failure_code)
    where primary_failure_code is not null;
create index ix_sandbox_execution_outcome_cleanup_failure
    on sandbox_execution_outcome (cleanup_failure_code)
    where cleanup_failure_code is not null;

create table sandbox_execution_outcome_missing_capability (
    execution_attempt_id varchar(128) not null,
    command_sequence int not null,
    primary_failure_code varchar(64) not null check (
        primary_failure_code in (
            'SANDBOX_CAPABILITY_UNSUPPORTED',
            'SANDBOX_POLICY_UNSATISFIABLE')),
    capability varchar(64) not null check (
        capability in (
            'PROCESS_TREE_CONTAINMENT',
            'BEST_EFFORT_DESCENDANT_CLEANUP',
            'WALL_CLOCK_TIMEOUT',
            'FILESYSTEM_PATH_VALIDATION',
            'FILESYSTEM_ACCESS_ISOLATION',
            'NETWORK_NONE',
            'NETWORK_ENDPOINT_ALLOWLIST',
            'ENVIRONMENT_CLEARING',
            'SECRET_INJECTION',
            'BOUNDED_CAPTURE',
            'CPU_COUNT_LIMIT',
            'MEMORY_LIMIT',
            'PROCESS_COUNT_LIMIT',
            'OPEN_FILE_LIMIT',
            'TEMPORARY_STORAGE_LIMIT',
            'OUTPUT_STORAGE_LIMIT',
            'UNPRIVILEGED_EXECUTION',
            'HOST_EXPOSURE_DENIAL',
            'DEVICE_NONE',
            'DEVICE_GRANTS')),
    primary key (execution_attempt_id, command_sequence, capability),
    foreign key (execution_attempt_id, command_sequence, primary_failure_code)
        references sandbox_execution_outcome (
            execution_attempt_id, command_sequence, primary_failure_code)
        on delete cascade
);
```

The parent and child inserts are one insert-only owner transaction. The store
enforces the missing-capability domain rules that cannot be expressed by the
proposed constraints without a trigger and rejects a conflicting replay.
Neither schema nor production implementation is part of this materialization.

## 10. Write, replay, and read laws

Writes are insert-once. Replay is idempotent only when every frozen field
exactly equals the existing row and typed child set for the same composite ID;
a conflicting second write fails closed.

The typed owner read returns the immutable snapshot only by exact composite
attempt ID. A second method may return the complete set for one exact existing
`ExecutionAttemptId`, ordered by command sequence. Missing data remains
unknown or absent. There is no `MAX`, latest-row, trace, timestamp, surrogate,
status, legacy-table, mutable-observation, recomputation, text, or exception
fallback.

H9 may later project this exact Sandbox owner fact only after authorized
production implementation. H9 does not own the upstream type, persistence,
classification, or fallback behavior, and this document adds no H9
projection.

## 11. Frozen implementation boundary

The schema change is required, but this review-materialization lane does not
hold the central V1/jOOQ lock and performs no implementation:

```text
SANDBOX_SCHEMA_CHANGE_REQUIRED=YES
CENTRAL_V1_JOOQ_LOCAL_LOCK=WAIT_FOR_STORAGE_RELEASE
SANDBOX_V1_CHANGE_COUNT=0
SANDBOX_JOOQ_CHANGE_COUNT=0
SANDBOX_DURABLE_OUTCOME_PRODUCTION_IMPLEMENTATION_COUNT=0
H9_PRODUCTION_PROJECTION_IMPLEMENTATION_COUNT=0
H7_MODIFICATION_COUNT=0
PRODUCTION_SOURCE_CHANGE_COUNT=0
TEST_SOURCE_CHANGE_COUNT=0
BUILD_FILE_CHANGE_COUNT=0
APPLICATION_CONFIG_CHANGE_COUNT=0
```

No V1 migration, generated jOOQ source, production source, test source, build
file, application configuration, H7 source, H9 source, persistence adapter,
public API, compatibility surface, or runtime behavior is changed here.

## 12. Contract freeze

```text
SANDBOX_ATTEMPT_IDENTITY_CONTRACT_FROZEN=YES
SANDBOX_DURABLE_OUTCOME_CONTRACT_FROZEN=YES
SANDBOX_TYPED_FAILURE_CONTRACT_FROZEN=YES
SANDBOX_MISSING_CAPABILITIES_CONTRACT_FROZEN=YES
SANDBOX_CLEANUP_OUTCOME_CONTRACT_FROZEN=YES
SANDBOX_CURRENT_TABLE_DISPOSITION_CONTRACT_FROZEN=YES
SANDBOX_SCHEMA_IMPACT_CONTRACT_FROZEN=YES
SANDBOX_ALL_CONTRACT_FIELDS_FROZEN=YES
READY_FOR_SANDBOX_DURABLE_OUTCOME_IMPLEMENTATION=YES
NEXT_ACTION=WAIT_FOR_CENTRAL_V1_LOCAL_LOCK_AND_SEPARATE_IMPLEMENTATION_AUTHORIZATION
```
