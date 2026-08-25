package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.infrastructure.JooqArtifactReuseIndex;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.reuse.ReusableArtifactPublication;
import com.example.platform.workerfabric.reuse.ReusableArtifactRecord;
import com.example.platform.workerfabric.reuse.ReusePublicationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PostgreSQL acceptance for persistent tenant-scoped pending/winning reuse publication. */
class ArtifactReuseIndexPostgresTest extends PostgresTestContainerSupport {

    private static final Instant NOW = TaskCTestFixture.NOW;
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TENANT = "tenant-reuse";
    private static final ArtifactId ARTIFACT_ID = new ArtifactId("artifact-reuse-pg");
    private static final ContentDigest ARTIFACT_DIGEST = digest("artifact-bytes");
    private static final ExecutionReuseKey REUSE_KEY = key("postgres-index");

    private static DataSource dataSource;
    private static DSLContext dsl;

    private JooqArtifactReuseIndex index;

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
        dsl.execute("truncate table wf_artifact_reuse_index, artifact cascade");
        dsl.execute(
                """
                insert into artifact (
                    id, tenant_id, content_digest, byte_length, media_type,
                    artifact_kind, state, schema_version, created_at)
                values (?, ?, ?, 14, 'VIDEO', 'RENDER_MASTER', 'AVAILABLE', 1,
                        cast(? as timestamp))
                """,
                ARTIFACT_ID.value(), TENANT, ARTIFACT_DIGEST.canonicalValue(),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        index = new JooqArtifactReuseIndex(dsl);
    }

    @Test
    void pendingIsInvisibleThenMatchingCompletionActivatesTenantScopedWinner() {
        AssignmentGrant grant = grant(801);
        ReusableArtifactPublication publication = publication(grant);

        assertThat(index.stageWinningPublication(publication))
                .isEqualTo(ReusePublicationResult.STAGED_PENDING);
        assertThat(index.stageWinningPublication(publication))
                .isEqualTo(ReusePublicationResult.PENDING_IDEMPOTENT);
        ReusableArtifactPublication conflictingPublication = publication(grant(804));
        assertThat(index.stageWinningPublication(conflictingPublication))
                .isEqualTo(ReusePublicationResult.CONFLICT_REJECTED);
        assertThat(index.lookup(TENANT, REUSE_KEY)).isEmpty();
        assertThat(index.lookup("other-tenant", REUSE_KEY)).isEmpty();

        CompletionEvidence completion = recordAuthoritativeCompletion(grant);
        assertThat(index.activateWinningPublication(publication, completion))
                .isEqualTo(ReusePublicationResult.ACTIVATED_WINNER);
        assertThat(index.activateWinningPublication(publication, completion))
                .isEqualTo(ReusePublicationResult.WINNER_IDEMPOTENT);
        assertThat(index.stageWinningPublication(conflictingPublication))
                .isEqualTo(ReusePublicationResult.CONFLICT_REJECTED);
        assertThat(index.lookup(TENANT, REUSE_KEY)).contains(publication.record());
        assertThat(index.lookup("other-tenant", REUSE_KEY)).isEmpty();
        assertThat(index.evict(TENANT, REUSE_KEY)).isTrue();
        assertThat(index.lookup(TENANT, REUSE_KEY)).isEmpty();
        assertThat(dsl.fetchExists(
                DSL.selectOne().from("artifact").where(DSL.field("id").eq(ARTIFACT_ID.value()))))
                .isTrue();
    }

    @Test
    void staleGenerationCannotStageAndStalePendingCleanupPreservesArtifact() {
        AssignmentGrant grant = grant(802);
        ReusableArtifactPublication publication = publication(grant);
        ReusableArtifactRecord staleRecord = new ReusableArtifactRecord(
                TENANT,
                key("stale-generation"),
                publication.record().artifactPin(),
                grant.executableTaskId(),
                grant.attempt().id(),
                grant.attempt().ownershipGeneration().next(),
                NOW);

        assertThat(index.stageWinningPublication(new ReusableArtifactPublication(staleRecord)))
                .isEqualTo(ReusePublicationResult.STALE_OWNER_REJECTED);
        assertThat(index.stageWinningPublication(publication))
                .isEqualTo(ReusePublicationResult.STAGED_PENDING);
        assertThat(index.purgePendingBefore(NOW.plusSeconds(1))).isOne();
        assertThat(index.lookup(TENANT, REUSE_KEY)).isEmpty();
        assertThat(dsl.fetchExists(
                DSL.selectOne().from("artifact").where(DSL.field("id").eq(ARTIFACT_ID.value()))))
                .isTrue();
    }

    @Test
    void concurrentFirstPublicationIsOneRowAndTwoTypedNormalResults() throws Exception {
        AssignmentGrant grant = grant(803);
        ReusableArtifactPublication publication = publication(grant);
        JooqArtifactReuseIndex firstProcess = new JooqArtifactReuseIndex(
                DSL.using(dataSource, SQLDialect.POSTGRES));
        JooqArtifactReuseIndex secondProcess = new JooqArtifactReuseIndex(
                DSL.using(dataSource, SQLDialect.POSTGRES));
        CyclicBarrier start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<StageCall> first = executor.submit(
                    () -> stageTogether(firstProcess, publication, start));
            Future<StageCall> second = executor.submit(
                    () -> stageTogether(secondProcess, publication, start));

            List<StageCall> calls = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));

            assertThat(calls)
                    .as("raw unique violations and all other exceptions")
                    .filteredOn(call -> call.failure() != null)
                    .isEmpty();
            assertThat(calls).extracting(StageCall::result)
                    .containsExactlyInAnyOrder(
                            ReusePublicationResult.STAGED_PENDING,
                            ReusePublicationResult.PENDING_IDEMPOTENT);
            assertThat(dsl.fetchOne(
                    """
                    select count(*) from wf_artifact_reuse_index
                     where tenant_id = ? and reuse_key_version = ? and reuse_key_digest = ?
                    """,
                    TENANT, REUSE_KEY.version(), REUSE_KEY.stableDigest()).get(0, Long.class))
                    .isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    private static StageCall stageTogether(
            JooqArtifactReuseIndex process,
            ReusableArtifactPublication publication,
            CyclicBarrier start) {
        try {
            start.await(10, TimeUnit.SECONDS);
            return new StageCall(process.stageWinningPublication(publication), null);
        } catch (Throwable failure) {
            return new StageCall(null, failure);
        }
    }

    private static AssignmentGrant grant(long identity) {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("reuse-" + identity);
        TaskCTestFixture.CandidateFixture candidate = TaskCTestFixture.candidate(identity);
        RequestWork request = runtime.requestWork();
        JooqWorkerFabricRegistrationBoundary registrations =
                new JooqWorkerFabricRegistrationBoundary(dsl);
        registrations.registerHost(new WorkerFabricRegistrationBoundary.HostRegistration(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot(),
                SafetyHeadroom.none(),
                NOW.minusSeconds(1),
                NOW.plusSeconds(3600)));
        registrations.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                request.workerRuntimeId(),
                request.workerRuntimeIncarnationId(),
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                NOW.minusSeconds(1),
                NOW.plusSeconds(3600)));
        JooqAtomicAssignmentGrantBoundary grants = new JooqAtomicAssignmentGrantBoundary(
                dsl, FIXED_CLOCK, LeaseRenewalContract.NATIVE_PULL_V1);
        RequestWorkResult result = new CentralWorkMatcher(grants).match(
                request, runtime.context(), List.of(candidate.candidate()));
        return (AssignmentGrant) ((RequestWorkResult.Granted) result).grant();
    }

    private static ReusableArtifactPublication publication(AssignmentGrant grant) {
        return new ReusableArtifactPublication(new ReusableArtifactRecord(
                TENANT,
                REUSE_KEY,
                new ArtifactPin(ARTIFACT_ID, ARTIFACT_DIGEST),
                grant.executableTaskId(),
                grant.attempt().id(),
                grant.attempt().ownershipGeneration(),
                NOW));
    }

    private static CompletionEvidence recordAuthoritativeCompletion(AssignmentGrant grant) {
        CompletionEventId eventId = new CompletionEventId(
                "completion-" + grant.attempt().id().value());
        dsl.execute(
                """
                insert into wf_completion_event (
                    completion_event_id, task_id, attempt_id, generation,
                    artifact_commit_reference, artifact_committed_at, completed_at)
                values (?, ?, ?, ?, 'artifact-authority-evidence',
                        cast(? as timestamptz), cast(? as timestamptz))
                """,
                eventId.value(),
                grant.executableTaskId().sha256Hex(),
                grant.attempt().id().value(),
                grant.attempt().ownershipGeneration().value(),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        dsl.execute(
                "update wf_execution_attempt set state = 'SUCCEEDED' where attempt_id = ?",
                grant.attempt().id().value());
        return new CompletionEvidence(
                eventId,
                NativeWorkerBackendExecutionHandle.forLease(
                        grant.attempt().id(),
                        grant.attempt().ownershipGeneration(),
                        grant.lease().id()),
                grant.executableTaskId(),
                ObservedExecutionState.SUCCEEDED,
                new ExpectedOutputValidation(
                        "reuse-index-postgres", ExpectedOutputValidation.Status.VALID));
    }

    private static ExecutionReuseKey key(String semantic) {
        String canonical = "roadmap22.execution-reuse-key.v1" + semantic;
        return new ExecutionReuseKey(
                ExecutionReuseKey.VERSION, canonical, sha256(canonical));
    }

    private static ContentDigest digest(String value) {
        return ContentDigest.sha256(sha256(value));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private record StageCall(ReusePublicationResult result, Throwable failure) {}
}
