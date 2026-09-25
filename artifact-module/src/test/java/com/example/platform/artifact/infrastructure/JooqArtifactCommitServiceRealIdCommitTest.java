package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactRelation.ARTIFACT_RELATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitRequest.ProvenanceEdgeDeclaration;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactErrorCode;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.artifact.domain.ProvenanceValidator;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.artifact.testutil.ArtifactSchemaFixture;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PLATFORM-ARTIFACT-COMMIT-001: real-identity commit tests through the canonical ArtifactCommitService.
 *
 * <p>Covers the two platform defects found by COVER-PROVIDER-001-CONTINUE-5:
 * <ul>
 *   <li><b>D4</b> — the canonical insert must populate {@code artifact.workspace_id} (V18:
 *       {@code NOT NULL}, derived as {@code coalesce(nullif(project_id, ''), 'legacy')});</li>
 *   <li><b>D7</b> — the canonical edge identity must fit {@code artifact_relation.id varchar(64)} for
 *       real platform identities ({@code art-<uuid>}, 40 characters), which the former
 *       {@code child + "-" + parent} concatenation could not.</li>
 * </ul>
 *
 * <p>Every test commits through {@link JooqArtifactCommitService#commit} — the single canonical write
 * path — against real PostgreSQL (Testcontainers), using real platform id shapes. No short-id
 * workaround and no synthetic workspace is used.
 */
class JooqArtifactCommitServiceRealIdCommitTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-artifact-commit";
    private static final String PROJECT = "project-artifact-commit";
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");
    private static final ContentDigest CHILD_DIGEST = ContentDigest.sha256("c".repeat(64));
    private static final ContentDigest PARENT_DIGEST = ContentDigest.sha256("d".repeat(64));
    private static final org.jooq.Field<String> WORKSPACE_ID =
            DSL.field(DSL.name("workspace_id"), String.class);

    private static DataSource dataSource;
    private static DSLContext dsl;
    private static JooqArtifactCommitService commitService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        ArtifactSchemaFixture.createCanonicalTables(new JdbcTemplate(dataSource));
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES,
                new Settings().withRenderNameCase(RenderNameCase.LOWER));
        commitService = new JooqArtifactCommitService(
                new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl), dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void cleanCanonicalTables() {
        dsl.execute("TRUNCATE TABLE artifact_relation, artifact_replica, artifact CASCADE");
    }

    /** Real platform identity shape: {@code art-<uuid>} (40 characters). */
    private static String realArtifactId() {
        return "art-" + UUID.randomUUID();
    }

    private static ArtifactCommitRequest request(
            String artifactId,
            String projectId,
            ContentDigest digest,
            String idempotencyKey,
            List<ProvenanceEdgeDeclaration> declarations) {
        return new ArtifactCommitRequest(
                new ArtifactId(artifactId),
                TENANT,
                digest,
                4096L,
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
                projectId);
    }

    private static ProvenanceEdgeDeclaration generatedFrom(String parentArtifactId) {
        return new ProvenanceEdgeDeclaration(
                new ArtifactId(parentArtifactId),
                ProvenanceRelationType.GENERATED_FROM,
                "artifact-commit:platform-artifact-commit@1",
                1,
                "attempt-real-id",
                CHILD_DIGEST.canonicalValue(),
                PARENT_DIGEST.canonicalValue());
    }

    private static String workspaceIdOf(String artifactId) {
        return dsl.select(WORKSPACE_ID).from(ARTIFACT)
                .where(ARTIFACT.ID.eq(artifactId).and(ARTIFACT.TENANT_ID.eq(TENANT)))
                .fetchOne(WORKSPACE_ID);
    }

    @Test
    void canonicalInsertPopulatesWorkspaceIdFromTheRealProjectContext() {
        String scoped = realArtifactId();
        String unscoped = realArtifactId();

        commitService.commit(request(scoped, PROJECT, CHILD_DIGEST, "key-scoped", List.of()));
        commitService.commit(request(unscoped, null, PARENT_DIGEST, "key-unscoped", List.of()));

        // Real project scope, not a synthetic workspace.
        assertThat(workspaceIdOf(scoped)).isEqualTo(PROJECT);
        // No project context: the migration's own canonical fallback, exactly as V18 backfilled it.
        assertThat(workspaceIdOf(unscoped)).isEqualTo("legacy");
    }

    @Test
    void commitSucceedsWithRealPlatformIdentitiesAndKeepsEndpointsVerbatim() {
        String parentId = realArtifactId();
        String childId = realArtifactId();
        assertThat(childId).hasSize(40);

        commitService.commit(request(parentId, PROJECT, PARENT_DIGEST, "key-parent", List.of()));

        ArtifactCommitResult result = commitService.commit(
                request(childId, PROJECT, CHILD_DIGEST, "key-child",
                        List.of(generatedFrom(parentId))));

        assertThat(result.provenanceEdges()).hasSize(1);
        assertThat(result.provenanceEdges().get(0).edgeId())
                .isEqualTo(ProvenanceValidator.canonicalEdgeId(
                        new ArtifactId(childId), new ArtifactId(parentId)));

        var row = dsl.select(ARTIFACT_RELATION.ID, ARTIFACT_RELATION.SOURCE_ARTIFACT_ID,
                        ARTIFACT_RELATION.TARGET_ARTIFACT_ID, ARTIFACT_RELATION.RELATION_TYPE)
                .from(ARTIFACT_RELATION)
                .where(ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(childId))
                .fetchOne();
        assertThat(row).isNotNull();
        // Endpoints stay the authoritative audit facts and are stored verbatim.
        assertThat(row.get(ARTIFACT_RELATION.TARGET_ARTIFACT_ID)).isEqualTo(parentId);
        assertThat(row.get(ARTIFACT_RELATION.RELATION_TYPE)).isEqualTo("GENERATED_FROM");
        // The relation identity now fits the landed column for real ids (the old concatenation could not).
        assertThat(row.get(ARTIFACT_RELATION.ID)).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(childId.length() + 1 + parentId.length())
                .as("the former child-parent concatenation would overflow varchar(64)")
                .isGreaterThan(64);
    }

    @Test
    void canonicalEdgeIdIsStableFixedWidthAndDirectionSensitive() {
        ArtifactId child = new ArtifactId(realArtifactId());
        ArtifactId parent = new ArtifactId(realArtifactId());

        String first = ProvenanceValidator.canonicalEdgeId(child, parent);
        String second = ProvenanceValidator.canonicalEdgeId(child, parent);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        // child/parent roles are semantic: the identity must not be symmetric.
        assertThat(ProvenanceValidator.canonicalEdgeId(parent, child)).isNotEqualTo(first);
        assertThat(ProvenanceValidator.canonicalEdgeId(child, new ArtifactId(realArtifactId())))
                .isNotEqualTo(first);
    }

    @Test
    void realIdentityCommitRemainsIdempotentAndFailsClosedOnReCommit() {
        String parentId = realArtifactId();
        String childId = realArtifactId();
        commitService.commit(request(parentId, PROJECT, PARENT_DIGEST, "key-parent-2", List.of()));

        ArtifactCommitResult first = commitService.commit(
                request(childId, PROJECT, CHILD_DIGEST, "key-child-2",
                        List.of(generatedFrom(parentId))));
        assertThat(first.idempotencyKey()).isEqualTo("key-child-2");

        assertThatThrownBy(() -> commitService.commit(
                request(childId, PROJECT, CHILD_DIGEST, "key-child-2b",
                        List.of(generatedFrom(parentId)))))
                .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class)
                .hasMessageContaining("Artifact already exists");

        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq(childId))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(childId))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.ID.eq(ProvenanceValidator.canonicalEdgeId(
                        new ArtifactId(childId), new ArtifactId(parentId))))).isOne();
    }
}
