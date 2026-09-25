package com.example.platform.artifact.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitRequest.ProvenanceEdgeDeclaration;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.artifact.domain.ProvenanceValidator;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PLATFORM-ARTIFACT-COMMIT-001: canonical commit against the <em>fully migrated</em> canonical schema.
 *
 * <p>This is the scenario that exposed D4 and D7: the artifact-module fixture schema is a subset, so
 * only a database carrying the complete canonical migration set (V1–V21, including
 * {@code V18__artifact_media_authority_convergence.sql}) can prove that the canonical insert satisfies
 * {@code artifact.workspace_id NOT NULL} and that the provenance edge identity fits
 * {@code artifact_relation.id varchar(64)} for real {@code art-<uuid>} identities.
 *
 * <p>Migrations are applied verbatim from the canonical directory into a per-class isolated schema of
 * the shared Testcontainers PostgreSQL runtime.
 */
class ArtifactCommitMigratedSchemaIntegrationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-migrated-schema";
    private static final String PROJECT = "project-migrated-schema";
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");
    private static final ContentDigest CHILD_DIGEST = ContentDigest.sha256("e".repeat(64));
    private static final ContentDigest PARENT_DIGEST = ContentDigest.sha256("f".repeat(64));
    private static final org.jooq.Field<String> WORKSPACE_ID =
            DSL.field(DSL.name("workspace_id"), String.class);

    private static DataSource dataSource;
    private static DSLContext dsl;
    private static JooqArtifactCommitService commitService;

    @BeforeAll
    static void applyCanonicalMigrations() throws Exception {
        String schema = isolatedSchemaName();
        try (Connection admin = DriverManager.getConnection(
                jdbcUrl() + "&currentSchema=public", username(), password());
             Statement statement = admin.createStatement()) {
            statement.execute("create schema " + schema);
        }
        dataSource = schemaPinnedDataSource(schema);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Path migrations = canonicalMigrationDirectory();
        List<Path> ordered;
        try (Stream<Path> files = Files.list(migrations)) {
            ordered = files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    // Flyway applies by version, not by file name: V1 must precede V16.
                    .sorted(Comparator.comparingInt(
                            path -> migrationVersion(path.getFileName().toString())))
                    .toList();
        }
        for (Path migration : ordered) {
            jdbc.execute(Files.readString(migration));
        }
        // Generated jOOQ tables are schema-qualified with "public"; map them onto the isolated schema
        // that carries the canonical migrations (the connection's currentSchema resolves untyped SQL).
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, new Settings()
                .withRenderNameCase(RenderNameCase.LOWER)
                .withRenderMapping(new RenderMapping().withSchemata(
                        new MappedSchema().withInput("public").withOutput(schema))));
        commitService = new JooqArtifactCommitService(
                new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl), dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    private static DataSource schemaPinnedDataSource(String schema) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl() + "&currentSchema=" + schema);
        config.setUsername(username());
        config.setPassword(password());
        config.setDriverClassName(driverClassName());
        config.setMaximumPoolSize(MANUAL_MAX_POOL_SIZE);
        config.setMinimumIdle(MANUAL_MIN_IDLE);
        return new HikariDataSource(config);
    }

    /** Leading {@code V<number>__} version, applied in numeric order exactly like Flyway. */
    private static int migrationVersion(String fileName) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^V(\\d+)__").matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalStateException("unrecognized migration file name: " + fileName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** The canonical platform-app migration directory (same resolution rule as other PG fixtures). */
    private static Path canonicalMigrationDirectory() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : new Path[] {
                workingDirectory.resolve("../platform-app/src/main/resources/db/migration"),
                workingDirectory.resolve("platform-app/src/main/resources/db/migration")}) {
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized.resolve("V1__initial_schema.sql"))) {
                return normalized;
            }
        }
        throw new IllegalStateException(
                "canonical platform-app Flyway migration directory is unavailable from "
                        + workingDirectory);
    }

    private static ArtifactCommitRequest request(
            String artifactId, ContentDigest digest, String idempotencyKey,
            List<ProvenanceEdgeDeclaration> declarations) {
        return new ArtifactCommitRequest(
                new ArtifactId(artifactId),
                TENANT,
                digest,
                8192L,
                ArtifactMediaType.IMAGE,
                ArtifactKind.DERIVED_MEDIA,
                1,
                new StorageObjectId("obj-" + artifactId),
                new StorageReplicaId("rep-" + artifactId),
                new StorageProviderId("local-object-store"),
                ReplicaRole.PRIMARY,
                "local",
                idempotencyKey,
                declarations,
                NOW,
                NOW,
                null,
                PROJECT);
    }

    @Test
    void canonicalCommitSatisfiesMigratedNotNulWorkspaceIdAndFitsRelationId() {
        // Exactly the identity shapes that blocked COVER-PROVIDER-001-CONTINUE-5: a canonical
        // art-<uuid> parent (40 chars) and a derived image child ("art-cover-" + uuid, 46 chars).
        String parentId = "art-" + UUID.randomUUID();
        String childId = "art-cover-" + UUID.randomUUID();
        assertThat(parentId).hasSize(40);
        assertThat(childId).hasSize(46);

        commitService.commit(request(parentId, PARENT_DIGEST, "migrated-key-parent", List.of()));
        ArtifactCommitResult result = commitService.commit(request(childId, CHILD_DIGEST,
                "migrated-key-child",
                List.of(new ProvenanceEdgeDeclaration(
                        new ArtifactId(parentId),
                        ProvenanceRelationType.GENERATED_FROM,
                        "artifact-commit:migrated-schema@1",
                        1,
                        "attempt-migrated",
                        CHILD_DIGEST.canonicalValue(),
                        PARENT_DIGEST.canonicalValue()))));

        // D4: the migrated NOT NULL workspace_id carries the real project scope.
        assertThat(dsl.select(WORKSPACE_ID).from(org.jooq.impl.DSL.table("artifact"))
                .where(DSL.field(DSL.name("id"), String.class).eq(childId))
                .fetchOne(WORKSPACE_ID)).isEqualTo(PROJECT);

        // D7: the real-identity edge is persisted with a bounded, canonical identity.
        assertThat(result.provenanceEdges()).hasSize(1);
        String edgeId = result.provenanceEdges().get(0).edgeId();
        assertThat(edgeId).hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(ProvenanceValidator.canonicalEdgeId(
                        new ArtifactId(childId), new ArtifactId(parentId)));
        assertThat(dsl.fetchCount(org.jooq.impl.DSL.table("artifact_relation"),
                DSL.field(DSL.name("id"), String.class).eq(edgeId)
                        .and(DSL.field(DSL.name("source_artifact_id"), String.class).eq(childId))
                        .and(DSL.field(DSL.name("target_artifact_id"), String.class).eq(parentId))
                        .and(DSL.field(DSL.name("relation_type"), String.class).eq("GENERATED_FROM"))))
                .isOne();
        // The former concatenation identity could not have fit the landed column for these ids.
        assertThat(childId.length() + 1 + parentId.length()).isGreaterThan(64);
    }
}
