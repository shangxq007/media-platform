package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Database-authoritative Task E boundary for Native Pull lease, admission, and recovery events.
 *
 * <p>All worker-originated mutations carry the exact lease, attempt, generation, runtime
 * incarnation, and fencing token. Delivery is at-least-once; implementations must make duplicate
 * and stale messages idempotent no-ops. A worker can answer only for its granted task and can never
 * select a replacement task through this boundary.
 */
public interface ExecutionLifecycleBoundary {

    LeaseHeartbeatResult heartbeat(LeaseHeartbeat heartbeat);

    LeaseExpiryResult expireLease(LeaseExpiry expiry);

    DisconnectResult disconnectWorker(WorkerDisconnect disconnect);

    DisconnectResult disconnectHost(HostDisconnect disconnect);

    LocalAdmissionResult recordLocalAdmission(LocalAdmission admission);

    AttemptCancellationResult cancelAttempt(AttemptCancellation cancellation);

    PhysicalReleaseResult confirmPhysicalRelease(PhysicalReleaseConfirmation confirmation);

    LowTelemetryResult recordLowTelemetry(LowTelemetryEvidence evidence);

    BackendLocalRetryResult recordBackendLocalRetry(BackendLocalRetry retry);

    /** Complete identity/fence supplied by a Native Pull lease owner. */
    record LeaseOwnershipFence(
            LeaseId leaseId,
            ExecutableTaskId executableTaskId,
            ExecutionAssignmentId executionAssignmentId,
            ExecutionAttemptId executionAttemptId,
            ExecutionOwnershipGeneration ownershipGeneration,
            WorkerRuntimeId workerRuntimeId,
            WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
            LeaseFencingToken fencingToken) {

        public LeaseOwnershipFence {
            Objects.requireNonNull(leaseId, "leaseId");
            Objects.requireNonNull(executableTaskId, "executableTaskId");
            Objects.requireNonNull(executionAssignmentId, "executionAssignmentId");
            Objects.requireNonNull(executionAttemptId, "executionAttemptId");
            Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
            Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
            Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
            Objects.requireNonNull(fencingToken, "fencingToken");
        }

        public static LeaseOwnershipFence from(TaskLease lease) {
            Objects.requireNonNull(lease, "lease");
            return new LeaseOwnershipFence(
                    lease.id(),
                    lease.executableTaskId(),
                    lease.executionAssignmentId(),
                    lease.executionAttemptId(),
                    lease.ownershipGeneration(),
                    lease.workerRuntimeId(),
                    lease.workerRuntimeIncarnationId(),
                    lease.fencingToken());
        }
    }

    record LeaseHeartbeat(LeaseOwnershipFence fence, Instant receivedAt) {

        public LeaseHeartbeat {
            Objects.requireNonNull(fence, "fence");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }

    record LeaseHeartbeatResult(LeaseHeartbeatStatus status, Optional<Instant> renewedExpiresAt) {

        public LeaseHeartbeatResult {
            Objects.requireNonNull(status, "status");
            renewedExpiresAt = Objects.requireNonNull(renewedExpiresAt, "renewedExpiresAt");
            if ((status == LeaseHeartbeatStatus.ACCEPTED) != renewedExpiresAt.isPresent()) {
                throw new IllegalArgumentException(
                        "only an accepted heartbeat carries a renewed expiry");
            }
        }
    }

    enum LeaseHeartbeatStatus {
        ACCEPTED,
        DUPLICATE_OR_OUT_OF_ORDER_NOOP,
        EXPIRED_NOOP,
        STALE_OWNER_NOOP
    }

    record LeaseExpiry(LeaseId leaseId, Instant detectedAt) {

        public LeaseExpiry {
            Objects.requireNonNull(leaseId, "leaseId");
            Objects.requireNonNull(detectedAt, "detectedAt");
        }
    }

    enum LeaseExpiryResult {
        OWNERSHIP_LOST_RECOVERY_HOLD,
        NOT_YET_EXPIRED,
        ALREADY_PROCESSED_NOOP,
        STALE_LEASE_NOOP
    }

    record WorkerDisconnect(
            WorkerRuntimeId workerRuntimeId,
            WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
            Instant detectedAt) {

        public WorkerDisconnect {
            Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
            Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
            Objects.requireNonNull(detectedAt, "detectedAt");
        }
    }

    record HostDisconnect(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            Instant detectedAt) {

        public HostDisconnect {
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
            Objects.requireNonNull(detectedAt, "detectedAt");
        }
    }

    record DisconnectResult(DisconnectStatus status, int ownershipsClosed) {

        public DisconnectResult {
            Objects.requireNonNull(status, "status");
            if (ownershipsClosed < 0) {
                throw new IllegalArgumentException("ownershipsClosed must not be negative");
            }
        }
    }

    enum DisconnectStatus {
        RECORDED,
        STALE_INCARNATION_NOOP
    }

    /** Last-mile facts checked only after a central grant selected the exact task. */
    record LocalAdmissionChecks(
            boolean processRuntimeReady,
            boolean localPressureAcceptable,
            boolean deviceReady,
            boolean artifactStagingFeasible,
            boolean sandboxRuntimeReady) {

        public boolean allAccepted() {
            return processRuntimeReady
                    && localPressureAcceptable
                    && deviceReady
                    && artifactStagingFeasible
                    && sandboxRuntimeReady;
        }
    }

    record LocalAdmission(
            LeaseOwnershipFence fence,
            LocalAdmissionDecision decision,
            LocalAdmissionChecks checks,
            Optional<LocalAdmissionDeclineReason> declineReason,
            Optional<PhysicalReleaseConfirmation> physicalReleaseConfirmation,
            Instant receivedAt) {

        public LocalAdmission {
            Objects.requireNonNull(fence, "fence");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(checks, "checks");
            declineReason = Objects.requireNonNull(declineReason, "declineReason");
            physicalReleaseConfirmation = Objects.requireNonNull(
                    physicalReleaseConfirmation, "physicalReleaseConfirmation");
            Objects.requireNonNull(receivedAt, "receivedAt");
            if (decision == LocalAdmissionDecision.ACCEPT
                    && (!checks.allAccepted()
                            || declineReason.isPresent()
                            || physicalReleaseConfirmation.isPresent())) {
                throw new IllegalArgumentException(
                        "ACCEPT requires all checks and no decline-only evidence");
            }
            if (decision == LocalAdmissionDecision.DECLINE
                    && (checks.allAccepted() || declineReason.isEmpty())) {
                throw new IllegalArgumentException(
                        "DECLINE requires a failed check and typed reason");
            }
            physicalReleaseConfirmation.ifPresent(confirmation -> {
                if (!confirmation.fence().equals(fence)) {
                    throw new IllegalArgumentException(
                            "admission release confirmation must carry the same ownership fence");
                }
            });
        }
    }

    enum LocalAdmissionDecision {
        ACCEPT,
        DECLINE
    }

    enum LocalAdmissionDeclineReason {
        PROCESS_RUNTIME_NOT_READY,
        UNEXPECTED_LOCAL_PRESSURE,
        DEVICE_STATE_CHANGED,
        ARTIFACT_STAGING_NOT_FEASIBLE,
        SANDBOX_RUNTIME_NOT_READY,
        UNKNOWN_FAIL_CLOSED
    }

    enum LocalAdmissionResult {
        ACCEPTED_RUNNING,
        DECLINED_RELEASED,
        DECLINED_RECOVERY_HOLD,
        DUPLICATE_NOOP,
        STALE_OWNER_NOOP
    }

    record AttemptCancellation(LeaseOwnershipFence fence, Instant receivedAt) {

        public AttemptCancellation {
            Objects.requireNonNull(fence, "fence");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }

    enum AttemptCancellationResult {
        CANCELLED_RECOVERY_HOLD,
        DUPLICATE_NOOP,
        STALE_OWNER_NOOP
    }

    /** Typed evidence that every reservation bound to one closed lease is physically released. */
    record PhysicalReleaseConfirmation(
            String confirmationId,
            LeaseOwnershipFence fence,
            Set<ReservationId> reservationIds,
            Instant confirmedAt) {

        public PhysicalReleaseConfirmation {
            Objects.requireNonNull(confirmationId, "confirmationId");
            if (confirmationId.isBlank()) {
                throw new IllegalArgumentException("confirmationId must not be blank");
            }
            Objects.requireNonNull(fence, "fence");
            Objects.requireNonNull(reservationIds, "reservationIds");
            if (reservationIds.stream().anyMatch(Objects::isNull)) {
                throw new NullPointerException("reservationIds element");
            }
            reservationIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(
                    reservationIds.stream()
                            .sorted(Comparator.comparing(ReservationId::value))
                            .toList()));
            if (reservationIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "physical release confirmation requires reservations");
            }
            Objects.requireNonNull(confirmedAt, "confirmedAt");
        }
    }

    enum PhysicalReleaseResult {
        RELEASED,
        ALREADY_RELEASED_NOOP,
        OWNERSHIP_STILL_ACTIVE_NOOP,
        STALE_OR_INCOMPLETE_CONFIRMATION_NOOP
    }

    /** Non-authoritative evidence which can trigger reconciliation but can never release capacity. */
    record LowTelemetryEvidence(
            LeaseId leaseId,
            Set<ReservationId> reservationIds,
            Instant observedAt) {

        public LowTelemetryEvidence {
            Objects.requireNonNull(leaseId, "leaseId");
            Objects.requireNonNull(reservationIds, "reservationIds");
            if (reservationIds.isEmpty()
                    || reservationIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "low telemetry evidence requires non-null reservations");
            }
            reservationIds = Set.copyOf(reservationIds);
            Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    enum LowTelemetryResult {
        RECORDED_AS_NON_AUTHORITATIVE_NOOP
    }

    /** A retry internal to one backend submission; it never allocates a platform attempt. */
    record BackendLocalRetry(
            ExecutionAttemptId executionAttemptId,
            ExecutionOwnershipGeneration ownershipGeneration,
            String backendLocalRetryId,
            Instant observedAt) {

        public BackendLocalRetry {
            Objects.requireNonNull(executionAttemptId, "executionAttemptId");
            Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
            Objects.requireNonNull(backendLocalRetryId, "backendLocalRetryId");
            if (backendLocalRetryId.isBlank()) {
                throw new IllegalArgumentException("backendLocalRetryId must not be blank");
            }
            Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    enum BackendLocalRetryResult {
        ACKNOWLEDGED_WITHOUT_PLATFORM_ATTEMPT
    }
}
