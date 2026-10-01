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
 * DB-backed thumbnail artifact tests through the canonical ArtifactCommitService only.
 *
 * <p>COVER-THUMBNAIL-REBUILD-001 (action 5): a thumbnail is an image Artifact whose thumbnail role is
 * the {@code THUMBNAIL_OF} provenance relation, not a dedicated {@code ArtifactKind.THUMBNAIL} — the
 * same relation-based model the cover slice uses with {@code COVER_OF}. The legacy
 * {@code ArtifactKind.THUMBNAIL} constant is retained for pre-rebuild rows but is not used by the new
 * commit path.
 */
class JooqArtifactCommitServiceThumbnailOfTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-thumbnail";
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final ContentDigest SUBJECT_DIGEST = ContentDigest.sha256("c".repeat(64));
    private static final ContentDigest THUMBNAIL_DIGEST = ContentDigest.sha256("d".repeat(64));
    private static final String SUBJECT_ARTIFACT_ID = "art-" + java.util.UUID.randomUUID();
    private static final String THUMBNAIL_ARTIFACT_ID = "art-thumb-" + java.util.UUID.randomUUID();
    /** Capability-independent thumbnail provenance operation tag (mirrors CoverImageContracts). */
    private static final String THUMBNAIL_OPERATION_TAG = "thumbnail";

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

    private static ArtifactCommitRequest request(
            String artifactId,
            String tenantId,
            ContentDigest digest,
            String idempotencyKey,
            ArtifactKind kind,
            List<ProvenanceEdgeDeclaration> declarations) {
        return new ArtifactCommitRequest(
                new ArtifactId(artifactId),
                tenantId,
                digest,
                2048L,
                ArtifactMediaType.IMAGE,
                kind,
                1,
                new StorageObjectId("obj-" + artifactId),
                new StorageReplicaId("rep-" + artifactId),
                new StorageProviderId("local"),
                ReplicaRole.PRIMARY,
                "local",
                idempotencyKey,
                declarations,
                NOW,
                NOW,
                null,
                "project-thumbnail");
    }

    private static ProvenanceEdgeDeclaration thumbnailOf(String subjectArtifactId) {
        return new ProvenanceEdgeDeclaration(
                new ArtifactId(subjectArtifactId),
                ProvenanceRelationType.THUMBNAIL_OF,
                THUMBNAIL_OPERATION_TAG,
                1,
                "attempt-thumbnail",
                THUMBNAIL_DIGEST.canonicalValue(),
                THUMBNAIL_DIGEST.canonicalValue());
    }

    @Test
    void thumbnailOfCommitPersistsCanonicalRelationEdgeSubjectToThumbnail() {
        commitService.commit(request(
                SUBJECT_ARTIFACT_ID, TENANT, SUBJECT_DIGEST, "subject-key",
                ArtifactKind.SOURCE_MEDIA, List.of()));

        ArtifactCommitResult result = commitService.commit(request(
                THUMBNAIL_ARTIFACT_ID, TENANT, THUMBNAIL_DIGEST, "thumbnail-key",
                ArtifactKind.DERIVED_MEDIA, List.of(thumbnailOf(SUBJECT_ARTIFACT_ID))));

        assertThat(result.artifact().artifactKind()).isEqualTo(ArtifactKind.DERIVED_MEDIA);
        assertThat(result.artifact().mediaType()).isEqualTo(ArtifactMediaType.IMAGE);
        assertThat(result.provenanceEdges()).hasSize(1);
        assertThat(result.provenanceEdges().get(0).relationType())
                .isEqualTo(ProvenanceRelationType.THUMBNAIL_OF);
        assertThat(result.provenanceEdges().get(0).operationId()).isEqualTo(THUMBNAIL_OPERATION_TAG);
        assertThat(result.provenanceEdges().get(0).edgeId()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(THUMBNAIL_ARTIFACT_ID)
                        .and(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq(SUBJECT_ARTIFACT_ID))
                        .and(ARTIFACT_RELATION.RELATION_TYPE.eq("THUMBNAIL_OF")))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(SUBJECT_ARTIFACT_ID))).isZero();
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq(THUMBNAIL_ARTIFACT_ID)
                .and(ARTIFACT.ARTIFACT_KIND.eq("DERIVED_MEDIA")))).isOne();
    }

    @Test
    void reCommitOfTheSameThumbnailIdentityFailsClosed() {
        commitService.commit(request(
                SUBJECT_ARTIFACT_ID, TENANT, SUBJECT_DIGEST, "subject-key-2",
                ArtifactKind.SOURCE_MEDIA, List.of()));
        commitService.commit(request(
                THUMBNAIL_ARTIFACT_ID, TENANT, THUMBNAIL_DIGEST, "thumbnail-key-2",
                ArtifactKind.DERIVED_MEDIA, List.of(thumbnailOf(SUBJECT_ARTIFACT_ID))));

        assertThatThrownBy(() -> commitService.commit(request(
                THUMBNAIL_ARTIFACT_ID, TENANT, THUMBNAIL_DIGEST, "thumbnail-key-2b",
                ArtifactKind.DERIVED_MEDIA, List.of(thumbnailOf(SUBJECT_ARTIFACT_ID)))))
                .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class)
                .hasMessageContaining("Artifact already exists");
    }
}
