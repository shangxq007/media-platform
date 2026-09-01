package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactRelation.ARTIFACT_RELATION;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactReplica.ARTIFACT_REPLICA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
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

class JooqArtifactCommitServiceProvenanceValidationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-provenance";
    private static final String OTHER_TENANT = "tenant-provenance-other";
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    private static DataSource dataSource;
    private static DSLContext dsl;
    private static ArtifactRepository artifactRepository;
    private static JooqArtifactCommitService commitService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        ArtifactSchemaFixture.createCanonicalTables(new JdbcTemplate(dataSource));
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES,
                new Settings().withRenderNameCase(RenderNameCase.LOWER));
        artifactRepository = new ArtifactRepository(dsl);
        commitService = new JooqArtifactCommitService(
                artifactRepository, new ArtifactRelationRepository(dsl), dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void cleanCanonicalTables() {
        dsl.execute("TRUNCATE TABLE artifact_relation, artifact_replica, artifact CASCADE");
    }

    @Test
    void validSameTenantProvenanceCommitSucceedsWithCanonicalDirection() {
        commitService.commit(request("parent-valid", TENANT, List.of()));

        ArtifactCommitResult result = commitService.commit(request(
                "child-valid", TENANT, List.of(declaration("parent-valid", "op-valid", "attempt-valid"))));

        assertThat(result.provenanceEdges()).hasSize(1);
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq("child-valid"))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_REPLICA,
                ARTIFACT_REPLICA.ARTIFACT_ID.eq("child-valid"))).isOne();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("child-valid")
                        .and(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq("parent-valid")))).isOne();
    }

    @Test
    void missingParentFailsClosedWithTypedEndpointCodeAndNoChildRows() {
        assertRejectedWithoutChildRows(
                request("child-missing", TENANT,
                        List.of(declaration("parent-missing", "op-missing", "attempt-missing"))),
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_ENDPOINT_NOT_FOUND);
    }

    @Test
    void crossTenantParentIsInvisibleAndFailsClosedWithoutChildRows() {
        commitService.commit(request("parent-foreign", OTHER_TENANT, List.of()));

        assertRejectedWithoutChildRows(
                request("child-cross-tenant", TENANT,
                        List.of(declaration("parent-foreign", "op-cross", "attempt-cross"))),
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_ENDPOINT_NOT_FOUND);
    }

    @Test
    void selfReferenceFailsWithTypedCodeAndNoChildRows() {
        assertRejectedWithoutChildRows(
                request("child-self", TENANT,
                        List.of(declaration("child-self", "op-self", "attempt-self"))),
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_SELF_REFERENCE);
    }

    @Test
    void duplicateDeclarationFailsBeforeAnySqlWithTypedCodeAndNoChildRows() {
        ArtifactRepository repository = mock(ArtifactRepository.class);
        ArtifactRelationRepository relations = mock(ArtifactRelationRepository.class);
        DSLContext unusedDsl = mock(DSLContext.class);
        JooqArtifactCommitService isolatedService =
                new JooqArtifactCommitService(repository, relations, unusedDsl);
        ArtifactCommitRequest duplicateRequest = request(
                "child-duplicate", TENANT,
                List.of(
                        declaration("parent-duplicate", "op-duplicate", "attempt-duplicate"),
                        declaration("parent-duplicate", "op-duplicate", "attempt-duplicate")));

        ArtifactErrorCode.ProvenanceException failure = catchThrowableOfType(
                () -> isolatedService.commit(duplicateRequest),
                ArtifactErrorCode.ProvenanceException.class);

        assertThat(failure.code()).isEqualTo(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_DUPLICATE);
        verifyNoInteractions(repository, relations, unusedDsl);
        assertNoChildRows("child-duplicate");
    }

    @Test
    void invalidOperationFailsWithTypedCodeAndNoChildRows() {
        ArtifactCommitRequest.ProvenanceEdgeDeclaration invalidOperation =
                new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                        new ArtifactId("parent-operation"),
                        ProvenanceRelationType.GENERATED_FROM,
                        " ", 0, "", "", null);

        assertRejectedWithoutChildRows(
                request("child-operation", TENANT, List.of(invalidOperation)),
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_OPERATION_INVALID);
    }

    @Test
    void validMultipleDistinctParentsSucceed() {
        commitService.commit(request("parent-multi-a", TENANT, List.of()));
        commitService.commit(request("parent-multi-b", TENANT, List.of()));

        ArtifactCommitResult result = commitService.commit(request(
                "child-multi", TENANT,
                List.of(
                        declaration("parent-multi-a", "op-compose", "attempt-compose"),
                        declaration("parent-multi-b", "op-compose", "attempt-compose"))));

        assertThat(result.provenanceEdges()).hasSize(2);
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("child-multi"))).isEqualTo(2);
    }

    @Test
    void primaryKeyAndBothEndpointForeignKeysBoundNewChildCycleMechanics() {
        List<String> constraintDefinitions = dsl.fetch(
                        "select pg_get_constraintdef(c.oid) as definition "
                                + "from pg_constraint c "
                                + "join pg_class t on t.oid = c.conrelid "
                                + "join pg_namespace n on n.oid = t.relnamespace "
                                + "where n.nspname = current_schema() "
                                + "and t.relname in ('artifact', 'artifact_relation')")
                .getValues("definition", String.class);

        assertThat(constraintDefinitions).contains(
                "PRIMARY KEY (id)",
                "FOREIGN KEY (source_artifact_id) REFERENCES artifact(id)",
                "FOREIGN KEY (target_artifact_id) REFERENCES artifact(id)");

        commitService.commit(request("bounded-existing-child", TENANT, List.of()));
        commitService.commit(request("bounded-parent", TENANT, List.of()));
        ArtifactCommitRequest attemptToAttachExistingChild = request(
                "bounded-existing-child", TENANT,
                List.of(declaration("bounded-parent", "op-bounded", "attempt-bounded")));

        ArtifactErrorCode.ArtifactDomainException failure = catchThrowableOfType(
                () -> commitService.commit(attemptToAttachExistingChild),
                ArtifactErrorCode.ArtifactDomainException.class);

        assertThat(failure.code()).isEqualTo(ArtifactErrorCode.Code.ARTIFACT_ALREADY_EXISTS);
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq("bounded-existing-child"))).isZero();
    }

    private static void assertRejectedWithoutChildRows(
            ArtifactCommitRequest request,
            ArtifactErrorCode.Code expectedCode) {
        ArtifactErrorCode.ProvenanceException failure = catchThrowableOfType(
                () -> commitService.commit(request),
                ArtifactErrorCode.ProvenanceException.class);

        assertThat(failure.code()).isEqualTo(expectedCode);
        assertThat(failure.error().artifactId()).isEqualTo(request.artifactId().value());
        assertNoChildRows(request.artifactId().value());
    }

    private static void assertNoChildRows(String childId) {
        assertThat(dsl.fetchCount(ARTIFACT, ARTIFACT.ID.eq(childId))).isZero();
        assertThat(dsl.fetchCount(ARTIFACT_REPLICA,
                ARTIFACT_REPLICA.ARTIFACT_ID.eq(childId))).isZero();
        assertThat(dsl.fetchCount(ARTIFACT_RELATION,
                ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(childId)
                        .or(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq(childId)))).isZero();
    }

    private static ArtifactCommitRequest request(
            String artifactId,
            String tenantId,
            List<ArtifactCommitRequest.ProvenanceEdgeDeclaration> declarations) {
        return new ArtifactCommitRequest(
                new ArtifactId(artifactId), tenantId, DIGEST, 100L,
                ArtifactMediaType.VIDEO, ArtifactKind.DERIVED_MEDIA, 1,
                new StorageObjectId("object-" + artifactId),
                new StorageReplicaId("replica-" + artifactId),
                new StorageProviderId("provider-test"), ReplicaRole.PRIMARY,
                "test-region", "commit-" + artifactId, declarations,
                NOW, NOW, null, null);
    }

    private static ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration(
            String parentArtifactId,
            String operationId,
            String attemptId) {
        return new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                new ArtifactId(parentArtifactId),
                ProvenanceRelationType.GENERATED_FROM,
                operationId, 1, attemptId, "request-digest", "result-digest");
    }
}
