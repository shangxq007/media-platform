package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.api.StorageOutputPort;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DB-backed idempotency tests for {@link CoverImageCommitService} (COVER-PROVIDER-001 increment 4).
 *
 * <p>The canonical {@link ArtifactCommitService} is fail-closed on an already-committed identity
 * (ARTIFACT-409-001) and does not perform an idempotent re-commit. A cover retry that reaches this
 * fence after a successful commit must therefore replay instead of committing a second Artifact.
 * Replay is resolved through the canonical {@code findByIdempotencyKey} hook and, when that hook
 * does not resolve (the canonical jOOQ adapter delegates idempotency-key replay to the caller's
 * durable record), through this task's own durable row.
 *
 * <p>The canonical collaborators are recording doubles used only to prove the commit fence is NOT
 * reached again on replay; the durable task row itself is real PostgreSQL.
 */
class CoverImageCommitServiceIdempotencyTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-cover";
    private static final String PROJECT = "project-cover";
    private static final String SUBJECT = "art-subject";
    private static final String TASK = "cimg_replaytask";
    private static final String COVER_ARTIFACT = "art-cover-existing";
    private static final String CONTENT_TYPE = "image/png";
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private CoverImageTaskStore tasks;
    private ArtifactCommitService commits;
    private StorageOutputPort outputs;
    private CoverImageCommitService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table if not exists cover_image_task (
                    id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    project_id varchar(128) not null,
                    subject_artifact_id varchar(128) not null,
                    timestamp_seconds double precision not null,
                    image_format varchar(16) not null,
                    width integer,
                    quality integer,
                    idempotency_key varchar(256) not null,
                    provider_id varchar(128) not null,
                    provider_version varchar(32) not null,
                    status varchar(32) not null,
                    artifact_id varchar(128),
                    failure_code varchar(64),
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp,
                    constraint uq_cover_image_task_idempotency
                        unique (tenant_id, project_id, idempotency_key))
                """);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void cleanTaskTable() {
        jdbc.execute("TRUNCATE TABLE cover_image_task");
        tasks = new CoverImageTaskStore(jdbc);
        commits = mock(ArtifactCommitService.class);
        outputs = mock(StorageOutputPort.class);
        service = new CoverImageCommitService(tasks, outputs, commits);
    }

    private void insertTask(String status, String artifactId) {
        jdbc.update("""
                insert into cover_image_task(id,tenant_id,project_id,subject_artifact_id,timestamp_seconds,
                    image_format,width,quality,idempotency_key,provider_id,provider_version,status,artifact_id)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                TASK, TENANT, PROJECT, SUBJECT, 1.5d, "png", 640, 80, "cover-idem-key",
                CoverImageContracts.PROVIDER, CoverImageContracts.PROVIDER_VERSION, status, artifactId);
    }

    private String replay() {
        return service.commit(TENANT, PROJECT, TASK, SUBJECT, CONTENT_TYPE, new byte[] {1, 2, 3},
                Path.of("/tmp/does-not-exist/cover.png"), "/tmp/does-not-exist");
    }

    private static ArtifactCommitResult committedCover() {
        ContentDigest digest = ContentDigest.sha256("b".repeat(64));
        Artifact artifact = new Artifact(
                new ArtifactId(COVER_ARTIFACT), TENANT, digest, 3L,
                ArtifactMediaType.IMAGE, ArtifactKind.DERIVED_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
        ArtifactReplicaBinding binding = new ArtifactReplicaBinding(
                COVER_ARTIFACT + ":rep-cover", new ArtifactId(COVER_ARTIFACT),
                new StorageObjectId("obj-cover"), new StorageReplicaId("rep-cover"),
                new StorageProviderId("local"), ReplicaRole.PRIMARY, "local", NOW);
        return new ArtifactCommitResult(artifact, binding, List.of(), "cover-image:" + TASK);
    }

    @Test
    void idempotencyKeyResolvesToTheSameArtifactAndReCommitFailsClosed() {
        insertTask(CoverImageContracts.Status.COMMITTING.name(), null);
        when(commits.findByIdempotencyKey(TENANT, CoverImageCommitService.idempotencyKey(TASK)))
                .thenReturn(Optional.of(committedCover()));

        assertThat(replay()).isEqualTo(COVER_ARTIFACT);

        // The already-committed identity is never re-committed and no storage write is attempted.
        verify(commits, never()).commit(any());
        verifyNoInteractions(outputs);
        // The durable row converges to COMPLETED with the resolved Artifact identity.
        assertThat(tasks.find(TENANT, PROJECT, TASK).orElseThrow().status())
                .isEqualTo(CoverImageContracts.Status.COMPLETED);
        assertThat(tasks.find(TENANT, PROJECT, TASK).orElseThrow().artifactId())
                .isEqualTo(COVER_ARTIFACT);
    }

    @Test
    void completedTaskReplaysFromTheDurableRecordWithoutReachingTheCommitFence() {
        insertTask(CoverImageContracts.Status.COMPLETED.name(), COVER_ARTIFACT);
        when(commits.findByIdempotencyKey(TENANT, CoverImageCommitService.idempotencyKey(TASK)))
                .thenReturn(Optional.empty());

        assertThat(replay()).isEqualTo(COVER_ARTIFACT);

        verify(commits, never()).commit(any());
        verifyNoInteractions(outputs);
    }

    @Test
    void taskWithoutACommittedArtifactDoesNotReplay() {
        insertTask(CoverImageContracts.Status.ADMITTED.name(), null);
        when(commits.findByIdempotencyKey(TENANT, CoverImageCommitService.idempotencyKey(TASK)))
                .thenReturn(Optional.empty());

        assertThat(replay()).isNull();

        // No replay shortcut fired: an unadmitted commit fence stays closed and no Artifact is
        // fabricated for a task that has not committed.
        verify(commits, never()).commit(any());
        verifyNoInteractions(outputs);
        assertThat(tasks.find(TENANT, PROJECT, TASK).orElseThrow().status())
                .isEqualTo(CoverImageContracts.Status.ADMITTED);
    }
}
