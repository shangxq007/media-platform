package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        dsl.execute("create table native_publication_probe(id int primary key)");
    }

    @AfterAll
    static void closeDatabaseAuthority() throws Exception {
        closeDataSource(dataSource);
        if(schema!=null)schema.close();
    }

    @BeforeEach
    void resetAuthoritativeRows() {
        AssignmentGrantPostgresFixture.truncate(dsl);
        dsl.execute("truncate native_publication_probe");
        dsl.execute("delete from artifact where id='native-probe-output' and tenant_id='tenant-native'");
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

    @Test
    void sameIncarnationRenewalKeepsItsGrantAndOneRegistration() {
        var runtime = TaskCTestFixture.runtime("inc-renew");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        var granted = granted(runtime, TaskCTestFixture.candidate(601));
        runtimeRegistration(runtime, runtime.requestWork().workerRuntimeIncarnationId().value(),
                FIXED_CLOCK.instant());
        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).contains(granted.grant());
        assertThat(count("wf_runtime_registration")).isOne();
    }

    @Test
    void replacementFencesOldGrantAndPermanentlyRejectsLateRegistration() {
        var runtime = TaskCTestFixture.runtime("inc-replaced");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        var granted = granted(runtime, TaskCTestFixture.candidate(602));
        runtimeRegistration(runtime, "replacement", FIXED_CLOCK.instant());
        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
        assertThat(dsl.fetchOne("select active from wf_task_lease where lease_id = ?",
                granted.grant().lease().id().value()).get(0, Boolean.class)).isFalse();
        assertThat(count("wf_runtime_registration")).isEqualTo(2);
        assertThatThrownBy(() -> runtimeRegistration(runtime,
                runtime.requestWork().workerRuntimeIncarnationId().value(), FIXED_CLOCK.instant().plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("superseded");
        assertThat(currentIncarnation(runtime)).isEqualTo("replacement");
        assertThat(count("wf_runtime_registration")).isEqualTo(2);
    }

    @Test
    void concurrentReplacementAndOldTickCannotRestoreRetiredOwnership() throws Exception {
        var runtime = TaskCTestFixture.runtime("inc-race");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        var granted = granted(runtime, TaskCTestFixture.candidate(603));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var old = pool.submit(() -> {
                start.await();
                try {
                    runtimeRegistration(runtime, runtime.requestWork().workerRuntimeIncarnationId().value(),
                            FIXED_CLOCK.instant());
                } catch (IllegalStateException retired) {
                    assertThat(retired).hasMessageContaining("superseded");
                }
                return true;
            });
            var replacement = pool.submit(() -> {
                start.await();
                runtimeRegistration(runtime, "race-replacement", FIXED_CLOCK.instant());
                return true;
            });
            start.countDown();
            assertThat(old.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(replacement.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        for (int i = 1; i <= 3; i++) {
            Instant time = FIXED_CLOCK.instant().plusSeconds(i);
            assertThatThrownBy(() -> runtimeRegistration(runtime,
                    runtime.requestWork().workerRuntimeIncarnationId().value(), time))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("superseded");
        }
        assertThat(currentIncarnation(runtime)).isEqualTo("race-replacement");
        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
        assertThat(count("wf_execution_assignment")).isOne();
    }

    @Test
    void outOfOrderCurrentTickCannotRollBackRegistrationValidity() {
        var runtime = TaskCTestFixture.runtime("inc-late");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        String incarnation = runtime.requestWork().workerRuntimeIncarnationId().value();
        runtimeRegistration(runtime, incarnation, FIXED_CLOCK.instant().plusSeconds(2));
        assertThatThrownBy(() -> runtimeRegistration(runtime, incarnation, FIXED_CLOCK.instant()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("older");
        assertThat(dsl.fetchOne("select registered_at from wf_runtime_registration where active")
                .get(0, java.time.OffsetDateTime.class).toInstant())
                .isEqualTo(FIXED_CLOCK.instant().plusSeconds(2));
    }

    @Test
    void renewalAfterExpiryCannotRevivePreExpiryGrant() {
        var runtime = TaskCTestFixture.runtime("inc-expiry");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        var granted = granted(runtime, TaskCTestFixture.candidate(604));
        staleRuntimeRegistration(granted.grant());
        runtimeRegistration(runtime, runtime.requestWork().workerRuntimeIncarnationId().value(),
                FIXED_CLOCK.instant());
        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
        assertThat(dsl.fetchOne("select active from wf_task_lease").get(0, Boolean.class)).isFalse();
        assertThat(count("wf_runtime_registration")).isOne();
    }

    @Test
    void disconnectAndReplacementKeepRetiredInstanceAndItsMessagesFenced() {
        var runtime = TaskCTestFixture.runtime("inc-disconnect");
        register(runtime, FIXED_CLOCK.instant().plusSeconds(3600));
        var granted = granted(runtime, TaskCTestFixture.candidate(605));
        var lifecycle = new com.example.platform.workerfabric.infrastructure.JooqExecutionLifecycleBoundary(dsl);
        var disconnect = new ExecutionLifecycleBoundary.WorkerDisconnect(
                runtime.requestWork().workerRuntimeId(), runtime.requestWork().workerRuntimeIncarnationId(),
                FIXED_CLOCK.instant());
        lifecycle.disconnectWorker(disconnect);
        runtimeRegistration(runtime, "after-disconnect", FIXED_CLOCK.instant().plusSeconds(1));
        assertThat(lifecycle.disconnectWorker(disconnect).status())
                .isEqualTo(ExecutionLifecycleBoundary.DisconnectStatus.STALE_INCARNATION_NOOP);
        assertThatThrownBy(() -> runtimeRegistration(runtime,
                runtime.requestWork().workerRuntimeIncarnationId().value(), FIXED_CLOCK.instant().plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("superseded");
        assertThat(currentIncarnation(runtime)).isEqualTo("after-disconnect");
        assertThat(boundary(dsl).findCurrentGrant(granted.taskId())).isEmpty();
        assertThat(count("wf_runtime_registration")).isEqualTo(2);
    }

    private static String currentIncarnation(TaskCTestFixture.RuntimeFixture runtime) {
        return dsl.fetchOne("select current_incarnation_id from wf_worker_runtime_connection where worker_runtime_id = ?",
                runtime.requestWork().workerRuntimeId().value()).get(0, String.class);
    }

    private static void runtimeRegistration(TaskCTestFixture.RuntimeFixture runtime, String incarnation, Instant time) {
        var request = runtime.requestWork();
        new JooqWorkerFabricRegistrationBoundary(dsl).registerRuntime(
                new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                        request.workerRuntimeId(), WorkerRuntimeIncarnationId.of(incarnation),
                        request.physicalHostId(), request.physicalHostIncarnationId(),
                        time, FIXED_CLOCK.instant().plusSeconds(3599)));
    }

    // ---------- helpers ----------

    private com.example.platform.workerfabric.reuse.TaskRuntimeExecution publication(TaskCTestFixture.CandidateFixture candidate,AssignmentGrant grant) {
        // Transaction protocol fixture only. Actual planning, bytes and Artifact commit are
        // separately exercised by the real HTTP media regression.
        var target=new com.example.platform.workerfabric.reuse.DurableOutputTarget(
                new com.example.platform.storage.contract.StorageProviderId("storage-native"),
                new com.example.platform.storage.contract.namespace.StorageNamespace("tenant-native","project-native",
                        com.example.platform.storage.contract.namespace.NamespaceClass.DERIVED,
                        com.example.platform.storage.contract.namespace.RegionPolicy.SINGLE_REGION,
                        com.example.platform.storage.contract.namespace.DataClassification.INTERNAL),"native-probe-write");
        var metadata=new com.example.platform.workerfabric.reuse.ArtifactCommitMetadata(
                new com.example.platform.shared.identity.ArtifactId("native-probe-output"),"tenant-native",
                com.example.platform.artifact.domain.ArtifactMediaType.IMAGE,com.example.platform.artifact.domain.ArtifactKind.DERIVED_MEDIA,
                1,com.example.platform.artifact.domain.ReplicaRole.PRIMARY,"local",List.of(),FIXED_CLOCK.instant(),FIXED_CLOCK.instant(),null,"project-native");
        return com.example.platform.workerfabric.reuse.TaskRuntimeExecutionFactory.build(candidate.task(),grant,target,metadata);
    }

    @Test void nativePublicationTransactionClosesCurrentAttemptAndReleasesOnlyItsReservation() {
        var runtime=TaskCTestFixture.runtime("native-publish");register(runtime,FIXED_CLOCK.instant().plusSeconds(3600));
        var candidate=TaskCTestFixture.candidate(701);var granted=granted(runtime,candidate);var execution=publication(candidate,granted.grant());
        var expected=org.mockito.Mockito.mock(com.example.platform.workerfabric.reuse.RuntimeClosedLoopTaskResult.class);
        var result=dsl.transactionResult(configuration->{var tx=DSL.using(configuration);
            var lifecycle=new com.example.platform.workerfabric.infrastructure.JooqExecutionLifecycleBoundary(tx);
            var publication=new com.example.platform.workerfabric.infrastructure.NativeOutputPublicationTransaction(tx,boundary(tx),lifecycle);
            try{return publication.commit(candidate.task().id(),execution,()->{
                tx.execute("insert into artifact(id,tenant_id,project_id,content_digest,byte_length,media_type,artifact_kind,state,schema_version,created_at) values('native-probe-output','tenant-native','project-native',?,1,'IMAGE','DERIVED_MEDIA','AVAILABLE',1,current_timestamp)","a".repeat(64));
                tx.execute("insert into native_publication_probe values(1)");
                var decision=new com.example.platform.workerfabric.infrastructure.JooqExecutionAuthorityBoundary(tx,FIXED_CLOCK)
                        .completeIfCurrent(execution.completionEvidence(),new ArtifactCommitEvidence(execution.artifactCommitMetadata().artifactId()));
                assertThat(decision).isEqualTo(CompletionDecision.COMPLETED);return expected;
            });}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        assertThat(result).isSameAs(expected);assertThat(count("native_publication_probe")).isOne();assertThat(count("wf_completion_event")).isOne();
        assertThat(boundary(dsl).findCurrentGrant(candidate.task().id())).isEmpty();
        assertThat(dsl.fetch("select state from wf_reservation").getValues("state",String.class)).containsOnly("RELEASED");
    }
    @Test void nativePublicationFailureRollsBackCallbackAndCompletionAndKeepsOriginalOwnership() {
        var runtime=TaskCTestFixture.runtime("native-rollback");register(runtime,FIXED_CLOCK.instant().plusSeconds(3600));
        var candidate=TaskCTestFixture.candidate(702);var granted=granted(runtime,candidate);var execution=publication(candidate,granted.grant());
        assertThatThrownBy(()->dsl.transactionResult(configuration->{var tx=DSL.using(configuration);
            var publication=new com.example.platform.workerfabric.infrastructure.NativeOutputPublicationTransaction(tx,boundary(tx),new com.example.platform.workerfabric.infrastructure.JooqExecutionLifecycleBoundary(tx));
            try{return publication.commit(candidate.task().id(),execution,()->{
                tx.execute("insert into artifact(id,tenant_id,project_id,content_digest,byte_length,media_type,artifact_kind,state,schema_version,created_at) values('native-probe-output','tenant-native','project-native',?,1,'IMAGE','DERIVED_MEDIA','AVAILABLE',1,current_timestamp)","a".repeat(64));
                tx.execute("insert into native_publication_probe values(1)");
                assertThat(new com.example.platform.workerfabric.infrastructure.JooqExecutionAuthorityBoundary(tx,FIXED_CLOCK)
                        .completeIfCurrent(execution.completionEvidence(),new ArtifactCommitEvidence(execution.artifactCommitMetadata().artifactId())))
                        .isEqualTo(CompletionDecision.COMPLETED);
                throw new java.io.IOException("synthetic callback failure");
            });}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        })).isInstanceOf(java.io.UncheckedIOException.class).hasMessageContaining("synthetic callback failure");
        assertThat(count("artifact")).isZero();
        assertThat(count("native_publication_probe")).isZero();assertThat(count("wf_completion_event")).isZero();
        assertThat(boundary(dsl).findCurrentGrant(candidate.task().id())).contains(granted.grant());
        assertThat(dsl.fetch("select state from wf_reservation").getValues("state",String.class)).containsOnly("ACTIVE");
    }
    @Test void retiredRuntimeNeverReachesNativePublicationCallback() {
        var runtime=TaskCTestFixture.runtime("native-retired");register(runtime,FIXED_CLOCK.instant().plusSeconds(3600));
        var candidate=TaskCTestFixture.candidate(703);var granted=granted(runtime,candidate);var execution=publication(candidate,granted.grant());
        runtimeRegistration(runtime,"native-replacement",FIXED_CLOCK.instant());var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->dsl.transactionResult(configuration->{var tx=DSL.using(configuration);
            var publication=new com.example.platform.workerfabric.infrastructure.NativeOutputPublicationTransaction(tx,boundary(tx),new com.example.platform.workerfabric.infrastructure.JooqExecutionLifecycleBoundary(tx));
            try{return publication.commit(candidate.task().id(),execution,()->{calls.incrementAndGet();throw new java.io.IOException("must not enter");});}
            catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer current");
        assertThat(calls.get()).isZero();assertThat(count("native_publication_probe")).isZero();assertThat(count("wf_completion_event")).isZero();
    }

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
