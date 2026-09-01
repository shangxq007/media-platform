package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactRelation.ARTIFACT_RELATION;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactReplica.ARTIFACT_REPLICA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactErrorCode;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JooqArtifactCommitServiceProvenanceErrorContextTest {

    private static final String TENANT = "tenant-provenance-context";
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    private RecordingDsl recordingDsl;
    private JooqArtifactCommitService commitService;

    @BeforeEach
    void setUp() {
        recordingDsl = new RecordingDsl();
        DSLContext dsl = (DSLContext) Proxy.newProxyInstance(
                DSLContext.class.getClassLoader(), new Class<?>[]{DSLContext.class}, recordingDsl);
        commitService = new JooqArtifactCommitService(
                new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl), dsl);
    }

    @Test
    void secondDeclarationInvalidOperationReportsItsOwnContextAndLeavesNoChildRows() {
        ArtifactCommitRequest.ProvenanceEdgeDeclaration invalidOperation =
                new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                        new ArtifactId("parent-context-invalid-operation"),
                        ProvenanceRelationType.GENERATED_FROM,
                        "op-context-invalid", 0, "attempt-context-invalid",
                        "request-digest", "result-digest");
        ArtifactCommitRequest commitRequest = request(
                "child-context-invalid-operation",
                List.of(
                        declaration("parent-context-valid", "op-context-valid", "attempt-context-valid"),
                        invalidOperation));

        assertRejectedWithDeclarationContextAndNoChildRows(
                commitRequest,
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_OPERATION_INVALID,
                invalidOperation);
    }

    @Test
    void secondDeclarationSelfReferenceReportsItsOwnContextAndLeavesNoChildRows() {
        ArtifactCommitRequest.ProvenanceEdgeDeclaration selfReference =
                declaration("child-context-self", "op-context-self", "attempt-context-self");
        ArtifactCommitRequest commitRequest = request(
                "child-context-self",
                List.of(
                        declaration(
                                "parent-context-self-valid",
                                "op-context-self-valid",
                                "attempt-context-self-valid"),
                        selfReference));

        assertRejectedWithDeclarationContextAndNoChildRows(
                commitRequest,
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_SELF_REFERENCE,
                selfReference);
    }

    @Test
    void secondDeclarationDuplicateReportsItsOwnContextAndLeavesNoChildRows() {
        ArtifactCommitRequest.ProvenanceEdgeDeclaration firstDeclaration = declaration(
                "parent-context-duplicate",
                "op-context-duplicate-first",
                "attempt-context-duplicate-first");
        ArtifactCommitRequest.ProvenanceEdgeDeclaration duplicateDeclaration = declaration(
                "parent-context-duplicate",
                "op-context-duplicate-second",
                "attempt-context-duplicate-second");
        ArtifactCommitRequest commitRequest = request(
                "child-context-duplicate", List.of(firstDeclaration, duplicateDeclaration));

        assertRejectedWithDeclarationContextAndNoChildRows(
                commitRequest,
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_DUPLICATE,
                duplicateDeclaration);
    }

    @Test
    void secondDeclarationInvalidOperationWinsOverLaterSelfReferenceWithMatchingContext() {
        ArtifactCommitRequest.ProvenanceEdgeDeclaration invalidOperation =
                new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                        new ArtifactId("parent-context-ordered-invalid"),
                        ProvenanceRelationType.GENERATED_FROM,
                        "op-context-ordered-invalid", 0, "attempt-context-ordered-invalid",
                        "request-digest", "result-digest");
        ArtifactCommitRequest commitRequest = request(
                "child-context-ordered",
                List.of(
                        declaration(
                                "parent-context-ordered-valid",
                                "op-context-ordered-valid",
                                "attempt-context-ordered-valid"),
                        invalidOperation,
                        declaration(
                                "child-context-ordered",
                                "op-context-ordered-self",
                                "attempt-context-ordered-self")));

        assertRejectedWithDeclarationContextAndNoChildRows(
                commitRequest,
                ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_OPERATION_INVALID,
                invalidOperation);
    }

    private void assertRejectedWithDeclarationContextAndNoChildRows(
            ArtifactCommitRequest request,
            ArtifactErrorCode.Code expectedCode,
            ArtifactCommitRequest.ProvenanceEdgeDeclaration expectedDeclaration) {
        ArtifactErrorCode.ProvenanceException failure = catchThrowableOfType(
                () -> commitService.commit(request),
                ArtifactErrorCode.ProvenanceException.class);

        assertThat(failure.code()).isEqualTo(expectedCode);
        assertThat(recordingDsl.artifactRowCount).isZero();
        assertThat(recordingDsl.replicaRowCount).isZero();
        assertThat(recordingDsl.relationRowCount).isZero();
        assertThat(failure.error())
                .extracting(
                        ArtifactErrorCode.Error::parentArtifactId,
                        ArtifactErrorCode.Error::operationId,
                        ArtifactErrorCode.Error::attemptId)
                .containsExactly(
                        expectedDeclaration.parentArtifactId().value(),
                        expectedDeclaration.operationId(),
                        expectedDeclaration.attemptId());
    }

    private static ArtifactCommitRequest request(
            String artifactId,
            List<ArtifactCommitRequest.ProvenanceEdgeDeclaration> declarations) {
        return new ArtifactCommitRequest(
                new ArtifactId(artifactId), TENANT, DIGEST, 100L,
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

    private static final class RecordingDsl implements InvocationHandler {

        private int artifactRowCount;
        private int replicaRowCount;
        private int relationRowCount;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "RecordingDsl";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            if ("insertInto".equals(method.getName()) && args != null && args.length > 0) {
                if (args[0] == ARTIFACT) {
                    artifactRowCount++;
                } else if (args[0] == ARTIFACT_REPLICA) {
                    replicaRowCount++;
                } else if (args[0] == ARTIFACT_RELATION) {
                    relationRowCount++;
                }
            }
            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return 0;
        }
    }
}
