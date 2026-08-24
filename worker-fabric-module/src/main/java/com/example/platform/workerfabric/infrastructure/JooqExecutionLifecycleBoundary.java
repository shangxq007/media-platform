package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.AttemptCancellation;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.AttemptCancellationResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.BackendLocalRetry;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.BackendLocalRetryResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.DisconnectResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.DisconnectStatus;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.HostDisconnect;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseExpiry;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseExpiryResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseHeartbeat;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseHeartbeatResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseHeartbeatStatus;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseOwnershipFence;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmission;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmissionDecision;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmissionResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LowTelemetryEvidence;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LowTelemetryResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.PhysicalReleaseConfirmation;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.PhysicalReleaseResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.WorkerDisconnect;
import com.example.platform.workerfabric.domain.ReservationState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** PostgreSQL/jOOQ authority for Task E idempotent, generation-fenced lifecycle transitions. */
@Repository
public class JooqExecutionLifecycleBoundary implements ExecutionLifecycleBoundary {

    private final DSLContext dsl;

    public JooqExecutionLifecycleBoundary(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public LeaseHeartbeatResult heartbeat(LeaseHeartbeat heartbeat) {
        Objects.requireNonNull(heartbeat, "heartbeat");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record lease = lockLease(tx, heartbeat.fence().leaseId().value());
            if (!matchesFence(lease, heartbeat.fence())
                    || !JooqOwnershipFencing.isCurrentOwner(tx, lease)) {
                return heartbeatResult(LeaseHeartbeatStatus.STALE_OWNER_NOOP);
            }
            Instant receivedAt = heartbeat.receivedAt();
            Instant expiresAt = instant(lease, "expires_at");
            Instant lastHeartbeatAt = instant(lease, "last_heartbeat_at");
            if (!lease.get("active", Boolean.class) || !receivedAt.isBefore(expiresAt)) {
                return heartbeatResult(LeaseHeartbeatStatus.EXPIRED_NOOP);
            }
            if (!receivedAt.isAfter(lastHeartbeatAt)) {
                return heartbeatResult(
                        LeaseHeartbeatStatus.DUPLICATE_OR_OUT_OF_ORDER_NOOP);
            }
            Instant renewedExpiry = receivedAt.plusMillis(
                    lease.get("lease_duration_millis", Long.class));
            int updated = tx.execute(
                    """
                    update wf_task_lease l
                       set last_heartbeat_at = cast(? as timestamptz),
                           expires_at = cast(? as timestamptz)
                     where l.lease_id = ? and l.active
                       and l.generation = ? and l.fencing_token = ?
                       and exists (
                           select 1 from wf_task_ownership o
                            where o.task_id = l.task_id
                              and o.current_generation = l.generation
                              and o.current_attempt_id = l.attempt_id
                              and o.active_assignment_id = l.assignment_id
                              and o.active_lease_id = l.lease_id
                              and not o.claimable)
                    """,
                    databaseTime(receivedAt),
                    databaseTime(renewedExpiry),
                    heartbeat.fence().leaseId().value(),
                    heartbeat.fence().ownershipGeneration().value(),
                    heartbeat.fence().fencingToken().value());
            return updated == 1
                    ? new LeaseHeartbeatResult(
                            LeaseHeartbeatStatus.ACCEPTED, Optional.of(renewedExpiry))
                    : heartbeatResult(LeaseHeartbeatStatus.STALE_OWNER_NOOP);
        });
    }

    @Override
    public LeaseExpiryResult expireLease(LeaseExpiry expiry) {
        Objects.requireNonNull(expiry, "expiry");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record lease = lockLease(tx, expiry.leaseId().value());
            if (lease == null) {
                return LeaseExpiryResult.STALE_LEASE_NOOP;
            }
            if (!lease.get("active", Boolean.class)) {
                return LeaseExpiryResult.ALREADY_PROCESSED_NOOP;
            }
            if (expiry.detectedAt().isBefore(instant(lease, "expires_at"))) {
                return LeaseExpiryResult.NOT_YET_EXPIRED;
            }
            if (!JooqOwnershipFencing.isCurrentOwner(tx, lease)) {
                tx.execute(
                        "update wf_task_lease set active = false where lease_id = ? and active",
                        lease.get("lease_id", String.class));
                tx.execute(
                        "update wf_reservation set state = 'RECOVERY_HOLD' "
                                + "where assignment_id = ? and state = 'ACTIVE'",
                        lease.get("assignment_id", String.class));
                return LeaseExpiryResult.STALE_LEASE_NOOP;
            }
            JooqOwnershipFencing.closeOwnership(
                    tx,
                    lease,
                    ExecutionAttemptState.ABANDONED,
                    ReservationState.RECOVERY_HOLD,
                    expiry.detectedAt());
            return LeaseExpiryResult.OWNERSHIP_LOST_RECOVERY_HOLD;
        });
    }

    @Override
    public DisconnectResult disconnectWorker(WorkerDisconnect disconnect) {
        Objects.requireNonNull(disconnect, "disconnect");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record current = tx.fetchOne(
                    """
                    select current_incarnation_id from wf_worker_runtime_connection
                     where worker_runtime_id = ? for update
                    """,
                    disconnect.workerRuntimeId().value());
            if (current != null
                    && !disconnect.workerRuntimeIncarnationId().value().equals(
                            current.get("current_incarnation_id", String.class))) {
                return new DisconnectResult(DisconnectStatus.STALE_INCARNATION_NOOP, 0);
            }
            tx.execute(
                    """
                    insert into wf_worker_runtime_connection (
                        worker_runtime_id, current_incarnation_id, connected, updated_at)
                    values (?, ?, false, cast(? as timestamptz))
                    on conflict (worker_runtime_id) do update
                       set connected = false, updated_at = excluded.updated_at
                     where wf_worker_runtime_connection.current_incarnation_id =
                           excluded.current_incarnation_id
                    """,
                    disconnect.workerRuntimeId().value(),
                    disconnect.workerRuntimeIncarnationId().value(),
                    databaseTime(disconnect.detectedAt()));
            int closed = JooqOwnershipFencing.fenceRuntimeIncarnation(
                    tx,
                    disconnect.workerRuntimeId().value(),
                    disconnect.workerRuntimeIncarnationId().value(),
                    disconnect.detectedAt());
            return new DisconnectResult(DisconnectStatus.RECORDED, closed);
        });
    }

    @Override
    public DisconnectResult disconnectHost(HostDisconnect disconnect) {
        Objects.requireNonNull(disconnect, "disconnect");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record current = tx.fetchOne(
                    """
                    select current_incarnation_id from wf_physical_host_connection
                     where physical_host_id = ? for update
                    """,
                    disconnect.physicalHostId().value());
            if (current != null
                    && !disconnect.physicalHostIncarnationId().value().equals(
                            current.get("current_incarnation_id", String.class))) {
                return new DisconnectResult(DisconnectStatus.STALE_INCARNATION_NOOP, 0);
            }
            tx.execute(
                    """
                    insert into wf_physical_host_connection (
                        physical_host_id, current_incarnation_id, connected, updated_at)
                    values (?, ?, false, cast(? as timestamptz))
                    on conflict (physical_host_id) do update
                       set connected = false, updated_at = excluded.updated_at
                     where wf_physical_host_connection.current_incarnation_id =
                           excluded.current_incarnation_id
                    """,
                    disconnect.physicalHostId().value(),
                    disconnect.physicalHostIncarnationId().value(),
                    databaseTime(disconnect.detectedAt()));
            int closed = JooqOwnershipFencing.fenceHostIncarnation(
                    tx,
                    disconnect.physicalHostId().value(),
                    disconnect.physicalHostIncarnationId().value(),
                    disconnect.detectedAt());
            return new DisconnectResult(DisconnectStatus.RECORDED, closed);
        });
    }

    @Override
    public LocalAdmissionResult recordLocalAdmission(LocalAdmission admission) {
        Objects.requireNonNull(admission, "admission");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record duplicate = tx.fetchOne(
                    "select decision from wf_local_admission where lease_id = ? for update",
                    admission.fence().leaseId().value());
            if (duplicate != null) {
                return LocalAdmissionResult.DUPLICATE_NOOP;
            }
            Record lease = lockLease(tx, admission.fence().leaseId().value());
            if (!matchesFence(lease, admission.fence())
                    || !JooqOwnershipFencing.isCurrentOwner(tx, lease)
                    || !admission.receivedAt().isBefore(instant(lease, "expires_at"))) {
                return LocalAdmissionResult.STALE_OWNER_NOOP;
            }
            if (admission.decision() == LocalAdmissionDecision.ACCEPT) {
                int updated = tx.execute(
                        """
                        update wf_execution_attempt a
                           set state = 'RUNNING', updated_at = cast(? as timestamptz)
                         where a.attempt_id = ? and a.generation = ? and a.state = 'CREATED'
                           and exists (
                               select 1 from wf_task_ownership o
                                where o.task_id = a.task_id
                                  and o.current_attempt_id = a.attempt_id
                                  and o.current_generation = a.generation
                                  and o.active_lease_id = ? and not o.claimable)
                        """,
                        databaseTime(admission.receivedAt()),
                        admission.fence().executionAttemptId().value(),
                        admission.fence().ownershipGeneration().value(),
                        admission.fence().leaseId().value());
                if (updated != 1) {
                    return LocalAdmissionResult.STALE_OWNER_NOOP;
                }
                insertAdmission(tx, admission, LocalAdmissionResult.ACCEPTED_RUNNING);
                return LocalAdmissionResult.ACCEPTED_RUNNING;
            }

            boolean physicallyReleased = admission.physicalReleaseConfirmation().isPresent()
                    && releaseConfirmationMatches(
                            tx, admission.physicalReleaseConfirmation().orElseThrow(), lease);
            ReservationState reservationState = physicallyReleased
                    ? ReservationState.RELEASED
                    : ReservationState.RECOVERY_HOLD;
            JooqOwnershipFencing.closeOwnership(
                    tx,
                    lease,
                    ExecutionAttemptState.ABANDONED,
                    reservationState,
                    admission.receivedAt());
            LocalAdmissionResult result = physicallyReleased
                    ? LocalAdmissionResult.DECLINED_RELEASED
                    : LocalAdmissionResult.DECLINED_RECOVERY_HOLD;
            insertAdmission(tx, admission, result);
            admission.physicalReleaseConfirmation()
                    .filter(ignored -> physicallyReleased)
                    .ifPresent(confirmation -> insertReleaseConfirmation(tx, confirmation));
            return result;
        });
    }

    @Override
    public AttemptCancellationResult cancelAttempt(AttemptCancellation cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record lease = lockLease(tx, cancellation.fence().leaseId().value());
            if (!matchesFence(lease, cancellation.fence())) {
                return AttemptCancellationResult.STALE_OWNER_NOOP;
            }
            if (!lease.get("active", Boolean.class)) {
                Record attempt = tx.fetchOne(
                        "select state from wf_execution_attempt where attempt_id = ?",
                        cancellation.fence().executionAttemptId().value());
                return attempt != null
                                && ExecutionAttemptState.CANCELLED.name().equals(
                                        attempt.get("state", String.class))
                        ? AttemptCancellationResult.DUPLICATE_NOOP
                        : AttemptCancellationResult.STALE_OWNER_NOOP;
            }
            if (!JooqOwnershipFencing.isCurrentOwner(tx, lease)) {
                return AttemptCancellationResult.STALE_OWNER_NOOP;
            }
            JooqOwnershipFencing.closeOwnership(
                    tx,
                    lease,
                    ExecutionAttemptState.CANCELLED,
                    ReservationState.RECOVERY_HOLD,
                    cancellation.receivedAt());
            return AttemptCancellationResult.CANCELLED_RECOVERY_HOLD;
        });
    }

    @Override
    public PhysicalReleaseResult confirmPhysicalRelease(
            PhysicalReleaseConfirmation confirmation) {
        Objects.requireNonNull(confirmation, "confirmation");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record prior = tx.fetchOne(
                    "select confirmation_id from wf_physical_release_confirmation where confirmation_id = ?",
                    confirmation.confirmationId());
            if (prior != null) {
                return PhysicalReleaseResult.ALREADY_RELEASED_NOOP;
            }
            Record lease = lockLease(tx, confirmation.fence().leaseId().value());
            if (!matchesFence(lease, confirmation.fence())
                    || !releaseConfirmationMatches(tx, confirmation, lease)) {
                return PhysicalReleaseResult.STALE_OR_INCOMPLETE_CONFIRMATION_NOOP;
            }
            if (lease.get("active", Boolean.class)) {
                return PhysicalReleaseResult.OWNERSHIP_STILL_ACTIVE_NOOP;
            }
            Set<String> states = tx.fetch(
                            """
                            select state from wf_reservation r
                             join wf_task_lease_reservation lr
                               on lr.reservation_id = r.reservation_id
                            where lr.lease_id = ?
                            """,
                            confirmation.fence().leaseId().value()).stream()
                    .map(row -> row.get("state", String.class))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (states.equals(Set.of(ReservationState.RELEASED.name()))) {
                insertReleaseConfirmation(tx, confirmation);
                return PhysicalReleaseResult.ALREADY_RELEASED_NOOP;
            }
            if (!states.equals(Set.of(ReservationState.RECOVERY_HOLD.name()))) {
                return PhysicalReleaseResult.STALE_OR_INCOMPLETE_CONFIRMATION_NOOP;
            }
            tx.execute(
                    """
                    update wf_reservation r set state = 'RELEASED'
                     where r.reservation_id in (
                         select lr.reservation_id from wf_task_lease_reservation lr
                          where lr.lease_id = ?)
                       and r.state = 'RECOVERY_HOLD'
                    """,
                    confirmation.fence().leaseId().value());
            insertReleaseConfirmation(tx, confirmation);
            return PhysicalReleaseResult.RELEASED;
        });
    }

    @Override
    public LowTelemetryResult recordLowTelemetry(LowTelemetryEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        // CAPACITY_RESERVATION_AND_OBSERVATION_ARE_DISTINCT_AUTHORITIES_V1.
        return LowTelemetryResult.RECORDED_AS_NON_AUTHORITATIVE_NOOP;
    }

    @Override
    public BackendLocalRetryResult recordBackendLocalRetry(BackendLocalRetry retry) {
        Objects.requireNonNull(retry, "retry");
        // BACKEND_LOCAL_RETRY_IS_NOT_PLATFORM_EXECUTION_ATTEMPT_V1.
        return BackendLocalRetryResult.ACKNOWLEDGED_WITHOUT_PLATFORM_ATTEMPT;
    }

    private static LeaseHeartbeatResult heartbeatResult(LeaseHeartbeatStatus status) {
        return new LeaseHeartbeatResult(status, Optional.empty());
    }

    private static Record lockLease(DSLContext tx, String leaseId) {
        return tx.fetchOne("select * from wf_task_lease where lease_id = ? for update", leaseId);
    }

    private static boolean matchesFence(Record lease, LeaseOwnershipFence fence) {
        return lease != null
                && fence.leaseId().value().equals(lease.get("lease_id", String.class))
                && fence.executableTaskId().sha256Hex().equals(
                        lease.get("task_id", String.class))
                && fence.executionAssignmentId().value().equals(
                        lease.get("assignment_id", String.class))
                && fence.executionAttemptId().value().equals(
                        lease.get("attempt_id", String.class))
                && fence.ownershipGeneration().value()
                        == lease.get("generation", Long.class)
                && fence.workerRuntimeId().value().equals(
                        lease.get("worker_runtime_id", String.class))
                && fence.workerRuntimeIncarnationId().value().equals(
                        lease.get("worker_runtime_incarnation_id", String.class))
                && fence.fencingToken().value().equals(
                        lease.get("fencing_token", String.class));
    }

    private static void insertAdmission(
            DSLContext tx, LocalAdmission admission, LocalAdmissionResult result) {
        tx.execute(
                """
                insert into wf_local_admission (
                    lease_id, attempt_id, generation, decision, decline_reason,
                    result, received_at)
                values (?, ?, ?, ?, ?, ?, cast(? as timestamptz))
                """,
                admission.fence().leaseId().value(),
                admission.fence().executionAttemptId().value(),
                admission.fence().ownershipGeneration().value(),
                admission.decision().name(),
                admission.declineReason().map(Enum::name).orElse(null),
                result.name(),
                databaseTime(admission.receivedAt()));
    }

    private static boolean releaseConfirmationMatches(
            DSLContext tx, PhysicalReleaseConfirmation confirmation, Record lease) {
        if (!matchesFence(lease, confirmation.fence())) {
            return false;
        }
        Set<String> authoritative = tx.fetch(
                        """
                        select reservation_id from wf_task_lease_reservation
                         where lease_id = ? order by reservation_id
                        """,
                        confirmation.fence().leaseId().value()).stream()
                .map(row -> row.get("reservation_id", String.class))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> supplied = confirmation.reservationIds().stream()
                .map(reservationId -> reservationId.value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return authoritative.equals(supplied);
    }

    private static void insertReleaseConfirmation(
            DSLContext tx, PhysicalReleaseConfirmation confirmation) {
        tx.execute(
                """
                insert into wf_physical_release_confirmation (
                    confirmation_id, lease_id, confirmed_at)
                values (?, ?, cast(? as timestamptz))
                on conflict (confirmation_id) do nothing
                """,
                confirmation.confirmationId(),
                confirmation.fence().leaseId().value(),
                databaseTime(confirmation.confirmedAt()));
    }

    private static Instant instant(Record row, String field) {
        return row.get(field, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
