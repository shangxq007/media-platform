package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * G2 grant validity: a still-current grant must be backed by a live lease and by the registrations
 * admission required when it was minted. Every invalidation below uses only persisted columns — no
 * new field, table or migration.
 *
 * <p>Runs in a class-owned schema inside the Supervisor-bound PG18 database.
 */
class AtomicAssignmentGrantValidityPostgresTest extends PostgresTestContainerSupport {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    private static com.example.platform.shared.test.TestDatabaseSchema schema;
    private static DataSource dataSource;
    private static DSLContext dsl;

    @BeforeAll
    static void startDatabaseAuthority() throws Exception {
        schema=com.example.platform.shared.test.TestDatabaseSchema.open(PROVIDER);
        try(var input=AtomicAssignmentGrantValidityPostgresTest.class.getResourceAsStream("/db/migration/V1__baseline.sql")) {
            if(input==null)throw new IllegalStateException("canonical V1 missing");
            schema.executeScript(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        var config=new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(PROVIDER.scopedJdbcUrl(schema.name()));
        config.setUsername(PROVIDER.username());config.setPassword(PROVIDER.secret());
        config.setMaximumPoolSize(MANUAL_MAX_POOL_SIZE);config.setMinimumIdle(MANUAL_MIN_IDLE);
        config.setConnectionTimeout(10000);
        dataSource = new com.zaxxer.hikari.HikariDataSource(config);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void closeDatabaseAuthority() throws Exception {
        closeDataSource(dataSource);
        if(schema!=null)schema.close();
    }

    @BeforeEach
    void resetAuthoritativeRows() {
        AssignmentGrantPostgresFixture.truncate(dsl);
    }

    @Test
    void liveLeaseAndValidRegistrationsKeepTheGrantCurrent() {
        Granted granted = grant("g2-valid", 501);

        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).contains(granted.grant());
    }

    @Test
    void expiredLeaseIsNoLongerCurrent() {
        Granted granted = grant("g2-expired-lease", 502);
        expireLease(granted.grant());

        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
    }

    @Test
    void staleHostRegistrationInvalidatesTheGrant() {
        Granted granted = grant("g2-stale-host", 503);
        staleHostRegistration(granted.grant());

        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
    }

    @Test
    void staleRuntimeRegistrationInvalidatesTheGrant() {
        Granted granted = grant("g2-stale-runtime", 504);
        staleRuntimeRegistration(granted.grant());

        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
    }

    @Test
    void disconnectedRuntimeConnectionInvalidatesTheGrant() {
        Granted granted = grant("g2-disconnected", 505);
        dsl.execute(
                "update wf_worker_runtime_connection set connected = false"
                        + " where worker_runtime_id = ?",
                granted.grant().assignment().workerRuntimeId().value());

        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
    }

    @Test
    void runtimeIncarnationMismatchInvalidatesTheGrant() {
        Granted granted = grant("g2-incarnation", 506);
        dsl.execute(
                "update wf_worker_runtime_connection set current_incarnation_id = ?"
                        + " where worker_runtime_id = ?",
                "other-runtime-incarnation",
                granted.grant().assignment().workerRuntimeId().value());

        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
    }

    @Test
    void anInvalidGrantCannotBeReacquiredWhileOwnershipRemainsUnclaimable() {
        // Observed contract today: the expired grant is no longer current, but the task ownership row
        // is still non-claimable, so a re-attempt resolves as NoWork. Re-acquisition needs the
        // existing (currently unwired) expiry/fencing recovery path — reported to the Owner.
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("g2-reacquire");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(507);
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        Granted granted = granted(runtime, candidate);
        expireLease(granted.grant());

        RequestWork replay = runtime.requestWithId("request-g2-reacquire-second");
        RequestWorkResult second = new CentralWorkMatcher(boundary(dsl))
                .match(replay, runtime.context(), List.of(candidate.candidate()));

        assertThat(second).isInstanceOf(RequestWorkResult.NoWork.class);
        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
        assertThat(count("wf_execution_assignment")).isOne();
    }

    @Test
    void concurrentRequestsOnAnExpiredGrantProduceNoDuplicateAuthorization() throws Exception {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("g2-concurrent");
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(508);
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        Granted granted = granted(runtime, candidate);
        expireLease(granted.grant());

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<RequestWorkResult> first = executor.submit(() -> {
                start.await();
                return new CentralWorkMatcher(boundary(dsl)).match(
                        runtime.requestWithId("request-g2-concurrent-a"),
                        runtime.context(),
                        List.of(candidate.candidate()));
            });
            Future<RequestWorkResult> second = executor.submit(() -> {
                start.await();
                return new CentralWorkMatcher(boundary(dsl)).match(
                        runtime.requestWithId("request-g2-concurrent-b"),
                        runtime.context(),
                        List.of(candidate.candidate()));
            });
            start.countDown();

            List<RequestWorkResult> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(RequestWorkResult::granted).isEmpty();
            assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
            assertThat(count("wf_execution_assignment")).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------- helpers ----------

    private record Granted(
            RequestWorkId requestWorkId, AssignmentGrant grant, ExecutableTaskId taskId) {}

    private Granted grant(String suffix, long identity) {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime(suffix);
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(identity);
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        return granted(runtime, candidate);
    }

    private Granted granted(
            TaskCTestFixture.RuntimeFixture runtime, TaskCTestFixture.CandidateFixture candidate) {
        RequestWorkResult result = new CentralWorkMatcher(boundary(dsl))
                .match(runtime.requestWork(), runtime.context(), List.of(candidate.candidate()));
        assertThat(result).isInstanceOf(RequestWorkResult.Granted.class);
        AssignmentGrant grant = (AssignmentGrant) ((RequestWorkResult.Granted) result).grant();
        return new Granted(runtime.requestWork().requestWorkId(), grant, candidate.task().id());
    }

    private static void expireLease(AssignmentGrant grant) {
        dsl.execute(
                """
                update wf_task_lease
                   set last_heartbeat_at = cast(? as timestamptz) - interval '2 minutes',
                       expires_at = cast(? as timestamptz) - interval '1 minute'
                 where assignment_id = ?
                """,
                databaseTime(FIXED_CLOCK.instant()),
                databaseTime(FIXED_CLOCK.instant()),
                grant.assignment().id().value());
    }

    private static void staleHostRegistration(AssignmentGrant grant) {
        dsl.execute(
                """
                update wf_host_registration
                   set valid_until = registered_at + interval '1 millisecond'
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                """,
                grant.assignment().physicalHostId().value(),
                grant.assignment().physicalHostIncarnationId().value());
    }

    private static void staleRuntimeRegistration(AssignmentGrant grant) {
        dsl.execute(
                """
                update wf_runtime_registration
                   set valid_until = registered_at + interval '1 millisecond'
                 where worker_runtime_id = ? and worker_runtime_incarnation_id = ?
                """,
                grant.assignment().workerRuntimeId().value(),
                grant.assignment().workerRuntimeIncarnationId().value());
    }

    private static void register(TaskCTestFixture.RuntimeFixture runtime, Instant validUntil) {
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

    private static JooqAtomicAssignmentGrantBoundary boundary(DSLContext context) {
        return new JooqAtomicAssignmentGrantBoundary(
                context, FIXED_CLOCK, LeaseRenewalContract.NATIVE_PULL_V1);
    }

    private static int count(String table) {
        return dsl.fetchOne("select count(*) from " + table).get(0, Integer.class);
    }

    private static java.time.OffsetDateTime databaseTime(Instant instant) {
        return java.time.OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
