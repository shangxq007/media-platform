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
 * DB-backed cover-image artifact tests through the canonical ArtifactCommitService only.
 *
 * <p>Covers the cover representation decided for COVER-PROVIDER-001: an image Artifact related to its
 * subject by {@code ProvenanceRelationType.COVER_OF}, committed on the single canonical write path,
 * with idempotent re-runs producing no second Artifact.
 */
class JooqArtifactCommitServiceCoverOfTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-cover";
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final ContentDigest SUBJECT_DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final ContentDigest COVER_DIGEST = ContentDigest.sha256("b".repeat(64));
    /**
     * Real platform identity shapes. The subject is a canonical {@code art-<uuid>} (40 chars) and the
     * cover uses the slice's derived {@code art-cover-<uuid>} (46 chars); with the bounded canonical
     * edge identity both fit {@code artifact_relation.id varchar(64)} without any short-id workaround.
     */
    private static final String SUBJECT_ARTIFACT_ID = "art-" + java.util.UUID.randomUUID();
    private static final String COVER_ARTIFACT_ID = "art-cover-" + java.util.UUID.randomUUID();
    private static final String SUBJECT_ARTIFACT_ID_2 = "art-" + java.util.UUID.randomUUID();
    private static final String COVER_ARTIFACT_ID_2 = "art-cover-" + java.util.UUID.randomUUID();
    private static final String SUBJECT_ARTIFACT_ID_3 = "art-" + java.util.UUID.randomUUID();
    private static final String COVER_ARTIFACT_ID_3 = "art-cover-" + java.util.UUID.randomUUID();
    /**
     * Canonical cover provenance operation tag (platform-app owns CoverImageContracts; no module
     * cycle here). It is a capability-independent label — the capability id is never embedded in it.
     */
    private static final String COVER_OPERATION_TAG = "cover-image";

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
            List<ProvenanceEdgeDeclaration> declarations) {
        return new ArtifactCommitRequest(
                new ArtifactId(artifactId),
                tenantId,
                digest,
                1024L,
                ArtifactMediaType.IMAGE,
                ArtifactKind.DERIVED_MEDIA,
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
                "project-cover");
    }

    private static ProvenanceEdgeDeclaration coverOf(String subjectArtifactId) {
        return new ProvenanceEdgeDeclaration(
                new ArtifactId(subjectArtifactId),
                ProvenanceRelationType.COVER_OF,
                COVER_OPERATION_TAG,
                1,
                "attempt-cover",
                COVER_DIGEST.canonicalValue(),
                COVER_DIGEST.canonicalValue());
    }

    @Test
    void coverOfCommitPersistsCanonicalRelationEdgeSubjectToCover() {
        commitService.commit(request(SUBJECT_ARTIFACT_ID, TENANT, SUBJECT_DIGEST, "subject-key", List.of()));

        ArtifactCommitResult result = commitService.commit(request(
                COVER_ARTIFACT_ID, TENANT, COVER_DIGEST, "cover-key",
                List.of(coverOf(SUBJECT_ARTIFACT_ID))));

        assertThat(result.artifact().artifactKind()).isEqualTo(ArtifactKind.DERIVED_MEDIA);
        assertThat(result.artifact().mediaType()).isEqualTo(ArtifactMediaType.IMAGE);
        assertThat(result.provenanceEdges()).hasSize(1);
        assertThat(result.provenanceEdges().get(0).relationType())
                .isEqualTo(ProvenanceRelationType.COVER_OF);
        // The capability-independent provenance tag survives the canonical commit path unchanged.
        assertThat(result.provenanceEdges().get(0).operationId()).isEqualTo(COVER_OPERATION_TAG);
        // Real ids: the canonical edge identity must be the bounded 64-char digest.
        assertThat(result.provenanceEdges().get(0).edgeId()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(COVER_ARTIFACT_ID)
                        .and(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq(SUBJECT_ARTIFACT_ID))
                        .and(ARTIFACT_RELATION.RELATION_TYPE.eq("COVER_OF")))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(SUBJECT_ARTIFACT_ID))).isZero();
    }

    @Test
    void idempotencyKeyReplayIsDelegatedToTheCallerAndReCommitFailsClosed() {
        commitService.commit(request(SUBJECT_ARTIFACT_ID_2, TENANT, SUBJECT_DIGEST, "subject-key-2", List.of()));

        ArtifactCommitResult first = commitService.commit(request(
                COVER_ARTIFACT_ID_2, TENANT, COVER_DIGEST, "cover-key-2",
                List.of(coverOf(SUBJECT_ARTIFACT_ID_2))));

        // Canonical idempotency contract of the jOOQ adapter (COVER-PROVIDER-001 increment 4): the
        // adapter is fail-closed on an already-committed identity and does NOT perform an idempotent
        // re-commit; it delegates idempotency-key replay to the caller's durable record (for the
        // cover slice: CoverImageTaskStore). The request's idempotency key is carried back on the
        // canonical result so the caller can re-resolve its own durable record.
        assertThat(first.idempotencyKey()).isEqualTo("cover-key-2");
        assertThat(commitService.findByIdempotencyKey(TENANT, "cover-key-2")).isEmpty();

        // A re-commit of an existing Artifact identity fails closed and creates no second Artifact,
        // replica or provenance edge.
        assertThatThrownBy(() -> commitService.commit(request(
                COVER_ARTIFACT_ID_2, TENANT, COVER_DIGEST, "cover-key-2b",
                List.of(coverOf(SUBJECT_ARTIFACT_ID_2)))))
                .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class)
                .hasMessageContaining("Artifact already exists");
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.TENANT_ID.eq(TENANT))).isEqualTo(2);
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq(COVER_ARTIFACT_ID_2))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(COVER_ARTIFACT_ID_2))).isOne();
    }

    @Test
    void conflictingDigestForTheSameIdempotencyKeyFailsClosed() {
        commitService.commit(request(SUBJECT_ARTIFACT_ID_3, TENANT, SUBJECT_DIGEST, "subject-key-3", List.of()));
        commitService.commit(request(
                COVER_ARTIFACT_ID_3, TENANT, COVER_DIGEST, "cover-key-3",
                List.of(coverOf(SUBJECT_ARTIFACT_ID_3))));

        assertThatThrownBy(() -> commitService.commit(request(
                COVER_ARTIFACT_ID_3, TENANT, SUBJECT_DIGEST, "cover-key-3",
                List.of(coverOf(SUBJECT_ARTIFACT_ID_3)))))
                .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class);
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq(COVER_ARTIFACT_ID_3))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(COVER_ARTIFACT_ID_3))).isOne();
    }
}
