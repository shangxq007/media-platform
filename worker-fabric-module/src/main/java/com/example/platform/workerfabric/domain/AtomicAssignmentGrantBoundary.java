package com.example.platform.workerfabric.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Persistence/transaction seam implemented by Roadmap #22 Epoch 3 Task D.
 *
 * <p>REQUEST_WORK_IS_IDEMPOTENT_V1: all methods key the exact immutable RequestWork context by
 * RequestWorkId. A replay with the same context returns the same still-valid grant or the same
 * terminal result. Reuse with different context returns
 * REQUEST_ID_REUSED_WITH_DIFFERENT_CONTEXT. Implementations must durably serialize both terminal
 * resolution and grant resolution; an in-memory matcher cache is not authoritative.
 *
 * <p>ASSIGNMENT_GRANT_V1: {@link #tryGrant(AtomicAssignmentGrantCommand)} atomically establishes
 * every {@link GrantAuthority}, makes the task non-claimable, and records the RequestWork
 * resolution. Racing calls for one task produce at most one grant. Any failure, including a
 * failure between component writes, leaves {@link GrantFailureDisposition#NONE_AUTHORITATIVE}.
 */
public interface AtomicAssignmentGrantBoundary {

    Set<GrantAuthority> ATOMIC_AUTHORITIES = Set.of(
            GrantAuthority.EXECUTION_ASSIGNMENT,
            GrantAuthority.RESERVATION,
            GrantAuthority.TASK_LEASE,
            GrantAuthority.EXECUTION_ATTEMPT,
            GrantAuthority.EXECUTION_OWNERSHIP_GENERATION);

    GrantFailureDisposition FAILURE_DISPOSITION =
            GrantFailureDisposition.NONE_AUTHORITATIVE;

    /** Returns a prior resolution only after checking the exact RequestWork context. */
    Optional<RequestWorkResult> findResolution(RequestWork requestWork);

    /**
     * Validates the durable host/runtime registration authority before RequestWork is accepted.
     * Missing, stale, disconnected, or incarnation-mismatched authority fails closed.
     */
    Optional<RequestWorkFailureReason> validateRegistration(RequestWork requestWork);

    /** Atomically records a terminal result, returning an already-recorded resolution on a race. */
    RequestWorkResult resolveTerminal(
            RequestWork requestWork,
            RequestWorkResult terminalResult);

    /** Executes ASSIGNMENT_GRANT_V1 as the single Task D transaction boundary. */
    RequestWorkResult tryGrant(AtomicAssignmentGrantCommand command);

    enum GrantAuthority {
        EXECUTION_ASSIGNMENT,
        RESERVATION,
        TASK_LEASE,
        EXECUTION_ATTEMPT,
        EXECUTION_OWNERSHIP_GENERATION
    }

    enum GrantFailureDisposition {
        NONE_AUTHORITATIVE
    }
}
