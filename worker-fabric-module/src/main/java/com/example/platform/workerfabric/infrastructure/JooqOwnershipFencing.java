package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ReservationState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

/** Shared SQL mechanics for closing canonical ownership inside a caller-owned transaction. */
final class JooqOwnershipFencing {

    private JooqOwnershipFencing() {}

    static int fenceRuntimeIncarnation(
            DSLContext tx,
            String workerRuntimeId,
            String workerRuntimeIncarnationId,
            Instant recordedAt) {
        return closeMatchingLeases(
                tx,
                "l.worker_runtime_id = ? and l.worker_runtime_incarnation_id = ?",
                recordedAt,
                workerRuntimeId,
                workerRuntimeIncarnationId);
    }

    static int fenceHostIncarnation(
            DSLContext tx,
            String physicalHostId,
            String physicalHostIncarnationId,
            Instant recordedAt) {
        return closeMatchingLeases(
                tx,
                "a.physical_host_id = ? and a.physical_host_incarnation_id = ?",
                recordedAt,
                physicalHostId,
                physicalHostIncarnationId);
    }

    static Record lockActiveLeaseForAttempt(
            DSLContext tx, String attemptId, long generation) {
        return tx.fetchOne(
                """
                select l.* from wf_task_lease l
                 where l.attempt_id = ? and l.generation = ? and l.active
                 for update
                """,
                attemptId,
                generation);
    }

    static boolean isCurrentOwner(DSLContext tx, Record lease) {
        if (lease == null || !lease.get("active", Boolean.class)) {
            return false;
        }
        return tx.fetchExists(
                DSL.selectOne()
                        .from("wf_task_ownership")
                        .where(DSL.field("task_id").eq(lease.get("task_id", String.class)))
                        .and(DSL.field("current_generation")
                                .eq(lease.get("generation", Long.class)))
                        .and(DSL.field("current_attempt_id")
                                .eq(lease.get("attempt_id", String.class)))
                        .and(DSL.field("active_assignment_id")
                                .eq(lease.get("assignment_id", String.class)))
                        .and(DSL.field("active_lease_id")
                                .eq(lease.get("lease_id", String.class)))
                        .and(DSL.field("claimable").eq(false)));
    }

    static void closeOwnership(
            DSLContext tx,
            Record lease,
            ExecutionAttemptState terminalState,
            ReservationState reservationState,
            Instant recordedAt) {
        if (!terminalState.terminal()) {
            throw new IllegalArgumentException("ownership closure requires terminal attempt state");
        }
        tx.execute(
                """
                update wf_execution_attempt
                   set state = ?, updated_at = cast(? as timestamptz)
                 where attempt_id = ? and generation = ?
                   and state in ('CREATED','RUNNING')
                """,
                terminalState.name(),
                databaseTime(recordedAt),
                lease.get("attempt_id", String.class),
                lease.get("generation", Long.class));
        tx.execute(
                """
                update wf_execution_backend_selection s
                   set active = false, terminal_at = cast(? as timestamptz)
                  from wf_execution_attempt a
                 where a.backend_selection_id = s.selection_id
                   and a.attempt_id = ? and a.generation = ? and s.active
                """,
                databaseTime(recordedAt),
                lease.get("attempt_id", String.class),
                lease.get("generation", Long.class));
        tx.execute(
                """
                update wf_reservation r set state = ?
                 where r.assignment_id = ? and r.state = 'ACTIVE'
                """,
                reservationState.name(),
                lease.get("assignment_id", String.class));
        tx.execute(
                "update wf_task_lease set active = false where lease_id = ? and active",
                lease.get("lease_id", String.class));
        int ownershipClosed = tx.execute(
                """
                update wf_task_ownership
                   set current_attempt_id = null, active_assignment_id = null,
                       active_lease_id = null, claimable = true,
                       updated_at = cast(? as timestamptz)
                 where task_id = ? and current_generation = ?
                   and current_attempt_id = ? and active_assignment_id = ?
                   and active_lease_id = ? and not claimable
                """,
                databaseTime(recordedAt),
                lease.get("task_id", String.class),
                lease.get("generation", Long.class),
                lease.get("attempt_id", String.class),
                lease.get("assignment_id", String.class),
                lease.get("lease_id", String.class));
        if (ownershipClosed != 1) {
            throw new IllegalStateException("current ownership changed during fenced closure");
        }
        tx.execute(
                """
                update wf_request_work_resolution
                   set result_kind = 'NO_WORK', assignment_id = null, task_id = null
                 where assignment_id = ? and result_kind = 'GRANTED'
                """,
                lease.get("assignment_id", String.class));
    }

    private static int closeMatchingLeases(
            DSLContext tx,
            String condition,
            Instant recordedAt,
            Object... bindings) {
        List<Record> leases = tx.fetch(
                """
                select l.* from wf_task_lease l
                join wf_execution_assignment a on a.assignment_id = l.assignment_id
                where l.active and
                """ + condition + " for update",
                bindings);
        int closed = 0;
        for (Record lease : leases) {
            if (isCurrentOwner(tx, lease)) {
                closeOwnership(
                        tx,
                        lease,
                        ExecutionAttemptState.ABANDONED,
                        ReservationState.RECOVERY_HOLD,
                        recordedAt);
                closed++;
            } else {
                deactivateStaleLease(tx, lease);
            }
        }
        return closed;
    }

    private static void deactivateStaleLease(DSLContext tx, Record lease) {
        tx.execute(
                "update wf_task_lease set active = false where lease_id = ? and active",
                lease.get("lease_id", String.class));
        tx.execute(
                """
                update wf_reservation set state = 'RECOVERY_HOLD'
                 where assignment_id = ? and state = 'ACTIVE'
                """,
                lease.get("assignment_id", String.class));
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
