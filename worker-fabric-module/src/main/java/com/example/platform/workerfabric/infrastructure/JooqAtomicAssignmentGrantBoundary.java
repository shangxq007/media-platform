package com.example.platform.workerfabric.infrastructure;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantCommand;
import com.example.platform.workerfabric.domain.BackendExecutionHandle;
import com.example.platform.workerfabric.domain.CueJobId;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceResourceReservation;
import com.example.platform.workerfabric.domain.ExecutionAssignment;
import com.example.platform.workerfabric.domain.ExecutionAssignmentId;
import com.example.platform.workerfabric.domain.ExecutionAttempt;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionBackendSelectionAuthority;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.LeaseFencingToken;
import com.example.platform.workerfabric.domain.LeaseId;
import com.example.platform.workerfabric.domain.LeaseRenewalContract;
import com.example.platform.workerfabric.domain.NativeWorkerBackendExecutionHandle;
import com.example.platform.workerfabric.domain.OpenCueBackendExecutionHandle;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.RequestWorkFailureReason;
import com.example.platform.workerfabric.domain.RequestWorkId;
import com.example.platform.workerfabric.domain.RequestWorkResult;
import com.example.platform.workerfabric.domain.Reservation;
import com.example.platform.workerfabric.domain.ReservationId;
import com.example.platform.workerfabric.domain.ReservationKind;
import com.example.platform.workerfabric.domain.ReservationState;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.RemoteExecutionId;
import com.example.platform.workerfabric.domain.RemoteProviderExecutionHandle;
import com.example.platform.workerfabric.domain.RuntimeResourceDemand;
import com.example.platform.workerfabric.domain.TaskLease;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL/jOOQ implementation of ASSIGNMENT_GRANT_V1.
 *
 * <p>One jOOQ transaction creates the generation, attempt, assignment, reservation, Native Pull
 * lease, durable RequestWork resolution, and non-claimable task row. The task row is locked before
 * generation allocation; a durable PENDING request row serializes idempotent retries. PostgreSQL
 * constraints remain authoritative after application restart.
 */
@Repository
public class JooqAtomicAssignmentGrantBoundary implements AtomicAssignmentGrantBoundary {

    private static final String PENDING = "PENDING";
    private static final String GRANTED = "GRANTED";
    private static final String NO_WORK = "NO_WORK";
    private static final String REJECTED = "REJECTED";
    private static final String REPROBE_REQUIRED = "REPROBE_REQUIRED";

    private final DSLContext dsl;
    private final Clock clock;
    private final LeaseRenewalContract leaseRenewalContract;

    @Autowired
    public JooqAtomicAssignmentGrantBoundary(DSLContext dsl) {
        this(dsl, Clock.systemUTC(), LeaseRenewalContract.NATIVE_PULL_V1);
    }

    public JooqAtomicAssignmentGrantBoundary(
            DSLContext dsl,
            Clock clock,
            LeaseRenewalContract leaseRenewalContract) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseRenewalContract = Objects.requireNonNull(
                leaseRenewalContract, "leaseRenewalContract");
    }

    @Override
    public Optional<RequestWorkResult> findResolution(RequestWork requestWork) {
        Objects.requireNonNull(requestWork, "requestWork");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Optional<RequestWorkFailureReason> staleRegistration =
                    validateRegistrationAuthority(tx, requestWork);
            Record stored = tx.fetchOne(
                    """
                    select request_context_fingerprint, result_kind, failure_reason, assignment_id
                      from wf_request_work_resolution
                     where request_work_id = ?
                     for update
                    """,
                    requestWork.requestWorkId().value());
            if (stored == null) {
                return Optional.empty();
            }
            if (staleRegistration.isPresent()) {
                return Optional.of(new RequestWorkResult.Rejected(
                        requestWork.requestWorkId(), staleRegistration.orElseThrow()));
            }
            return Optional.of(toResult(tx, requestWork, stored));
        });
    }

    @Override
    public Optional<RequestWorkFailureReason> validateRegistration(RequestWork requestWork) {
        Objects.requireNonNull(requestWork, "requestWork");
        return dsl.transactionResult(configuration -> validateRegistrationAuthority(
                DSL.using(configuration), requestWork));
    }

    @Override
    public RequestWorkResult resolveTerminal(
            RequestWork requestWork,
            RequestWorkResult terminalResult) {
        Objects.requireNonNull(requestWork, "requestWork");
        Objects.requireNonNull(terminalResult, "terminalResult");
        if (!requestWork.requestWorkId().equals(terminalResult.requestWorkId())
                || terminalResult instanceof RequestWorkResult.Granted) {
            throw new IllegalArgumentException(
                    "terminal resolution must be non-granted and bind the exact RequestWorkId");
        }
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Optional<RequestWorkFailureReason> registrationFailure =
                    validateRegistrationAuthority(tx, requestWork);
            String fingerprint = RequestWorkContextFingerprint.of(requestWork);
            Optional<RequestWorkResult> existing = claimRequestResolution(
                    tx, requestWork, fingerprint);
            if (existing.isPresent()) {
                if (registrationFailure.isPresent()) {
                    return new RequestWorkResult.Rejected(
                            requestWork.requestWorkId(), registrationFailure.orElseThrow());
                }
                return existing.orElseThrow();
            }
            RequestWorkResult authoritativeTerminal = registrationFailure
                    .<RequestWorkResult>map(reason -> new RequestWorkResult.Rejected(
                            requestWork.requestWorkId(), reason))
                    .orElse(terminalResult);
            Terminal terminal = Terminal.from(authoritativeTerminal);
            int updated = tx.execute(
                    """
                    update wf_request_work_resolution
                       set result_kind = ?, failure_reason = ?
                     where request_work_id = ? and result_kind = 'PENDING'
                    """,
                    terminal.kind(), terminal.reason(), requestWork.requestWorkId().value());
            if (updated != 1) {
                throw new IllegalStateException("failed to finalize RequestWork terminal resolution");
            }
            return authoritativeTerminal;
        });
    }

    @Override
    public RequestWorkResult tryGrant(AtomicAssignmentGrantCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.backendSelection().backend() != ExecutionBackend.NATIVE_PULL_WORKER) {
            throw new IllegalArgumentException("ASSIGNMENT_GRANT_V1 is Native Pull only");
        }
        return dsl.transactionResult(configuration -> grant(DSL.using(configuration), command));
    }

    /** Reloads one durable grant without relying on process-local state. */
    public Optional<AssignmentGrant> findGrant(ExecutionAssignmentId assignmentId) {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Record resolution = dsl.fetchOne(
                """
                select request_work_id
                  from wf_request_work_resolution
                 where assignment_id = ? and result_kind = 'GRANTED'
                """,
                assignmentId.value());
        if (resolution == null) {
            return Optional.empty();
        }
        return Optional.of(loadGrant(
                dsl,
                new RequestWorkId(resolution.get("request_work_id", String.class)),
                assignmentId));
    }

    /** Reloads the current non-claimable Native Pull ownership for one task. */
    public Optional<AssignmentGrant> findCurrentGrant(ExecutableTaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        Record row = dsl.fetchOne(
                """
                select r.request_work_id, o.active_assignment_id
                  from wf_task_ownership o
                  join wf_request_work_resolution r
                    on r.assignment_id = o.active_assignment_id
                 where o.task_id = ? and not o.claimable and r.result_kind = 'GRANTED'
                """,
                taskId.sha256Hex());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(loadGrant(
                dsl,
                new RequestWorkId(row.get("request_work_id", String.class)),
                new ExecutionAssignmentId(row.get("active_assignment_id", String.class))));
    }

    /**
     * Database-fenced A3 transition primitive. This does not implement Task E retry/cancel policy;
     * it only proves that a non-current generation cannot mutate authoritative attempt state.
     */
    public ExecutionAttempt transitionAttemptIfCurrent(
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration suppliedGeneration,
            ExecutionAttemptState nextState) {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(suppliedGeneration, "suppliedGeneration");
        Objects.requireNonNull(nextState, "nextState");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record attempt = tx.fetchOne(
                    """
                    select task_id, generation, state
                      from wf_execution_attempt
                     where attempt_id = ?
                    """,
                    attemptId.value());
            if (attempt == null) {
                throw new IllegalArgumentException("unknown ExecutionAttemptId: " + attemptId);
            }
            String taskId = attempt.get("task_id", String.class);
            Record current = tx.fetchOne(
                    """
                    select current_generation, current_attempt_id
                      from wf_task_ownership
                     where task_id = ?
                     for update
                    """,
                    taskId);
            if (current == null) {
                throw new IllegalStateException("attempt has no task ownership row");
            }
            ExecutionOwnershipGeneration authoritative = new ExecutionOwnershipGeneration(
                    current.get("current_generation", Long.class));
            long attemptGeneration = attempt.get("generation", Long.class);
            String currentAttemptId = current.get("current_attempt_id", String.class);
            if (!suppliedGeneration.equals(authoritative)
                    || suppliedGeneration.value() != attemptGeneration
                    || !attemptId.value().equals(currentAttemptId)) {
                throw new StaleOwnershipGenerationException(
                        suppliedGeneration, authoritative);
            }
            ExecutionAttemptState previous = ExecutionAttemptState.valueOf(
                    attempt.get("state", String.class));
            if (!previous.canTransitionTo(nextState)) {
                throw new IllegalStateException(
                        "invalid ExecutionAttempt transition " + previous + " -> " + nextState);
            }
            int updated = tx.execute(
                    """
                    update wf_execution_attempt a
                       set state = ?, updated_at = cast(? as timestamptz)
                     where a.attempt_id = ? and a.generation = ?
                       and exists (
                           select 1 from wf_task_ownership o
                            where o.task_id = a.task_id
                              and o.current_attempt_id = a.attempt_id
                              and o.current_generation = a.generation)
                    """,
                    nextState.name(), databaseTime(clock.instant()), attemptId.value(),
                    suppliedGeneration.value());
            if (updated != 1) {
                throw new StaleOwnershipGenerationException(
                        suppliedGeneration, authoritative);
            }
            if (nextState.terminal()) {
                tx.execute(
                        """
                        update wf_execution_backend_selection s
                           set active = false, terminal_at = cast(? as timestamptz)
                          from wf_execution_attempt a
                         where a.backend_selection_id = s.selection_id
                           and a.attempt_id = ? and a.generation = ? and s.active
                        """,
                        databaseTime(clock.instant()),
                        attemptId.value(),
                        suppliedGeneration.value());
            }
            return loadAttempt(tx, attemptId);
        });
    }

    private RequestWorkResult grant(DSLContext tx, AtomicAssignmentGrantCommand command) {
        RequestWork request = command.requestWork();
        String fingerprint = RequestWorkContextFingerprint.of(request);
        Optional<RequestWorkFailureReason> registrationFailure =
                validateRegistrationAuthority(tx, request);
        Optional<RequestWorkResult> existing = claimRequestResolution(tx, request, fingerprint);
        if (existing.isPresent()) {
            if (registrationFailure.isPresent()) {
                return new RequestWorkResult.Rejected(
                        request.requestWorkId(), registrationFailure.orElseThrow());
            }
            return existing.orElseThrow();
        }

        if (registrationFailure.isPresent()) {
            RequestWorkFailureReason reason = registrationFailure.orElseThrow();
            finalizeRejected(tx, request.requestWorkId(), reason);
            return new RequestWorkResult.Rejected(request.requestWorkId(), reason);
        }

        String taskId = command.executableTask().id().sha256Hex();
        tx.execute(
                """
                insert into wf_task_ownership (
                    task_id, current_generation, claimable, updated_at)
                values (?, 0, true, cast(? as timestamptz))
                on conflict (task_id) do nothing
                """,
                taskId, databaseTime(clock.instant()));
        Record ownership = tx.fetchOne(
                """
                select current_generation, claimable
                  from wf_task_ownership
                 where task_id = ?
                 for update
                """,
                taskId);
        if (ownership == null) {
            throw new IllegalStateException("failed to establish task ownership serialization row");
        }
        if (!ownership.get("claimable", Boolean.class)) {
            finalizeNoWork(tx, request.requestWorkId());
            return new RequestWorkResult.NoWork(request.requestWorkId());
        }

        if (!reservationFeasibleInsideTransaction(tx, command)) {
            finalizeRejected(tx, request.requestWorkId(), RequestWorkFailureReason.GRANT_CONFLICT);
            return new RequestWorkResult.Rejected(
                    request.requestWorkId(), RequestWorkFailureReason.GRANT_CONFLICT);
        }

        ExecutionBackendSelectionAuthority.ActivationResult backendActivation =
                JooqExecutionBackendSelectionAuthority.activate(
                        tx, command.backendSelection(), clock.instant());
        if (!backendActivation.activated()) {
            finalizeRejected(tx, request.requestWorkId(), RequestWorkFailureReason.GRANT_CONFLICT);
            return new RequestWorkResult.Rejected(
                    request.requestWorkId(), RequestWorkFailureReason.GRANT_CONFLICT);
        }

        long generationValue = Math.incrementExact(
                ownership.get("current_generation", Long.class));
        ExecutionOwnershipGeneration generation =
                new ExecutionOwnershipGeneration(generationValue);
        ExecutionAttemptId attemptId = new ExecutionAttemptId(unique("attempt"));
        ExecutionAssignmentId assignmentId = new ExecutionAssignmentId(unique("assignment"));
        ReservationId reservationId = new ReservationId(unique("reservation"));
        LeaseId leaseId = new LeaseId(unique("lease"));
        LeaseFencingToken fencingToken = new LeaseFencingToken(unique("fence"));
        Instant grantedAt = clock.instant();
        Instant expiresAt = grantedAt.plus(leaseRenewalContract.leaseDuration());

        tx.execute(
                """
                insert into wf_execution_ownership_generation (task_id, generation, created_at)
                values (?, ?, cast(? as timestamptz))
                """,
                taskId, generation.value(), databaseTime(grantedAt));
        tx.execute(
                """
                insert into wf_execution_attempt (
                    attempt_id, task_id, generation, backend, state,
                    backend_selection_id, backend_local_handle_reference, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?,
                    cast(? as timestamptz), cast(? as timestamptz))
                """,
                attemptId.value(), taskId, generation.value(),
                ExecutionBackend.NATIVE_PULL_WORKER.name(), ExecutionAttemptState.CREATED.name(),
                backendActivation.authoritativeSelection().id().value(),
                leaseId.value(), databaseTime(grantedAt), databaseTime(grantedAt));
        tx.execute(
                """
                insert into wf_execution_assignment (
                    assignment_id, task_id, attempt_id, generation,
                    worker_runtime_id, worker_runtime_incarnation_id,
                    physical_host_id, physical_host_incarnation_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as timestamptz))
                """,
                assignmentId.value(), taskId, attemptId.value(), generation.value(),
                request.workerRuntimeId().value(), request.workerRuntimeIncarnationId().value(),
                request.physicalHostId().value(), request.physicalHostIncarnationId().value(),
                databaseTime(grantedAt));
        command.resourceDemand().deviceDemands().keySet().stream()
                .sorted(Comparator.comparing(DeviceId::value))
                .forEach(deviceId -> tx.execute(
                        "insert into wf_execution_assignment_device (assignment_id, device_id) values (?, ?)",
                        assignmentId.value(), deviceId.value()));

        RuntimeResourceDemand demand = command.resourceDemand();
        tx.execute(
                """
                insert into wf_reservation (
                    reservation_id, assignment_id, task_id, physical_host_id,
                    physical_host_incarnation_id,
                    kind, state, cpu_millicores, memory_bytes,
                    temporary_storage_bytes, created_at)
                values (?, ?, ?, ?, ?, 'TASK', 'ACTIVE', ?, ?, ?, cast(? as timestamptz))
                """,
                reservationId.value(), assignmentId.value(), taskId,
                request.physicalHostId().value(), request.physicalHostIncarnationId().value(),
                demand.cpuMillicores(), demand.memoryBytes(),
                demand.temporaryStorageBytes(), databaseTime(grantedAt));
        demand.deviceDemands().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(DeviceId::value)))
                .forEach(entry -> {
                    RuntimeResourceDemand.DeviceDemand device = entry.getValue();
                    tx.execute(
                            """
                            insert into wf_reservation_device (
                                reservation_id, device_id, vram_bytes, compute_units,
                                encoder_engines, decoder_engines)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                            reservationId.value(), entry.getKey().value(), device.vramBytes(),
                            device.computeUnits(), device.encoderEngines(), device.decoderEngines());
                });

        tx.execute(
                """
                insert into wf_task_lease (
                    lease_id, task_id, assignment_id, attempt_id, generation,
                    worker_runtime_id, worker_runtime_incarnation_id,
                    expires_at, last_heartbeat_at, heartbeat_interval_millis,
                    lease_duration_millis, fencing_token, active, created_at)
                values (?, ?, ?, ?, ?, ?, ?,
                    cast(? as timestamptz), cast(? as timestamptz),
                    ?, ?, ?, true, cast(? as timestamptz))
                """,
                leaseId.value(), taskId, assignmentId.value(), attemptId.value(),
                generation.value(), request.workerRuntimeId().value(),
                request.workerRuntimeIncarnationId().value(), databaseTime(expiresAt),
                databaseTime(grantedAt), leaseRenewalContract.heartbeatInterval().toMillis(),
                leaseRenewalContract.leaseDuration().toMillis(), fencingToken.value(),
                databaseTime(grantedAt));
        tx.execute(
                """
                insert into wf_task_lease_reservation (lease_id, reservation_id, assignment_id)
                values (?, ?, ?)
                """,
                leaseId.value(), reservationId.value(), assignmentId.value());
        int claimed = tx.execute(
                """
                update wf_task_ownership
                   set current_generation = ?, current_attempt_id = ?,
                       active_assignment_id = ?, active_lease_id = ?,
                       claimable = false, updated_at = cast(? as timestamptz)
                 where task_id = ? and claimable
                """,
                generation.value(), attemptId.value(), assignmentId.value(), leaseId.value(),
                databaseTime(grantedAt), taskId);
        if (claimed != 1) {
            throw new IllegalStateException("locked task ownership changed during atomic grant");
        }
        int resolved = tx.execute(
                """
                update wf_request_work_resolution
                   set result_kind = 'GRANTED', assignment_id = ?, task_id = ?
                 where request_work_id = ? and result_kind = 'PENDING'
                """,
                assignmentId.value(), taskId, request.requestWorkId().value());
        if (resolved != 1) {
            throw new IllegalStateException("failed to durably resolve granted RequestWork");
        }
        return new RequestWorkResult.Granted(
                request.requestWorkId(), loadGrant(tx, request.requestWorkId(), assignmentId));
    }

    private Optional<RequestWorkResult> claimRequestResolution(
            DSLContext tx,
            RequestWork request,
            String fingerprint) {
        int inserted = tx.execute(
                """
                insert into wf_request_work_resolution (
                    request_work_id, request_context_fingerprint, result_kind, created_at)
                values (?, ?, 'PENDING', cast(? as timestamptz))
                on conflict (request_work_id) do nothing
                """,
                request.requestWorkId().value(), fingerprint, databaseTime(clock.instant()));
        if (inserted == 1) {
            return Optional.empty();
        }
        Record stored = tx.fetchOne(
                """
                select request_context_fingerprint, result_kind, failure_reason, assignment_id
                  from wf_request_work_resolution
                 where request_work_id = ?
                 for update
                """,
                request.requestWorkId().value());
        if (stored == null) {
            throw new IllegalStateException("conflicting RequestWork resolution disappeared");
        }
        return Optional.of(toResult(tx, request, stored));
    }

    private static RequestWorkResult toResult(
            DSLContext tx,
            RequestWork request,
            Record stored) {
        String actualFingerprint = stored.get("request_context_fingerprint", String.class);
        if (!RequestWorkContextFingerprint.of(request).equals(actualFingerprint)) {
            return new RequestWorkResult.Rejected(
                    request.requestWorkId(),
                    RequestWorkFailureReason.REQUEST_ID_REUSED_WITH_DIFFERENT_CONTEXT);
        }
        String kind = stored.get("result_kind", String.class);
        return switch (kind) {
            case GRANTED -> new RequestWorkResult.Granted(
                    request.requestWorkId(),
                    loadGrant(
                            tx,
                            request.requestWorkId(),
                            new ExecutionAssignmentId(stored.get("assignment_id", String.class))));
            case NO_WORK -> new RequestWorkResult.NoWork(request.requestWorkId());
            case REJECTED -> new RequestWorkResult.Rejected(
                    request.requestWorkId(),
                    RequestWorkFailureReason.valueOf(stored.get("failure_reason", String.class)));
            case REPROBE_REQUIRED -> new RequestWorkResult.ReprobeRequired(
                    request.requestWorkId(),
                    RequestWorkFailureReason.valueOf(stored.get("failure_reason", String.class)));
            case PENDING -> throw new IllegalStateException(
                    "committed RequestWork resolution must never remain PENDING");
            default -> throw new IllegalStateException("unknown RequestWork result kind: " + kind);
        };
    }

    private static void finalizeNoWork(DSLContext tx, RequestWorkId requestWorkId) {
        int updated = tx.execute(
                """
                update wf_request_work_resolution
                   set result_kind = 'NO_WORK'
                 where request_work_id = ? and result_kind = 'PENDING'
                """,
                requestWorkId.value());
        if (updated != 1) {
            throw new IllegalStateException("failed to resolve losing RequestWork race");
        }
    }

    private void finalizeRejected(
            DSLContext tx,
            RequestWorkId requestWorkId,
            RequestWorkFailureReason reason) {
        int updated = tx.execute(
                """
                update wf_request_work_resolution
                   set result_kind = 'REJECTED', failure_reason = ?
                 where request_work_id = ? and result_kind = 'PENDING'
                """,
                reason.name(), requestWorkId.value());
        if (updated != 1) {
            throw new IllegalStateException("failed to resolve disconnected RequestWork");
        }
    }

    private Optional<RequestWorkFailureReason> validateRegistrationAuthority(
            DSLContext tx, RequestWork request) {
        Record hostRegistration = tx.fetchOne(
                """
                select valid_until
                  from wf_host_registration
                 where physical_host_id = ? and physical_host_incarnation_id = ? and active
                 for update
                """,
                request.physicalHostId().value(),
                request.physicalHostIncarnationId().value());
        if (hostRegistration == null) {
            boolean hostKnown = tx.fetchExists(
                    DSL.selectOne()
                            .from("wf_host_registration")
                            .where(DSL.field("physical_host_id")
                                    .eq(request.physicalHostId().value()))
                            .and(DSL.field("active").eq(true)));
            return Optional.of(hostKnown
                    ? RequestWorkFailureReason.HOST_INCARNATION_MISMATCH
                    : RequestWorkFailureReason.PHYSICAL_HOST_NOT_REGISTERED);
        }
        if (!instant(hostRegistration, "valid_until").isAfter(clock.instant())) {
            return Optional.of(RequestWorkFailureReason.REGISTRATION_STALE);
        }

        Record runtimeRegistration = tx.fetchOne(
                """
                select physical_host_id, physical_host_incarnation_id, valid_until
                  from wf_runtime_registration
                 where worker_runtime_id = ? and worker_runtime_incarnation_id = ? and active
                 for update
                """,
                request.workerRuntimeId().value(),
                request.workerRuntimeIncarnationId().value());
        if (runtimeRegistration == null) {
            boolean runtimeKnown = tx.fetchExists(
                    DSL.selectOne()
                            .from("wf_runtime_registration")
                            .where(DSL.field("worker_runtime_id")
                                    .eq(request.workerRuntimeId().value()))
                            .and(DSL.field("active").eq(true)));
            return Optional.of(runtimeKnown
                    ? RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH
                    : RequestWorkFailureReason.WORKER_RUNTIME_NOT_REGISTERED);
        }
        if (!request.physicalHostId().value().equals(
                        runtimeRegistration.get("physical_host_id", String.class))
                || !request.physicalHostIncarnationId().value().equals(
                        runtimeRegistration.get("physical_host_incarnation_id", String.class))) {
            return Optional.of(RequestWorkFailureReason.HOST_INCARNATION_MISMATCH);
        }
        if (!instant(runtimeRegistration, "valid_until").isAfter(clock.instant())) {
            return Optional.of(RequestWorkFailureReason.REGISTRATION_STALE);
        }

        Record runtimeConnection = tx.fetchOne(
                """
                select current_incarnation_id, connected
                  from wf_worker_runtime_connection
                 where worker_runtime_id = ? for update
                """,
                request.workerRuntimeId().value());
        if (runtimeConnection == null) {
            return Optional.of(RequestWorkFailureReason.WORKER_RUNTIME_UNHEALTHY);
        }
        if (!request.workerRuntimeIncarnationId().value().equals(
                runtimeConnection.get("current_incarnation_id", String.class))) {
            return Optional.of(RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH);
        }
        if (!runtimeConnection.get("connected", Boolean.class)) {
            return Optional.of(RequestWorkFailureReason.WORKER_RUNTIME_UNHEALTHY);
        }

        Record hostConnection = tx.fetchOne(
                """
                select current_incarnation_id, connected
                  from wf_physical_host_connection
                 where physical_host_id = ? for update
                """,
                request.physicalHostId().value());
        if (hostConnection == null) {
            return Optional.of(RequestWorkFailureReason.PHYSICAL_HOST_UNAVAILABLE);
        }
        if (!request.physicalHostIncarnationId().value().equals(
                hostConnection.get("current_incarnation_id", String.class))) {
            return Optional.of(RequestWorkFailureReason.HOST_INCARNATION_MISMATCH);
        }
        if (!hostConnection.get("connected", Boolean.class)) {
            return Optional.of(RequestWorkFailureReason.PHYSICAL_HOST_UNAVAILABLE);
        }

        Record snapshot = tx.fetchOne(
                """
                select s.snapshot_fingerprint
                  from wf_host_resource_snapshot s
                  join wf_host_snapshot_generation_authority a
                    on a.physical_host_id = s.physical_host_id
                   and a.physical_host_incarnation_id = s.physical_host_incarnation_id
                   and a.current_generation = s.snapshot_generation
                 where s.physical_host_id = ? and s.physical_host_incarnation_id = ?
                   and s.snapshot_generation = ?
                 for update of s, a
                """,
                request.physicalHostId().value(),
                request.physicalHostIncarnationId().value(),
                request.hostResourceSnapshot().snapshotGeneration().value());
        if (snapshot == null
                || !RequestWorkContextFingerprint.ofSnapshot(request.hostResourceSnapshot())
                        .equals(snapshot.get("snapshot_fingerprint", String.class))) {
            return Optional.of(RequestWorkFailureReason.HOST_RESOURCE_SNAPSHOT_MISMATCH);
        }
        return Optional.empty();
    }

    private static boolean reservationFeasibleInsideTransaction(
            DSLContext tx,
            AtomicAssignmentGrantCommand command) {
        RequestWork request = command.requestWork();
        Record capacity = tx.fetchOne(
                """
                select s.cpu_millicores, s.memory_bytes, s.temporary_storage_bytes,
                       s.safety_headroom_cpu_millicores, s.safety_headroom_memory_bytes,
                       s.safety_headroom_temporary_storage_bytes
                  from wf_host_resource_snapshot s
                  join wf_host_snapshot_generation_authority a
                    on a.physical_host_id = s.physical_host_id
                   and a.physical_host_incarnation_id = s.physical_host_incarnation_id
                   and a.current_generation = s.snapshot_generation
                 where s.physical_host_id = ? and s.physical_host_incarnation_id = ?
                   and s.snapshot_generation = ?
                 for update of s, a
                """,
                request.physicalHostId().value(),
                request.physicalHostIncarnationId().value(),
                request.hostResourceSnapshot().snapshotGeneration().value());
        if (capacity == null) {
            return false;
        }
        Record reserved = tx.fetchOne(
                """
                select coalesce(sum(cpu_millicores), 0)::bigint as cpu_millicores,
                       coalesce(sum(memory_bytes), 0)::bigint as memory_bytes,
                       coalesce(sum(temporary_storage_bytes), 0)::bigint as temporary_storage_bytes
                  from wf_reservation
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                   and state in ('ACTIVE', 'RECOVERY_HOLD')
                """,
                request.physicalHostId().value(),
                request.physicalHostIncarnationId().value());
        RuntimeResourceDemand demand = command.resourceDemand();
        if (!fits(
                        reserved.get("cpu_millicores", Long.class),
                        demand.cpuMillicores(),
                        capacity.get("cpu_millicores", Long.class),
                        capacity.get("safety_headroom_cpu_millicores", Long.class))
                || !fits(
                        reserved.get("memory_bytes", Long.class),
                        demand.memoryBytes(),
                        capacity.get("memory_bytes", Long.class),
                        capacity.get("safety_headroom_memory_bytes", Long.class))
                || !fits(
                        reserved.get("temporary_storage_bytes", Long.class),
                        demand.temporaryStorageBytes(),
                        capacity.get("temporary_storage_bytes", Long.class),
                        capacity.get(
                                "safety_headroom_temporary_storage_bytes", Long.class))
                || demand.cpuMillicores()
                        > command.authoritativeSchedulableCapacity().cpu().millicores()
                || demand.memoryBytes()
                        > command.authoritativeSchedulableCapacity().memory().bytes()
                || demand.temporaryStorageBytes()
                        > command.authoritativeSchedulableCapacity().temporaryStorage().bytes()) {
            return false;
        }

        for (Map.Entry<DeviceId, RuntimeResourceDemand.DeviceDemand> entry
                : demand.deviceDemands().entrySet()) {
            Record deviceCapacity = tx.fetchOne(
                    """
                    select vram_bytes, compute_units, encoder_engines, decoder_engines,
                           safety_headroom_vram_bytes, safety_headroom_compute_units,
                           safety_headroom_encoder_engines, safety_headroom_decoder_engines
                      from wf_host_resource_snapshot_device
                     where physical_host_id = ? and physical_host_incarnation_id = ?
                       and snapshot_generation = ? and device_id = ?
                     for update
                    """,
                    request.physicalHostId().value(),
                    request.physicalHostIncarnationId().value(),
                    request.hostResourceSnapshot().snapshotGeneration().value(),
                    entry.getKey().value());
            if (deviceCapacity == null) {
                return false;
            }
            Record deviceReserved = tx.fetchOne(
                    """
                    select coalesce(sum(rd.vram_bytes), 0)::bigint as vram_bytes,
                           coalesce(sum(rd.compute_units), 0)::bigint as compute_units,
                           coalesce(sum(rd.encoder_engines), 0)::bigint as encoder_engines,
                           coalesce(sum(rd.decoder_engines), 0)::bigint as decoder_engines
                      from wf_reservation_device rd
                      join wf_reservation r on r.reservation_id = rd.reservation_id
                     where r.physical_host_id = ? and r.physical_host_incarnation_id = ?
                       and r.state in ('ACTIVE', 'RECOVERY_HOLD') and rd.device_id = ?
                    """,
                    request.physicalHostId().value(),
                    request.physicalHostIncarnationId().value(),
                    entry.getKey().value());
            RuntimeResourceDemand.DeviceDemand deviceDemand = entry.getValue();
            com.example.platform.workerfabric.domain.DeviceResourceCapacity supplied =
                    command.authoritativeSchedulableCapacity()
                            .deviceResources().get(entry.getKey());
            if (supplied == null
                    || !fits(deviceReserved.get("vram_bytes", Long.class),
                            deviceDemand.vramBytes(), deviceCapacity.get("vram_bytes", Long.class),
                            deviceCapacity.get("safety_headroom_vram_bytes", Long.class))
                    || !fits(deviceReserved.get("compute_units", Long.class),
                            deviceDemand.computeUnits(), deviceCapacity.get("compute_units", Long.class),
                            deviceCapacity.get("safety_headroom_compute_units", Long.class))
                    || !fits(deviceReserved.get("encoder_engines", Long.class),
                            deviceDemand.encoderEngines(), deviceCapacity.get("encoder_engines", Long.class),
                            deviceCapacity.get("safety_headroom_encoder_engines", Long.class))
                    || !fits(deviceReserved.get("decoder_engines", Long.class),
                            deviceDemand.decoderEngines(), deviceCapacity.get("decoder_engines", Long.class),
                            deviceCapacity.get("safety_headroom_decoder_engines", Long.class))
                    || deviceDemand.vramBytes() > supplied.vramBytes()
                    || deviceDemand.computeUnits() > supplied.computeUnits()
                    || deviceDemand.encoderEngines() > supplied.encoderEngines()
                    || deviceDemand.decoderEngines() > supplied.decoderEngines()) {
                return false;
            }
        }
        return true;
    }

    private static boolean fits(
            long reserved,
            long requested,
            long capacity,
            long safetyHeadroom) {
        return reserved >= 0
                && requested >= 0
                && safetyHeadroom >= 0
                && capacity >= safetyHeadroom
                && capacity - safetyHeadroom >= reserved
                && requested <= capacity - safetyHeadroom - reserved;
    }

    private static AssignmentGrant loadGrant(
            DSLContext tx,
            RequestWorkId requestWorkId,
            ExecutionAssignmentId assignmentId) {
        Record assignmentRow = tx.fetchOne(
                "select * from wf_execution_assignment where assignment_id = ?",
                assignmentId.value());
        if (assignmentRow == null) {
            throw new IllegalStateException("granted assignment row is missing: " + assignmentId);
        }
        String taskIdValue = assignmentRow.get("task_id", String.class);
        ExecutableTaskId taskId = new ExecutableTaskId(taskIdValue);
        ExecutionAttemptId attemptId = new ExecutionAttemptId(
                assignmentRow.get("attempt_id", String.class));
        ExecutionOwnershipGeneration generation = new ExecutionOwnershipGeneration(
                assignmentRow.get("generation", Long.class));
        Set<DeviceId> deviceIds = tx.fetch(
                        """
                        select device_id from wf_execution_assignment_device
                         where assignment_id = ? order by device_id
                        """,
                        assignmentId.value()).stream()
                .map(row -> new DeviceId(row.get("device_id", String.class)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<Reservation> reservations = loadReservations(tx, assignmentId);
        Set<ReservationId> reservationIds = reservations.stream()
                .map(Reservation::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ExecutionAssignment assignment = new ExecutionAssignment(
                assignmentId,
                taskId,
                attemptId,
                generation,
                new WorkerRuntimeId(assignmentRow.get("worker_runtime_id", String.class)),
                new WorkerRuntimeIncarnationId(
                        assignmentRow.get("worker_runtime_incarnation_id", String.class)),
                new PhysicalHostId(assignmentRow.get("physical_host_id", String.class)),
                new PhysicalHostIncarnationId(
                        assignmentRow.get("physical_host_incarnation_id", String.class)),
                deviceIds,
                reservationIds);
        ExecutionAttempt attempt = loadAttempt(tx, attemptId);

        Record leaseRow = tx.fetchOne(
                """
                select l.*
                  from wf_task_ownership o
                  join wf_task_lease l on l.lease_id = o.active_lease_id
                 where o.task_id = ? and o.active_assignment_id = ? and not o.claimable
                """,
                taskIdValue, assignmentId.value());
        if (leaseRow == null) {
            throw new IllegalStateException("granted active lease row is missing");
        }
        Set<ReservationId> leaseReservations = tx.fetch(
                        """
                        select reservation_id from wf_task_lease_reservation
                         where lease_id = ? order by reservation_id
                        """,
                        leaseRow.get("lease_id", String.class)).stream()
                .map(row -> new ReservationId(row.get("reservation_id", String.class)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        TaskLease lease = new TaskLease(
                new LeaseId(leaseRow.get("lease_id", String.class)),
                taskId,
                assignmentId,
                attemptId,
                generation,
                new WorkerRuntimeId(leaseRow.get("worker_runtime_id", String.class)),
                new WorkerRuntimeIncarnationId(
                        leaseRow.get("worker_runtime_incarnation_id", String.class)),
                leaseReservations,
                instant(leaseRow, "expires_at"),
                instant(leaseRow, "last_heartbeat_at"),
                new LeaseRenewalContract(
                        java.time.Duration.ofMillis(
                                leaseRow.get("heartbeat_interval_millis", Long.class)),
                        java.time.Duration.ofMillis(
                                leaseRow.get("lease_duration_millis", Long.class))),
                new LeaseFencingToken(leaseRow.get("fencing_token", String.class)));
        return new AssignmentGrant(requestWorkId, assignment, reservations, lease, attempt);
    }

    private static List<Reservation> loadReservations(
            DSLContext tx,
            ExecutionAssignmentId assignmentId) {
        List<Reservation> result = new ArrayList<>();
        for (Record reservation : tx.fetch(
                """
                select * from wf_reservation
                 where assignment_id = ? order by reservation_id
                """,
                assignmentId.value())) {
            ReservationId id = new ReservationId(reservation.get("reservation_id", String.class));
            Map<DeviceId, DeviceResourceReservation> devices = new LinkedHashMap<>();
            for (Record device : tx.fetch(
                    """
                    select * from wf_reservation_device
                     where reservation_id = ? order by device_id
                    """,
                    id.value())) {
                DeviceId deviceId = new DeviceId(device.get("device_id", String.class));
                devices.put(deviceId, new DeviceResourceReservation(
                        deviceId,
                        device.get("vram_bytes", Long.class),
                        device.get("compute_units", Long.class),
                        device.get("encoder_engines", Long.class),
                        device.get("decoder_engines", Long.class)));
            }
            result.add(new Reservation(
                    id,
                    new PhysicalHostId(reservation.get("physical_host_id", String.class)),
                    ReservationKind.valueOf(reservation.get("kind", String.class)),
                    new ReservedResources(
                            reservation.get("cpu_millicores", Long.class),
                            reservation.get("memory_bytes", Long.class),
                            reservation.get("temporary_storage_bytes", Long.class),
                            devices),
                    ReservationState.valueOf(reservation.get("state", String.class))));
        }
        return List.copyOf(result);
    }

    private static ExecutionAttempt loadAttempt(DSLContext tx, ExecutionAttemptId attemptId) {
        Record row = tx.fetchOne(
                "select * from wf_execution_attempt where attempt_id = ?",
                attemptId.value());
        if (row == null) {
            throw new IllegalStateException("execution attempt row is missing: " + attemptId);
        }
        ExecutionOwnershipGeneration generation =
                new ExecutionOwnershipGeneration(row.get("generation", Long.class));
        ExecutionBackend backend = ExecutionBackend.valueOf(row.get("backend", String.class));
        Optional<String> backendLocalReference =
                Optional.ofNullable(row.get("backend_local_handle_reference", String.class));
        Optional<BackendExecutionHandle> backendHandle = backendLocalReference.map(reference ->
                switch (backend) {
                    case NATIVE_PULL_WORKER -> NativeWorkerBackendExecutionHandle.forLease(
                            attemptId, generation, new LeaseId(reference));
                    case OPEN_CUE_FARM -> OpenCueBackendExecutionHandle.forSubmission(
                            attemptId, generation, new CueJobId(reference));
                    case REMOTE_PROVIDER -> RemoteProviderExecutionHandle.forRemoteExecution(
                            attemptId, generation, new RemoteExecutionId(reference));
                });
        return new ExecutionAttempt(
                attemptId,
                new ExecutableTaskId(row.get("task_id", String.class)),
                generation,
                backend,
                ExecutionAttemptState.valueOf(row.get("state", String.class)),
                backendHandle);
    }

    private static Instant instant(Record row, String field) {
        return row.get(field, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record Terminal(String kind, String reason) {

        private static Terminal from(RequestWorkResult result) {
            if (result instanceof RequestWorkResult.NoWork) {
                return new Terminal(NO_WORK, null);
            }
            if (result instanceof RequestWorkResult.Rejected rejected) {
                return new Terminal(REJECTED, rejected.reason().name());
            }
            if (result instanceof RequestWorkResult.ReprobeRequired reprobe) {
                return new Terminal(REPROBE_REQUIRED, reprobe.reason().name());
            }
            throw new IllegalArgumentException("granted result is not terminal");
        }
    }
}
