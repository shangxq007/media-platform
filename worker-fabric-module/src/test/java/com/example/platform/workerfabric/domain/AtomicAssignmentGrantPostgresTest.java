package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.infrastructure.StaleOwnershipGenerationException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D1-D6 acceptance against the repository-standard Testcontainers PostgreSQL authority. */
class AtomicAssignmentGrantPostgresTest extends PostgresTestContainerSupport {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    private static DataSource dataSource;
    private static DSLContext dsl;

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
    void resetAuthoritativeRows() {
        removeFailureInjection();
        AssignmentGrantPostgresFixture.truncate(dsl);
    }

    @AfterEach
    void removeInjectedFailure() {
        removeFailureInjection();
    }

    @Test
    void d1AssignmentGrantRollbackLeavesNoPartialAuthoritativeRecords() {
        dsl.execute("""
            create function wf_test_fail_lease_insert() returns trigger language plpgsql as $$
            begin
                raise exception 'D1 injected lease insert failure';
            end
            $$
            """);
        dsl.execute("""
            create trigger wf_test_fail_lease_insert before insert on wf_task_lease
            for each row execute function wf_test_fail_lease_insert()
            """);
        var runtime = TaskCTestFixture.runtime("d1");
        var candidate = TaskCTestFixture.candidate(101);
        var boundary = boundary(dsl);

        assertThatThrownBy(() -> match(boundary, runtime, candidate))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("D1 injected lease insert failure");

        assertCounts(0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void d2DuplicateRequestWorkTransactionRetryHasNoDuplicateActiveOwnership() {
        var runtime = TaskCTestFixture.runtime("d2");
        var candidate = TaskCTestFixture.candidate(102);
        var boundary = boundary(dsl);

        RequestWorkResult first = match(boundary, runtime, candidate);
        RequestWorkResult replay = match(boundary, runtime, candidate);

        assertThat(first).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(replay).isEqualTo(first);
        assertCounts(1, 1, 1, 1, 1, 1, 1);
        assertThat(activeLeaseCount(candidate.task().id().sha256Hex())).isOne();
    }

    @Test
    void d3ConcurrentGrantRaceCreatesExactlyOneCurrentGeneration() throws Exception {
        var workerA = TaskCTestFixture.runtime("d3-a");
        var workerB = TaskCTestFixture.runtime("d3-b");
        var candidate = TaskCTestFixture.candidate(103);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<RequestWorkResult> first = executor.submit(() -> {
                start.await();
                return match(boundary(dsl), workerA, candidate);
            });
            Future<RequestWorkResult> second = executor.submit(() -> {
                start.await();
                return match(boundary(dsl), workerB, candidate);
            });
            start.countDown();

            List<RequestWorkResult> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(RequestWorkResult::granted).hasSize(1);
            assertThat(results).filteredOn(result -> !result.granted())
                    .singleElement().isInstanceOf(RequestWorkResult.NoWork.class);
            assertCounts(1, 1, 1, 1, 1, 2, 1);
            assertThat(dsl.fetchOne(
                    "select current_generation from wf_task_ownership where task_id = ?",
                    candidate.task().id().sha256Hex()).get(0, Long.class)).isEqualTo(1L);
            assertThat(activeLeaseCount(candidate.task().id().sha256Hex())).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void d4RepositoryReloadRecoversAuthoritativeGrantState() {
        var runtime = TaskCTestFixture.runtime("d4");
        var candidate = TaskCTestFixture.candidate(104);
        var firstProcess = boundary(dsl);
        RequestWorkResult granted = match(firstProcess, runtime, candidate);
        AssignmentGrant original = (AssignmentGrant)
                ((RequestWorkResult.Granted) granted).grant();

        DSLContext reloadedDsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        var restartedProcess = boundary(reloadedDsl);

        assertThat(restartedProcess.findResolution(runtime.requestWork()))
                .contains(granted);
        assertThat(restartedProcess.findGrant(original.assignment().id()))
                .contains(original);
        assertThat(restartedProcess.findCurrentGrant(candidate.task().id()))
                .contains(original);
    }

    @Test
    void d5StaleGenerationDatabaseWriteIsRejected() {
        var runtime = TaskCTestFixture.runtime("d5");
        var candidate = TaskCTestFixture.candidate(105);
        OffsetDateTime now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC);
        dsl.execute(
                """
                insert into wf_task_ownership (
                    task_id, current_generation, claimable, updated_at)
                values (?, 1, true, cast(? as timestamptz))
                """,
                candidate.task().id().sha256Hex(), now);
        dsl.execute(
                """
                insert into wf_execution_ownership_generation (task_id, generation, created_at)
                values (?, 1, cast(? as timestamptz))
                """,
                candidate.task().id().sha256Hex(), now);
        var boundary = boundary(dsl);
        AssignmentGrant grant = (AssignmentGrant)
                ((RequestWorkResult.Granted) match(boundary, runtime, candidate)).grant();

        assertThat(grant.attempt().ownershipGeneration().value()).isEqualTo(2L);
        assertThatThrownBy(() -> boundary.transitionAttemptIfCurrent(
                        grant.attempt().id(),
                        ExecutionOwnershipGeneration.first(),
                        ExecutionAttemptState.RUNNING))
                .isInstanceOf(StaleOwnershipGenerationException.class)
                .hasMessageContaining("current generation is 2");
        assertThat(boundary.findGrant(grant.assignment().id()))
                .get().extracting(loaded -> loaded.attempt().state())
                .isEqualTo(ExecutionAttemptState.CREATED);
    }

    @Test
    void d6ReservationLeaseAttemptReferentialInvariantsAreTransactional() {
        OffsetDateTime now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC);
        assertThatThrownBy(() -> dsl.execute(
                """
                insert into wf_reservation (
                    reservation_id, assignment_id, task_id, physical_host_id,
                    physical_host_incarnation_id,
                    kind, state, cpu_millicores, memory_bytes,
                    temporary_storage_bytes, created_at)
                values ('orphan-reservation', 'missing-assignment', 'task', 'host', 'host-inc',
                    'TASK', 'ACTIVE', 0, 0, 0, cast(? as timestamptz))
                """,
                now)).isInstanceOf(DataAccessException.class);
        dsl.execute(
                """
                insert into wf_execution_backend_selection (
                    selection_id, task_id, backend, placement_authority_scope,
                    active, selected_at)
                values ('orphan-backend-selection', 'task', 'NATIVE_PULL_WORKER',
                    'PLATFORM_MANAGED', true, cast(? as timestamptz))
                """,
                now);
        assertThatThrownBy(() -> dsl.execute(
                """
                insert into wf_execution_attempt (
                    attempt_id, task_id, generation, backend, state, backend_selection_id,
                    created_at, updated_at)
                values ('orphan-attempt', 'task', 1, 'NATIVE_PULL_WORKER', 'CREATED',
                    'orphan-backend-selection', cast(? as timestamptz), cast(? as timestamptz))
                """,
                now, now)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> dsl.execute(
                """
                insert into wf_task_lease (
                    lease_id, task_id, assignment_id, attempt_id, generation,
                    worker_runtime_id, worker_runtime_incarnation_id,
                    expires_at, last_heartbeat_at, heartbeat_interval_millis,
                    lease_duration_millis, fencing_token, active, created_at)
                values ('orphan-lease', 'task', 'missing-assignment', 'missing-attempt', 1,
                    'runtime', 'runtime-inc',
                    cast(? as timestamptz) + interval '60 seconds', cast(? as timestamptz),
                    15000, 60000, 'orphan-fence', true, cast(? as timestamptz))
                """,
                now, now, now)).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute("""
                insert into wf_execution_backend_selection (
                    selection_id, task_id, backend, placement_authority_scope,
                    active, selected_at)
                values ('selection-without-reservation', 'assignment-without-reservation',
                    'NATIVE_PULL_WORKER', 'PLATFORM_MANAGED', true, cast(? as timestamptz))
                """, now);
            tx.execute("""
                insert into wf_execution_ownership_generation (task_id, generation, created_at)
                values ('assignment-without-reservation', 1, cast(? as timestamptz))
                """, now);
            tx.execute("""
                insert into wf_execution_attempt (
                    attempt_id, task_id, generation, backend, state, backend_selection_id,
                    created_at, updated_at)
                values ('attempt-without-reservation', 'assignment-without-reservation', 1,
                    'NATIVE_PULL_WORKER', 'CREATED', 'selection-without-reservation',
                    cast(? as timestamptz), cast(? as timestamptz))
                """, now, now);
            tx.execute("""
                insert into wf_execution_assignment (
                    assignment_id, task_id, attempt_id, generation,
                    worker_runtime_id, worker_runtime_incarnation_id,
                    physical_host_id, physical_host_incarnation_id, created_at)
                values ('assignment-without-reservation', 'assignment-without-reservation',
                    'attempt-without-reservation', 1, 'runtime', 'runtime-inc',
                    'host', 'host-inc', cast(? as timestamptz))
                """, now);
        })).isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("has no reservation");

        var runtime = TaskCTestFixture.runtime("d6");
        var candidate = TaskCTestFixture.candidate(106);
        AssignmentGrant grant = (AssignmentGrant)
                ((RequestWorkResult.Granted) match(boundary(dsl), runtime, candidate)).grant();

        assertThat(dsl.fetchOne(
                """
                select count(*) from wf_task_lease l
                join wf_execution_assignment a
                  on (a.assignment_id, a.task_id, a.attempt_id, a.generation)
                   = (l.assignment_id, l.task_id, l.attempt_id, l.generation)
                join wf_execution_attempt e
                  on (e.attempt_id, e.task_id, e.generation)
                   = (a.attempt_id, a.task_id, a.generation)
                join wf_execution_ownership_generation g
                  on (g.task_id, g.generation) = (e.task_id, e.generation)
                join wf_task_lease_reservation lr on lr.lease_id = l.lease_id
                join wf_reservation r
                  on (r.reservation_id, r.assignment_id)
                   = (lr.reservation_id, lr.assignment_id)
                where a.assignment_id = ?
                """,
                grant.assignment().id().value()).get(0, Integer.class)).isOne();
        assertThat(grant.assignment().reservationIds())
                .isEqualTo(grant.lease().reservationIds());
        assertThat(grant.attempt().ownershipGeneration())
                .isEqualTo(grant.assignment().ownershipGeneration());
    }

    @Test
    void f2ConcurrentDifferentTasksOnSameHostCannotExhaustCpuTwice() throws Exception {
        var runtime = TaskCTestFixture.runtime("f2-host");
        var firstCandidate = TaskCTestFixture.candidate(
                301, new RuntimeResourceDemand(5_000, 0, 0, Map.of()));
        var secondCandidate = TaskCTestFixture.candidate(
                302, new RuntimeResourceDemand(5_000, 0, 0, Map.of()));
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));

        List<RequestWorkResult> results = raceDifferentTasks(
                runtime,
                firstCandidate,
                secondCandidate,
                "request-f2-host-a",
                "request-f2-host-b");

        assertThat(results).filteredOn(RequestWorkResult::granted).hasSize(1);
        assertThat(results).filteredOn(result -> !result.granted())
                .containsExactly(new RequestWorkResult.Rejected(
                        results.stream()
                                .filter(result -> !result.granted())
                                .findFirst().orElseThrow().requestWorkId(),
                        RequestWorkFailureReason.GRANT_CONFLICT));
        assertThat(count("wf_reservation")).isOne();
        assertThat(dsl.fetchOne(
                "select sum(cpu_millicores)::bigint from wf_reservation where state = 'ACTIVE'")
                .get(0, Long.class)).isEqualTo(5_000L);
    }

    @Test
    void f2ConcurrentDifferentTasksOnSameDeviceCannotExhaustDeviceTwice() throws Exception {
        var runtime = TaskCTestFixture.runtimeWithDeviceEvidence("f2-device");
        RuntimeResourceDemand.DeviceDemand deviceDemand = new RuntimeResourceDemand.DeviceDemand(
                TaskCTestFixture.DEVICE_ID, 0, 60, 0, 0);
        RuntimeResourceDemand demand = new RuntimeResourceDemand(
                0, 0, 0, Map.of(TaskCTestFixture.DEVICE_ID, deviceDemand));
        var firstCandidate = TaskCTestFixture.candidate(303, demand);
        var secondCandidate = TaskCTestFixture.candidate(304, demand);
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));

        List<RequestWorkResult> results = raceDifferentTasks(
                runtime,
                firstCandidate,
                secondCandidate,
                "request-f2-device-a",
                "request-f2-device-b");

        assertThat(results).filteredOn(RequestWorkResult::granted).hasSize(1);
        assertThat(results).filteredOn(result -> !result.granted())
                .singleElement()
                .isEqualTo(new RequestWorkResult.Rejected(
                        results.stream()
                                .filter(result -> !result.granted())
                                .findFirst().orElseThrow().requestWorkId(),
                        RequestWorkFailureReason.GRANT_CONFLICT));
        assertThat(dsl.fetchOne(
                "select sum(compute_units)::bigint from wf_reservation_device")
                .get(0, Long.class)).isEqualTo(60L);
    }

    @Test
    void f3UnregisteredWorkerIsRejectedBeforeRequestWorkAcceptance() {
        var runtime = TaskCTestFixture.runtime("f3-unregistered");
        RequestWork request = runtime.requestWork();
        new JooqWorkerFabricRegistrationBoundary(dsl).registerHost(
                new WorkerFabricRegistrationBoundary.HostRegistration(
                        request.physicalHostId(),
                        request.physicalHostIncarnationId(),
                        request.hostResourceSnapshot(),
                        SafetyHeadroom.none(),
                        FIXED_CLOCK.instant().minusSeconds(1),
                        FIXED_CLOCK.instant().plusSeconds(3600)));
        RequestWorkResult result = new CentralWorkMatcher(boundary(dsl)).match(
                request,
                runtime.context(),
                List.of(TaskCTestFixture.candidate(305).candidate()));

        assertThat(result).isEqualTo(new RequestWorkResult.Rejected(
                request.requestWorkId(),
                RequestWorkFailureReason.WORKER_RUNTIME_NOT_REGISTERED));
        assertThat(count("wf_execution_assignment")).isZero();
    }

    @Test
    void f3StaleRegistrationIsRejectedBeforeRequestWorkAcceptance() {
        var runtime = TaskCTestFixture.runtime("f3-stale");
        RequestWork request = runtime.requestWork();
        var registrations = new JooqWorkerFabricRegistrationBoundary(dsl);
        Instant registeredAt = FIXED_CLOCK.instant().minusSeconds(7200);
        Instant staleAt = FIXED_CLOCK.instant().minusSeconds(3600);
        registrations.registerHost(new WorkerFabricRegistrationBoundary.HostRegistration(
                request.physicalHostId(), request.physicalHostIncarnationId(),
                request.hostResourceSnapshot(), SafetyHeadroom.none(), registeredAt, staleAt));
        registrations.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                request.workerRuntimeId(), request.workerRuntimeIncarnationId(),
                request.physicalHostId(), request.physicalHostIncarnationId(),
                registeredAt, staleAt));

        RequestWorkResult result = new CentralWorkMatcher(boundary(dsl)).match(
                request,
                runtime.context(),
                List.of(TaskCTestFixture.candidate(306).candidate()));

        assertThat(result).isEqualTo(new RequestWorkResult.Rejected(
                request.requestWorkId(), RequestWorkFailureReason.REGISTRATION_STALE));
        assertThat(count("wf_execution_assignment")).isZero();
    }

    @Test
    void f3DurableRegistrationThenRequestWorkIsGranted() {
        var runtime = TaskCTestFixture.runtime("f3-happy");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));

        RequestWorkResult result = new CentralWorkMatcher(boundary(dsl)).match(
                runtime.requestWork(),
                runtime.context(),
                List.of(TaskCTestFixture.candidate(307).candidate()));

        assertThat(result).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(count("wf_host_registration")).isOne();
        assertThat(count("wf_runtime_registration")).isOne();
        assertThat(count("wf_host_resource_snapshot")).isOne();
    }

    private static List<RequestWorkResult> raceDifferentTasks(
            TaskCTestFixture.RuntimeFixture runtime,
            TaskCTestFixture.CandidateFixture firstCandidate,
            TaskCTestFixture.CandidateFixture secondCandidate,
            String firstRequestId,
            String secondRequestId) throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<RequestWorkResult> first = executor.submit(() -> {
                start.await();
                return new CentralWorkMatcher(boundary(dsl)).match(
                        runtime.requestWithId(firstRequestId),
                        runtime.context(),
                        List.of(firstCandidate.candidate()));
            });
            Future<RequestWorkResult> second = executor.submit(() -> {
                start.await();
                return new CentralWorkMatcher(boundary(dsl)).match(
                        runtime.requestWithId(secondRequestId),
                        runtime.context(),
                        List.of(secondCandidate.candidate()));
            });
            start.countDown();
            return List.of(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static JooqAtomicAssignmentGrantBoundary boundary(DSLContext context) {
        return new JooqAtomicAssignmentGrantBoundary(
                context, FIXED_CLOCK, LeaseRenewalContract.NATIVE_PULL_V1);
    }

    private static RequestWorkResult match(
            JooqAtomicAssignmentGrantBoundary boundary,
            TaskCTestFixture.RuntimeFixture runtime,
            TaskCTestFixture.CandidateFixture candidate) {
        // Exact retries resolve before an unresolved request publishes a new snapshot generation.
        var priorResolution = boundary.findResolution(runtime.requestWork());
        if (priorResolution.isPresent()) {
            return priorResolution.orElseThrow();
        }
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        return new CentralWorkMatcher(boundary).match(
                runtime.requestWork(), runtime.context(), List.of(candidate.candidate()));
    }

    private static void register(
            TaskCTestFixture.RuntimeFixture runtime,
            Instant validUntil) {
        RequestWork request = runtime.requestWork();
        var registrations = new JooqWorkerFabricRegistrationBoundary(dsl);
        Instant registeredAt = FIXED_CLOCK.instant().minusSeconds(1);
        registrations.registerHost(new WorkerFabricRegistrationBoundary.HostRegistration(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot(),
                SafetyHeadroom.none(),
                registeredAt,
                validUntil));
        registrations.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                request.workerRuntimeId(),
                request.workerRuntimeIncarnationId(),
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                registeredAt,
                validUntil));
    }

    private static int activeLeaseCount(String taskId) {
        return dsl.fetchOne(
                "select count(*) from wf_task_lease where task_id = ? and active",
                taskId).get(0, Integer.class);
    }

    private static void assertCounts(
            int generations,
            int attempts,
            int assignments,
            int reservations,
            int leases,
            int resolutions,
            int ownerships) {
        assertThat(count("wf_execution_ownership_generation")).isEqualTo(generations);
        assertThat(count("wf_execution_attempt")).isEqualTo(attempts);
        assertThat(count("wf_execution_assignment")).isEqualTo(assignments);
        assertThat(count("wf_reservation")).isEqualTo(reservations);
        assertThat(count("wf_task_lease")).isEqualTo(leases);
        assertThat(count("wf_request_work_resolution")).isEqualTo(resolutions);
        assertThat(count("wf_task_ownership")).isEqualTo(ownerships);
    }

    private static int count(String table) {
        return dsl.fetchOne("select count(*) from " + table).get(0, Integer.class);
    }

    private static void removeFailureInjection() {
        dsl.execute("drop trigger if exists wf_test_fail_lease_insert on wf_task_lease");
        dsl.execute("drop function if exists wf_test_fail_lease_insert()");
    }
}
