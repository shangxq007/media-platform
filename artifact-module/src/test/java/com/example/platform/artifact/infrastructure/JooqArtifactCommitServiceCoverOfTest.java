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
    /** Canonical cover capability operation id (platform-app owns CoverImageContracts; no module cycle here). */
    private static final String COVER_OPERATION_ID = "cover-image:media.cover-image@1";

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
                COVER_OPERATION_ID,
                1,
                "attempt-cover",
                COVER_DIGEST.canonicalValue(),
                COVER_DIGEST.canonicalValue());
    }

    @Test
    void coverOfCommitPersistsCanonicalRelationEdgeSubjectToCover() {
        commitService.commit(request("art-subject", TENANT, SUBJECT_DIGEST, "subject-key", List.of()));

        ArtifactCommitResult result = commitService.commit(request(
                "art-cover", TENANT, COVER_DIGEST, "cover-key", List.of(coverOf("art-subject"))));

        assertThat(result.artifact().artifactKind()).isEqualTo(ArtifactKind.DERIVED_MEDIA);
        assertThat(result.artifact().mediaType()).isEqualTo(ArtifactMediaType.IMAGE);
        assertThat(result.provenanceEdges()).hasSize(1);
        assertThat(result.provenanceEdges().get(0).relationType())
                .isEqualTo(ProvenanceRelationType.COVER_OF);
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("art-cover")
                        .and(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq("art-subject"))
                        .and(ARTIFACT_RELATION.RELATION_TYPE.eq("COVER_OF")))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("art-subject"))).isZero();
    }

    @Test
    void idempotencyKeyResolvesToTheSameArtifactAndReCommitFailsClosed() {
        commitService.commit(request("art-subject", TENANT, SUBJECT_DIGEST, "subject-key-2", List.of()));

        ArtifactCommitResult first = commitService.commit(request(
                "art-cover-2", TENANT, COVER_DIGEST, "cover-key-2", List.of(coverOf("art-subject"))));

        // Canonical idempotency is the persisted key lookup; a re-commit of an existing identity fails closed.
        assertThat(commitService.findByIdempotencyKey(TENANT, "cover-key-2"))
                .isPresent()
                .get()
                .extracting(result -> result.artifact().artifactId().value())
                .isEqualTo(first.artifact().artifactId().value());
        assertThat(first.idempotencyKey()).isEqualTo("cover-key-2");
        assertThatThrownBy(() -> commitService.commit(request(
                "art-cover-2", TENANT, COVER_DIGEST, "cover-key-2b", List.of(coverOf("art-subject")))))
                .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class)
                .hasMessageContaining("Artifact already exists");
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq("art-cover-2"))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("art-cover-2"))).isOne();
    }

    @Test
    void conflictingDigestForTheSameIdempotencyKeyFailsClosed() {
        commitService.commit(request("art-subject", TENANT, SUBJECT_DIGEST, "subject-key-3", List.of()));
        commitService.commit(request(
                "art-cover-3", TENANT, COVER_DIGEST, "cover-key-3", List.of(coverOf("art-subject"))));

        assertThatThrownBy(() -> commitService.commit(request(
                "art-cover-3", TENANT, SUBJECT_DIGEST, "cover-key-3", List.of(coverOf("art-subject")))))
                .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class);
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq("art-cover-3"))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("art-cover-3"))).isOne();
    }
}
