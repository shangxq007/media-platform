package com.example.platform.coverimage;

import com.example.platform.contract.media.CoverImageContracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StoragePlacementQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Local end-to-end acceptance for the cover capability (COVER-PROVIDER-001 Continue-5).
 *
 * <p>Boots the real worker role against an external Temporal cluster and PostgreSQL, then drives the
 * full canonical path: admission (API-side {@link CoverImageService}) → durable task row → Temporal
 * workflow → activity → digest-verified subject materialization → bubblewrap/ffmpeg sandbox →
 * {@code ArtifactCommitService} → Artifact read-back → {@code COVER_OF} relation, plus an idempotent
 * re-run.
 *
 * <p>Gated on {@code COVER_E2E=true} because it requires the acceptance services; it is skipped (not
 * silently passing) in the ordinary suite. Environment overrides:
 * {@code COVER_E2E_JDBC_URL}, {@code COVER_E2E_DB_USER}, {@code COVER_E2E_DB_PASSWORD},
 * {@code TEMPORAL_TARGET}, {@code TEMPORAL_NAMESPACE}.
 */
@EnabledIfEnvironmentVariable(named = "COVER_E2E", matches = "true")
@SpringBootTest(
        classes = com.example.platform.runtime.PlatformFfmpegWorkerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.temporal.start-workers=true",
            "app.temporal.worker-required=true",
            // The acceptance database is migrated explicitly (canonical platform-app migration
            // directory) before the context refreshes, exactly like the repository's other
            // PostgreSQL fixtures; the application must not migrate a shared acceptance database.
            "spring.flyway.enabled=false"
        })
@ActiveProfiles({"temporal", "ffmpeg-worker"})
@ContextConfiguration(initializers = CoverImageEndToEndAcceptanceTest.AcceptanceDatabaseInitializer.class)
class CoverImageEndToEndAcceptanceTest {

    private static final String TENANT = "tenant-cover-e2e";
    private static final String PROJECT = "project-cover-e2e";
    private static final Duration DEADLINE = Duration.ofMinutes(3);

    /**
     * Acceptance runtime roots. Deliberately NOT under the host {@code /tmp}: the sandbox profile
     * mounts a private tmpfs on {@code /tmp}, so any host path below it would be hidden from the
     * sandboxed provider command. Production defaults ({@code ./.data/...}) are outside it too.
     */
    private static final Path WORK =
            Path.of(System.getProperty("user.dir"), "build", "cover-e2e-runtime");

    /** Applies the canonical platform-app migration directory to the acceptance database first. */
    static class AcceptanceDatabaseInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            Flyway.configure()
                    .dataSource(
                            env("COVER_E2E_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/cover_e2e"),
                            env("COVER_E2E_DB_USER", "media_platform"),
                            env("COVER_E2E_DB_PASSWORD", ""))
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
        }
    }

    @DynamicPropertySource
    static void acceptanceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("COVER_E2E_JDBC_URL",
                "jdbc:postgresql://127.0.0.1:5432/cover_e2e"));
        registry.add("spring.datasource.username", () -> env("COVER_E2E_DB_USER", "media_platform"));
        registry.add("spring.datasource.password", () -> env("COVER_E2E_DB_PASSWORD", ""));
        registry.add("spring.temporal.connection.target", () -> env("TEMPORAL_TARGET", "127.0.0.1:7233"));
        registry.add("spring.temporal.namespace", () -> env("TEMPORAL_NAMESPACE", "media-platform-dev"));
        registry.add("app.storage.local-root", () -> WORK.resolve("blob-storage").toString());
        registry.add("app.cover-image.storage.root", () -> WORK.resolve("object-store").toString());
        registry.add("app.cover-image.work-root", () -> WORK.resolve("cover-work").toString());
        registry.add("app.cover-image.materialization-root",
                () -> WORK.resolve("materialized").toString());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Autowired ArtifactCommitService commits;
    @Autowired ArtifactQueryService artifacts;
    @Autowired CoverImageService coverService;
    @Autowired CoverImageTaskStore tasks;
    @Autowired CoverImageCommitService coverCommits;
    @Autowired StorageProvider storageProvider;
    @Autowired StoragePlacementQuery placements;
    @Autowired JdbcTemplate jdbc;

    @Test
    void coverRequestRunsTheFullCanonicalPathAndReplaysIdempotently() throws Exception {
        String runId = Long.toString(System.nanoTime());
        // Real platform identity shape (art-<uuid>, 40 chars): the platform fix bounds the canonical
        // edge identity, so no short-id workaround is needed.
        String subjectArtifactId = "art-" + java.util.UUID.randomUUID();
        String idempotencyKey = "e2e-cover-" + runId;

        // 1. seed one canonical subject Artifact whose bytes live in the worker-visible object store
        byte[] video = Files.readAllBytes(Path.of(
                getClass().getResource("/render-output-fixture.mp4").toURI()));
        ContentDigest subjectDigest = ContentDigest.sha256(sha256Hex(video));
        var writeSession = storageProvider.beginWrite("seed-" + runId, namespace(),
                subjectDigest, video.length);
        storageProvider.write(writeSession, video, 0, video.length);
        var written = storageProvider.completeWrite(writeSession, subjectDigest);
        commits.commit(new ArtifactCommitRequest(
                new ArtifactId(subjectArtifactId), TENANT, subjectDigest, video.length,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, Artifact.CURRENT_SCHEMA_VERSION,
                written.objectId(), written.replicaId(), storageProvider.providerId(),
                ReplicaRole.PRIMARY, "local", "subject-" + runId, List.of(),
                Instant.now(), Instant.now(), null, PROJECT));

        // 2. admit + start the workflow through the canonical API-side service
        var submitted = coverService.submit(new CoverImageContracts.Request(
                TENANT, PROJECT, subjectArtifactId, 0d, "png", 320, null, idempotencyKey));
        assertThat(submitted.taskId()).isNotBlank();
        // Admission status is start-race dependent (the worker may already have picked the task up).
        assertThat(submitted.status()).isNotIn(
                CoverImageContracts.Status.FAILED, CoverImageContracts.Status.CANCELLED);
        assertThat(jdbc.queryForObject(
                "select count(*) from cover_image_task where tenant_id=? and id=?",
                Integer.class, TENANT, submitted.taskId())).isOne();

        // 3. the durable task row reaches a terminal COMPLETED state
        CoverImageTaskStore.Task task = awaitCompleted(submitted.taskId());
        assertThat(task.providerId()).isEqualTo(CoverImageContracts.PROVIDER);
        assertThat(task.artifactId()).isNotBlank();

        // 4. the committed cover Artifact reads back through the canonical query service
        Artifact cover = artifacts.getArtifact(TENANT, new ArtifactId(task.artifactId())).orElseThrow();
        assertThat(cover.state()).isEqualTo(ArtifactState.AVAILABLE);
        assertThat(cover.mediaType()).isEqualTo(ArtifactMediaType.IMAGE);
        assertThat(cover.artifactKind()).isEqualTo(ArtifactKind.DERIVED_MEDIA);
        assertThat(cover.byteLength()).isGreaterThan(0L);
        // D4 (platform fix): the canonical insert satisfied V18's NOT NULL workspace_id with the real
        // project scope — no acceptance-database default is needed or present.
        assertThat(workspaceIdOf(task.artifactId())).isEqualTo(PROJECT);
        assertThat(workspaceIdOf(subjectArtifactId)).isEqualTo(PROJECT);

        // 5. the cover bytes are really on disk and are a PNG
        byte[] coverBytes = readBack(task.artifactId());
        assertThat(coverBytes).hasSize((int) cover.byteLength());
        assertThat(sha256Hex(coverBytes)).isEqualTo(cover.contentDigest().canonicalValue());
        assertThat(new byte[] {coverBytes[0], coverBytes[1], coverBytes[2], coverBytes[3]})
                .containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);

        // 6. exactly one COVER_OF edge: the cover is the child/source, the subject is the parent/target
        assertThat(count("source_artifact_id = ? and target_artifact_id = ? and relation_type = 'COVER_OF'",
                task.artifactId(), subjectArtifactId)).isOne();
        assertThat(count("source_artifact_id = ?", subjectArtifactId)).isZero();
        assertThat(count("target_artifact_id = ?", task.artifactId())).isZero();
        // The canonical edge identity is the bounded 64-char digest of the (child, parent) pair, and
        // the endpoints stay the authoritative facts stored verbatim (platform fix D7).
        assertThat(jdbc.queryForObject(
                "select id from artifact_relation where source_artifact_id = ? and target_artifact_id = ?",
                String.class, task.artifactId(), subjectArtifactId))
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(com.example.platform.artifact.domain.ProvenanceValidator.canonicalEdgeId(
                        new ArtifactId(task.artifactId()), new ArtifactId(subjectArtifactId)));

        // 7. idempotent re-run: same submission returns the same task and the same cover Artifact
        var replayed = coverService.submit(new CoverImageContracts.Request(
                TENANT, PROJECT, subjectArtifactId, 0d, "png", 320, null, idempotencyKey));
        assertThat(replayed.taskId()).isEqualTo(submitted.taskId());
        assertThat(replayed.artifactId()).isEqualTo(task.artifactId());
        assertThat(replayed.status()).isEqualTo(CoverImageContracts.Status.COMPLETED);

        // 8. workflow-retry replay at the commit fence returns the same Artifact without re-committing
        String replayedArtifact = coverCommits.commit(TENANT, PROJECT, submitted.taskId(),
                subjectArtifactId, "image/png", new byte[] {1},
                WORK.resolve("blob-storage").resolve("replay-cover.png"),
                WORK.resolve("blob-storage").toString());
        assertThat(replayedArtifact).isEqualTo(task.artifactId());

        // 9. the run produced exactly two Artifacts (subject + cover) and exactly one COVER_OF edge
        assertThat(jdbc.queryForObject("select count(*) from artifact where tenant_id=? and id in (?,?)",
                Integer.class, TENANT, subjectArtifactId, task.artifactId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from artifact_relation where source_artifact_id=? and target_artifact_id=?",
                Integer.class, task.artifactId(), subjectArtifactId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from artifact_relation where target_artifact_id=?",
                Integer.class, subjectArtifactId)).isOne();
    }

    private CoverImageTaskStore.Task awaitCompleted(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(DEADLINE);
        CoverImageTaskStore.Task last = null;
        while (Instant.now().isBefore(deadline)) {
            Optional<CoverImageTaskStore.Task> found = tasks.find(TENANT, PROJECT, taskId);
            if (found.isPresent()) {
                last = found.get();
                if (last.status() == CoverImageContracts.Status.COMPLETED) {
                    return last;
                }
                assertThat(last.status())
                        .as("cover task failed with %s", last.failureCode())
                        .isNotIn(CoverImageContracts.Status.FAILED, CoverImageContracts.Status.CANCELLED);
            }
            Thread.sleep(500L);
        }
        throw new AssertionError("cover task did not complete within " + DEADLINE
                + "; last state=" + (last == null ? "absent" : last.status()));
    }

    /** Canonical persisted-placement read of the committed cover bytes. */
    private byte[] readBack(String coverArtifactId) {
        var replica = artifacts.listReplicas(TENANT, new ArtifactId(coverArtifactId)).getFirst();
        // The persisted-placement read is a tenant-scoped request-path API: establish the same scope
        // an authenticated request would have.
        TenantContext.set(TENANT);
        try {
            return placements.read(new StorageOwnershipScope(TENANT, PROJECT),
                    replica.storageObjectId(), replica.storageReplicaId());
        } finally {
            TenantContext.clear();
        }
    }

    private int count(String where, Object... args) {
        return jdbc.queryForObject(
                "select count(*) from artifact_relation where " + where, Integer.class, args);
    }

    private String workspaceIdOf(String artifactId) {
        return jdbc.queryForObject(
                "select workspace_id from artifact where id = ?", String.class, artifactId);
    }

    private static StorageNamespace namespace() {
        return new StorageNamespace(TENANT, PROJECT, NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION,
                DataClassification.INTERNAL);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
