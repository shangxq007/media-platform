package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.AttemptCancellation;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.AttemptCancellationResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.BackendLocalRetry;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.BackendLocalRetryResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.DisconnectStatus;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.HostDisconnect;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseExpiry;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseExpiryResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseHeartbeat;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseHeartbeatStatus;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseOwnershipFence;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmission;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmissionChecks;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmissionDecision;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmissionDeclineReason;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LocalAdmissionResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LowTelemetryEvidence;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LowTelemetryResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.PhysicalReleaseConfirmation;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.PhysicalReleaseResult;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.WorkerDisconnect;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.infrastructure.JooqExecutionAuthorityBoundary;
import com.example.platform.workerfabric.infrastructure.JooqExecutionLifecycleBoundary;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.infrastructure.StaleOwnershipGenerationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** L1-L10 and A1-A9 acceptance against the canonical PostgreSQL lifecycle authority. */
class ExecutionLifecyclePostgresTest extends PostgresTestContainerSupport {

    private static final Instant GRANTED_AT = TaskCTestFixture.NOW;
    private static final Instant HEARTBEAT_AT = GRANTED_AT.plusSeconds(20);
    private static final Instant EXPIRED_AT = GRANTED_AT.plusSeconds(90);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(GRANTED_AT, ZoneOffset.UTC);
    private static final LocalAdmissionChecks ACCEPTABLE =
            new LocalAdmissionChecks(true, true, true, true, true);
    private static final LocalAdmissionChecks LOCAL_PRESSURE_DECLINE =
            new LocalAdmissionChecks(true, false, true, true, true);

    private static DataSource dataSource;
    private static DSLContext dsl;

    private JooqAtomicAssignmentGrantBoundary grants;
    private JooqExecutionLifecycleBoundary lifecycle;

    @BeforeAll
    static void startDatabaseAuthority() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        AssignmentGrantPostgresFixture.migrate(dataSource);
    }

    @AfterAll
    static void closeDatabaseAuthority() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void resetAuthority() {
        AssignmentGrantPostgresFixture.truncate(dsl);
        grants = new JooqAtomicAssignmentGrantBoundary(
                dsl, FIXED_CLOCK, LeaseRenewalContract.NATIVE_PULL_V1);
        lifecycle = new JooqExecutionLifecycleBoundary(dsl);
    }

    @Test
    void l1HeartbeatCurrentGenerationAccepted() {
        AssignmentGrant grant = grant("l1", 201);

        var result = lifecycle.heartbeat(new LeaseHeartbeat(fence(grant), HEARTBEAT_AT));

        assertThat(result.status()).isEqualTo(LeaseHeartbeatStatus.ACCEPTED);
        assertThat(result.renewedExpiresAt())
                .contains(HEARTBEAT_AT.plus(LeaseRenewalContract.NATIVE_PULL_V1.leaseDuration()));
        assertThat(instant("wf_task_lease", "expires_at", "lease_id", grant.lease().id().value()))
                .isEqualTo(result.renewedExpiresAt().orElseThrow());
    }

    @Test
    void l2StaleHeartbeatRejectedAsIdempotentNoop() {
        AssignmentGrant old = grant("l2-old", 202);
        lifecycle.expireLease(new LeaseExpiry(old.lease().id(), EXPIRED_AT));
        AssignmentGrant current = retry("l2-current", 202);

        var first = lifecycle.heartbeat(new LeaseHeartbeat(fence(old), EXPIRED_AT.plusSeconds(1)));
        var duplicate = lifecycle.heartbeat(
                new LeaseHeartbeat(fence(old), EXPIRED_AT.plusSeconds(1)));

        assertThat(first.status()).isEqualTo(LeaseHeartbeatStatus.STALE_OWNER_NOOP);
        assertThat(duplicate).isEqualTo(first);
        assertThat(current.attempt().ownershipGeneration().value()).isEqualTo(2L);
        assertThat(activeLeaseCount(current.executableTaskId().sha256Hex())).isOne();
    }

    @Test
    void l3LeaseExpiryLosesOwnershipWithoutAutomaticRelease() {
        AssignmentGrant grant = grant("l3", 203);

        assertThat(lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT)))
                .isEqualTo(LeaseExpiryResult.OWNERSHIP_LOST_RECOVERY_HOLD);

        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isTrue();
        assertThat(reservationState(grant)).isNotEqualTo(ReservationState.RELEASED);
        assertThat(activeLeaseCount(grant.executableTaskId().sha256Hex())).isZero();
    }

    @Test
    void l4ExpiredUnconfirmedReservationEntersRecoveryHold() {
        AssignmentGrant grant = grant("l4", 204);

        lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT));

        assertThat(reservationState(grant)).isEqualTo(ReservationState.RECOVERY_HOLD);
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.ABANDONED);
    }

    @Test
    void l5WorkerDisconnectPreventsNewAssignmentToRuntime() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("l5");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(205);
        AssignmentGrant grant = grant(runtime, candidate);

        var disconnected = lifecycle.disconnectWorker(new WorkerDisconnect(
                grant.assignment().workerRuntimeId(),
                grant.assignment().workerRuntimeIncarnationId(),
                EXPIRED_AT));
        RequestWork retryRequest = runtime.requestWithId("request-l5-retry");
        RequestWorkResult result = match(retryRequest, runtime.context(), candidate);

        assertThat(disconnected.status()).isEqualTo(DisconnectStatus.RECORDED);
        assertThat(disconnected.ownershipsClosed()).isOne();
        assertThat(result).isEqualTo(new RequestWorkResult.Rejected(
                retryRequest.requestWorkId(),
                RequestWorkFailureReason.WORKER_RUNTIME_UNHEALTHY));
        assertThat(count("wf_execution_attempt")).isOne();
    }

    @Test
    void hostDisconnectClosesOwnershipAndPreventsAssignmentOnDisconnectedIncarnation() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("host-disconnect");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(220);
        AssignmentGrant grant = grant(runtime, candidate);

        var disconnected = lifecycle.disconnectHost(new HostDisconnect(
                grant.assignment().physicalHostId(),
                grant.assignment().physicalHostIncarnationId(),
                EXPIRED_AT));
        RequestWork retryRequest = runtime.requestWithId("request-host-disconnect-retry");
        RequestWorkResult result = match(retryRequest, runtime.context(), candidate);

        assertThat(disconnected.status()).isEqualTo(DisconnectStatus.RECORDED);
        assertThat(disconnected.ownershipsClosed()).isOne();
        assertThat(result).isEqualTo(new RequestWorkResult.Rejected(
                retryRequest.requestWorkId(),
                RequestWorkFailureReason.PHYSICAL_HOST_UNAVAILABLE));
    }

    @Test
    void l6ReplacedRuntimeIncarnationCannotReclaimFromStaleReconnect() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("l6");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(206);
        AssignmentGrant grant = grant(runtime, candidate);
        var registrations = new JooqWorkerFabricRegistrationBoundary(dsl);

        registrations.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                grant.assignment().workerRuntimeId(),
                WorkerRuntimeIncarnationId.of("runtime-inc-l6-new"),
                grant.assignment().physicalHostId(),
                grant.assignment().physicalHostIncarnationId(),
                HEARTBEAT_AT,
                GRANTED_AT.plusSeconds(3500)));

        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isTrue();
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.ABANDONED);
        assertThat(lifecycle.heartbeat(new LeaseHeartbeat(
                        fence(grant), EXPIRED_AT.plusSeconds(1))).status())
                .isEqualTo(LeaseHeartbeatStatus.STALE_OWNER_NOOP);

        RequestWork oldReconnect = runtime.requestWithId("request-l6-old-reconnect");
        assertThat(match(oldReconnect, runtime.context(), candidate))
                .isEqualTo(new RequestWorkResult.Rejected(
                        oldReconnect.requestWorkId(),
                        RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH));
    }

    @Test
    void r2RuntimeRegistrationReplacementAtomicallyFencesAllFiveStalePaths() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("r2-runtime");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(221);
        AssignmentGrant grant = grant(runtime, candidate);
        var registrations = new JooqWorkerFabricRegistrationBoundary(dsl);

        registrations.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                grant.assignment().workerRuntimeId(),
                WorkerRuntimeIncarnationId.of("runtime-inc-r2-current"),
                grant.assignment().physicalHostId(),
                grant.assignment().physicalHostIncarnationId(),
                HEARTBEAT_AT,
                GRANTED_AT.plusSeconds(3500)));

        assertThat(dsl.fetchOne(
                        "select worker_runtime_incarnation_id from wf_runtime_registration "
                                + "where worker_runtime_id = ? and active",
                        grant.assignment().workerRuntimeId().value())
                .get(0, String.class))
                .isEqualTo("runtime-inc-r2-current");
        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isTrue();
        assertThat(activeLeaseCount(grant.executableTaskId().sha256Hex())).isZero();
        assertThat(reservationState(grant)).isEqualTo(ReservationState.RECOVERY_HOLD);

        assertThat(match(runtime.requestWork(), runtime.context(), candidate))
                .isEqualTo(new RequestWorkResult.Rejected(
                        runtime.requestWork().requestWorkId(),
                        RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH));
        assertThat(lifecycle.heartbeat(new LeaseHeartbeat(fence(grant), HEARTBEAT_AT)).status())
                .isEqualTo(LeaseHeartbeatStatus.STALE_OWNER_NOOP);
        assertThat(lifecycle.recordLocalAdmission(accept(grant)))
                .isEqualTo(LocalAdmissionResult.STALE_OWNER_NOOP);

        NativeWorkerBackendExecutionHandle handle = NativeWorkerBackendExecutionHandle.forLease(
                grant.attempt().id(), grant.attempt().ownershipGeneration(), grant.lease().id());
        var authority = new JooqExecutionAuthorityBoundary(dsl, FIXED_CLOCK);
        ExecutionObservation staleObservation = new ExecutionObservation(
                new ObservationId("observation-r2-stale"),
                grant.attempt().id(),
                grant.attempt().ownershipGeneration(),
                handle,
                TaskFTestFixture.BINDING,
                ObservedExecutionState.SUCCEEDED,
                HEARTBEAT_AT,
                Optional.empty());
        assertThat(authority.ingest(staleObservation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult.RECORDED_STALE_EVIDENCE);

        CompletionEvidence staleCompletion = new CompletionEvidence(
                new CompletionEventId("completion-r2-stale"),
                handle,
                grant.executableTaskId(),
                ObservedExecutionState.SUCCEEDED,
                new ExpectedOutputValidation(
                        "output-r2-stale", ExpectedOutputValidation.Status.VALID));
        assertThat(authority.completeIfCurrent(
                        staleCompletion,
                        new ArtifactCommitEvidence("artifact-r2-stale", HEARTBEAT_AT)))
                .isEqualTo(CompletionDecision.STALE_ATTEMPT_REJECTED);
        assertThat(count("wf_completion_event")).isZero();
    }

    @Test
    void r2HostRegistrationReplacementAtomicallyFencesOwnershipAndOldRuntime() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("r2-host");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(222);
        AssignmentGrant grant = grant(runtime, candidate);
        PhysicalHostIncarnationId replacementIncarnation =
                PhysicalHostIncarnationId.of("host-inc-r2-current");
        HostResourceSnapshot prior = runtime.requestWork().hostResourceSnapshot();
        HostResourceSnapshot replacementSnapshot = new HostResourceSnapshot(
                prior.physicalHostId(),
                replacementIncarnation,
                HostResourceSnapshotGeneration.first(),
                HEARTBEAT_AT,
                prior.schemaVersion(),
                prior.staticCapacity(),
                prior.observedUsage(),
                Optional.empty());

        new JooqWorkerFabricRegistrationBoundary(dsl).registerHost(
                new WorkerFabricRegistrationBoundary.HostRegistration(
                        prior.physicalHostId(),
                        replacementIncarnation,
                        replacementSnapshot,
                        SafetyHeadroom.none(),
                        HEARTBEAT_AT,
                        GRANTED_AT.plusSeconds(3600)));

        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isTrue();
        assertThat(activeLeaseCount(grant.executableTaskId().sha256Hex())).isZero();
        assertThat(dsl.fetchOne(
                        "select count(*) from wf_runtime_registration where worker_runtime_id = ? and active",
                        grant.assignment().workerRuntimeId().value())
                .get(0, Integer.class))
                .isZero();
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.ABANDONED);
    }

    @Test
    void r2FailedRegistrationReplacementRollsBackIdentityAndOwnershipFenceTogether() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("r2-rollback");
        AssignmentGrant grant = grant(runtime, TaskCTestFixture.candidate(223));
        dsl.execute("""
            create function wf_test_fail_runtime_replacement() returns trigger language plpgsql as $$
            begin
                if new.worker_runtime_incarnation_id = 'runtime-inc-r2-rollback-new' then
                    raise exception 'injected registration replacement failure';
                end if;
                return new;
            end
            $$
            """);
        dsl.execute("""
            create trigger wf_test_fail_runtime_replacement
            before insert on wf_runtime_registration
            for each row execute function wf_test_fail_runtime_replacement()
            """);
        try {
            assertThatThrownBy(() -> new JooqWorkerFabricRegistrationBoundary(dsl).registerRuntime(
                            new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                                    grant.assignment().workerRuntimeId(),
                                    WorkerRuntimeIncarnationId.of(
                                            "runtime-inc-r2-rollback-new"),
                                    grant.assignment().physicalHostId(),
                                    grant.assignment().physicalHostIncarnationId(),
                                    HEARTBEAT_AT,
                                    GRANTED_AT.plusSeconds(3500))))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("injected registration replacement failure");
        } finally {
            dsl.execute("drop trigger if exists wf_test_fail_runtime_replacement "
                    + "on wf_runtime_registration");
            dsl.execute("drop function if exists wf_test_fail_runtime_replacement()");
        }

        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isFalse();
        assertThat(activeLeaseCount(grant.executableTaskId().sha256Hex())).isOne();
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.CREATED);
        assertThat(dsl.fetchOne(
                        "select worker_runtime_incarnation_id from wf_runtime_registration "
                                + "where worker_runtime_id = ? and active",
                        grant.assignment().workerRuntimeId().value())
                .get(0, String.class))
                .isEqualTo(grant.assignment().workerRuntimeIncarnationId().value());
    }

    @Test
    void canonicalObservationAndCompletionUseCurrentOwnershipAndCloseThroughTaskELifecycle() {
        AssignmentGrant grant = grant("r2-completion-current", 224);
        assertThat(lifecycle.recordLocalAdmission(accept(grant)))
                .isEqualTo(LocalAdmissionResult.ACCEPTED_RUNNING);
        NativeWorkerBackendExecutionHandle handle = NativeWorkerBackendExecutionHandle.forLease(
                grant.attempt().id(), grant.attempt().ownershipGeneration(), grant.lease().id());
        var authority = new JooqExecutionAuthorityBoundary(
                dsl, Clock.fixed(HEARTBEAT_AT.plusSeconds(1), ZoneOffset.UTC));
        ExecutionObservation observation = new ExecutionObservation(
                new ObservationId("observation-r2-current"),
                grant.attempt().id(),
                grant.attempt().ownershipGeneration(),
                handle,
                TaskFTestFixture.BINDING,
                ObservedExecutionState.SUCCEEDED,
                HEARTBEAT_AT,
                Optional.empty());
        CompletionEvidence completion = new CompletionEvidence(
                new CompletionEventId("completion-r2-current"),
                handle,
                grant.executableTaskId(),
                ObservedExecutionState.SUCCEEDED,
                new ExpectedOutputValidation(
                        "output-r2-current", ExpectedOutputValidation.Status.VALID));
        ArtifactCommitEvidence commit =
                new ArtifactCommitEvidence("artifact-r2-current", HEARTBEAT_AT);

        assertThat(authority.ingest(observation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult.RECORDED_CURRENT_EVIDENCE);
        assertThat(authority.completeIfCurrent(completion, commit))
                .isEqualTo(CompletionDecision.COMPLETED);
        assertThat(authority.completeIfCurrent(completion, commit))
                .isEqualTo(CompletionDecision.DUPLICATE_NOOP);
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.SUCCEEDED);
        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isTrue();
        assertThat(activeLeaseCount(grant.executableTaskId().sha256Hex())).isZero();
        assertThat(reservationState(grant)).isEqualTo(ReservationState.RECOVERY_HOLD);
        assertThat(count("wf_completion_event")).isOne();
    }

    @Test
    void l7NewGenerationElsewhereIsLegalWhileOldReservationRecoveryHeld() {
        AssignmentGrant old = grant("l7-old", 207);
        lifecycle.expireLease(new LeaseExpiry(old.lease().id(), EXPIRED_AT));

        AssignmentGrant current = retry("l7-elsewhere", 207);

        assertThat(current.attempt().ownershipGeneration().value()).isEqualTo(2L);
        assertThat(current.assignment().physicalHostId())
                .isNotEqualTo(old.assignment().physicalHostId());
        assertThat(reservationState(old)).isEqualTo(ReservationState.RECOVERY_HOLD);
        assertThat(reservationState(current)).isEqualTo(ReservationState.ACTIVE);
    }

    @Test
    void l8TypedPhysicalReleaseConfirmationReleasesReservation() {
        AssignmentGrant grant = grant("l8", 208);
        lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT));

        PhysicalReleaseResult result = lifecycle.confirmPhysicalRelease(
                confirmation("l8-confirmation", grant));

        assertThat(result).isEqualTo(PhysicalReleaseResult.RELEASED);
        assertThat(reservationState(grant)).isEqualTo(ReservationState.RELEASED);
    }

    @Test
    void l9LowTelemetryAloneCannotReleaseRecoveryHold() {
        AssignmentGrant grant = grant("l9", 209);
        lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT));

        LowTelemetryResult result = lifecycle.recordLowTelemetry(new LowTelemetryEvidence(
                grant.lease().id(), grant.assignment().reservationIds(), EXPIRED_AT.plusSeconds(1)));

        assertThat(result).isEqualTo(LowTelemetryResult.RECORDED_AS_NON_AUTHORITATIVE_NOOP);
        assertThat(reservationState(grant)).isEqualTo(ReservationState.RECOVERY_HOLD);
    }

    @Test
    void l10DuplicateLeaseExpiryProcessingIsIdempotent() {
        AssignmentGrant grant = grant("l10", 210);

        var first = lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT));
        var duplicate = lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT));

        assertThat(first).isEqualTo(LeaseExpiryResult.OWNERSHIP_LOST_RECOVERY_HOLD);
        assertThat(duplicate).isEqualTo(LeaseExpiryResult.ALREADY_PROCESSED_NOOP);
        assertThat(reservationState(grant)).isEqualTo(ReservationState.RECOVERY_HOLD);
        assertThat(count("wf_execution_attempt")).isOne();
    }

    @Test
    void a1AcceptMovesAttemptToRunningAndDuplicateIsNoop() {
        AssignmentGrant grant = grant("a1", 211);
        LocalAdmission admission = accept(grant);

        assertThat(lifecycle.recordLocalAdmission(admission))
                .isEqualTo(LocalAdmissionResult.ACCEPTED_RUNNING);
        assertThat(lifecycle.recordLocalAdmission(admission))
                .isEqualTo(LocalAdmissionResult.DUPLICATE_NOOP);
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.RUNNING);
    }

    @Test
    void a2DeclinePreservesAttemptHistory() {
        AssignmentGrant grant = grant("a2", 212);

        assertThat(lifecycle.recordLocalAdmission(decline(grant, Optional.empty())))
                .isEqualTo(LocalAdmissionResult.DECLINED_RECOVERY_HOLD);

        assertThat(count("wf_execution_attempt")).isOne();
        assertThat(attemptState(grant.attempt().id())).isEqualTo(ExecutionAttemptState.ABANDONED);
        assertThat(count("wf_local_admission")).isOne();
    }

    @Test
    void a3DeclineReconcilesLeaseAssignmentAndReservation() {
        AssignmentGrant grant = grant("a3", 213);

        lifecycle.recordLocalAdmission(decline(grant, Optional.empty()));

        assertThat(activeLeaseCount(grant.executableTaskId().sha256Hex())).isZero();
        assertThat(taskClaimable(grant.executableTaskId().sha256Hex())).isTrue();
        assertThat(reservationState(grant)).isEqualTo(ReservationState.RECOVERY_HOLD);
        assertThat(count("wf_execution_assignment")).isOne();
    }

    @Test
    void a4StaleAdmissionResponseCannotAffectNewerGeneration() {
        AssignmentGrant old = grant("a4-old", 214);
        lifecycle.expireLease(new LeaseExpiry(old.lease().id(), EXPIRED_AT));
        AssignmentGrant current = retry("a4-new", 214);

        assertThat(lifecycle.recordLocalAdmission(accept(old)))
                .isEqualTo(LocalAdmissionResult.STALE_OWNER_NOOP);
        assertThat(attemptState(current.attempt().id())).isEqualTo(ExecutionAttemptState.CREATED);
        assertThat(activeLeaseCount(current.executableTaskId().sha256Hex())).isOne();
    }

    @Test
    void a5CancelCurrentAttemptFencesLateWorkerCompletion() {
        AssignmentGrant old = grant("a5-old", 215);
        lifecycle.recordLocalAdmission(accept(old));

        assertThat(lifecycle.cancelAttempt(new AttemptCancellation(fence(old), EXPIRED_AT)))
                .isEqualTo(AttemptCancellationResult.CANCELLED_RECOVERY_HOLD);
        AssignmentGrant current = retry("a5-new", 215);

        assertThatThrownBy(() -> grants.transitionAttemptIfCurrent(
                        old.attempt().id(),
                        old.attempt().ownershipGeneration(),
                        ExecutionAttemptState.SUCCEEDED))
                .isInstanceOf(StaleOwnershipGenerationException.class);
        assertThat(attemptState(old.attempt().id())).isEqualTo(ExecutionAttemptState.CANCELLED);
        assertThat(attemptState(current.attempt().id())).isEqualTo(ExecutionAttemptState.CREATED);
    }

    @Test
    void a6PlatformRetryCreatesNewExecutionAttemptId() {
        AssignmentGrant old = expireThenRetrySource("a6-old", 216);

        AssignmentGrant current = retry("a6-new", 216);

        assertThat(current.attempt().id()).isNotEqualTo(old.attempt().id());
        assertThat(count("wf_execution_attempt")).isEqualTo(2);
    }

    @Test
    void a7PlatformRetryCreatesNewOwnershipGeneration() {
        AssignmentGrant old = expireThenRetrySource("a7-old", 217);

        AssignmentGrant current = retry("a7-new", 217);

        assertThat(current.attempt().ownershipGeneration())
                .isEqualTo(old.attempt().ownershipGeneration().next());
    }

    @Test
    void a8PlatformRetryRetainsExecutableTaskId() {
        AssignmentGrant old = expireThenRetrySource("a8-old", 218);

        AssignmentGrant current = retry("a8-new", 218);

        assertThat(current.executableTaskId()).isEqualTo(old.executableTaskId());
    }

    @Test
    void a9BackendLocalRetryDoesNotCreatePlatformAttempt() {
        AssignmentGrant grant = grant("a9", 219);
        int before = count("wf_execution_attempt");

        BackendLocalRetryResult result = lifecycle.recordBackendLocalRetry(new BackendLocalRetry(
                grant.attempt().id(),
                grant.attempt().ownershipGeneration(),
                "backend-local-retry-a9",
                HEARTBEAT_AT));

        assertThat(result)
                .isEqualTo(BackendLocalRetryResult.ACKNOWLEDGED_WITHOUT_PLATFORM_ATTEMPT);
        assertThat(count("wf_execution_attempt")).isEqualTo(before);
    }

    private AssignmentGrant expireThenRetrySource(String suffix, long taskIdentity) {
        AssignmentGrant grant = grant(suffix, taskIdentity);
        lifecycle.expireLease(new LeaseExpiry(grant.lease().id(), EXPIRED_AT));
        return grant;
    }

    private AssignmentGrant grant(String runtimeSuffix, long taskIdentity) {
        return grant(
                TaskCTestFixture.runtime(runtimeSuffix),
                TaskCTestFixture.candidate(taskIdentity));
    }

    private AssignmentGrant retry(String runtimeSuffix, long taskIdentity) {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime(runtimeSuffix);
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(taskIdentity);
        RequestWork request = runtime.requestWithId("request-" + runtimeSuffix + "-retry");
        register(request);
        return granted(match(request, runtime.context(), candidate));
    }

    private AssignmentGrant grant(
            TaskCTestFixture.RuntimeFixture runtime,
            TaskCTestFixture.CandidateFixture candidate) {
        register(runtime.requestWork());
        return granted(match(runtime.requestWork(), runtime.context(), candidate));
    }

    private RequestWorkResult match(
            RequestWork request,
            RequestWorkValidationContext context,
            TaskCTestFixture.CandidateFixture candidate) {
        return new CentralWorkMatcher(grants).match(
                request, context, List.of(candidate.candidate()));
    }

    private static void register(RequestWork request) {
        var registrations = new JooqWorkerFabricRegistrationBoundary(dsl);
        registrations.registerHost(new WorkerFabricRegistrationBoundary.HostRegistration(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot(),
                SafetyHeadroom.none(),
                FIXED_CLOCK.instant().minusSeconds(1),
                FIXED_CLOCK.instant().plusSeconds(3600)));
        registrations.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                request.workerRuntimeId(),
                request.workerRuntimeIncarnationId(),
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                FIXED_CLOCK.instant().minusSeconds(1),
                FIXED_CLOCK.instant().plusSeconds(3600)));
    }

    private static AssignmentGrant granted(RequestWorkResult result) {
        assertThat(result).isInstanceOf(RequestWorkResult.Granted.class);
        return (AssignmentGrant) ((RequestWorkResult.Granted) result).grant();
    }

    private static LeaseOwnershipFence fence(AssignmentGrant grant) {
        return LeaseOwnershipFence.from(grant.lease());
    }

    private static LocalAdmission accept(AssignmentGrant grant) {
        return new LocalAdmission(
                fence(grant),
                LocalAdmissionDecision.ACCEPT,
                ACCEPTABLE,
                Optional.empty(),
                Optional.empty(),
                HEARTBEAT_AT);
    }

    private static LocalAdmission decline(
            AssignmentGrant grant,
            Optional<PhysicalReleaseConfirmation> confirmation) {
        return new LocalAdmission(
                fence(grant),
                LocalAdmissionDecision.DECLINE,
                LOCAL_PRESSURE_DECLINE,
                Optional.of(LocalAdmissionDeclineReason.UNEXPECTED_LOCAL_PRESSURE),
                confirmation,
                HEARTBEAT_AT);
    }

    private static PhysicalReleaseConfirmation confirmation(
            String confirmationId, AssignmentGrant grant) {
        return new PhysicalReleaseConfirmation(
                confirmationId,
                fence(grant),
                grant.assignment().reservationIds(),
                EXPIRED_AT.plusSeconds(1));
    }

    private static ReservationState reservationState(AssignmentGrant grant) {
        return ReservationState.valueOf(dsl.fetchOne(
                        "select state from wf_reservation where reservation_id = ?",
                        grant.reservations().getFirst().id().value())
                .get("state", String.class));
    }

    private static ExecutionAttemptState attemptState(ExecutionAttemptId attemptId) {
        return ExecutionAttemptState.valueOf(dsl.fetchOne(
                        "select state from wf_execution_attempt where attempt_id = ?",
                        attemptId.value())
                .get("state", String.class));
    }

    private static boolean taskClaimable(String taskId) {
        return dsl.fetchOne(
                        "select claimable from wf_task_ownership where task_id = ?", taskId)
                .get("claimable", Boolean.class);
    }

    private static int activeLeaseCount(String taskId) {
        return dsl.fetchOne(
                        "select count(*) from wf_task_lease where task_id = ? and active", taskId)
                .get(0, Integer.class);
    }

    private static int count(String table) {
        return dsl.fetchOne("select count(*) from " + table).get(0, Integer.class);
    }

    private static Instant instant(
            String table, String field, String keyField, String keyValue) {
        return dsl.fetchOne(
                        "select " + field + " from " + table + " where " + keyField + " = ?",
                        keyValue)
                .get(field, java.time.OffsetDateTime.class)
                .toInstant();
    }
}
