package com.example.platform.artifact.domain;

import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ArtifactCommitService — atomic creation and idempotency.
 */
@DisplayName("ArtifactCommitService")
class ArtifactCommitServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final StorageObjectId STORAGE_OBJECT_ID = new StorageObjectId("obj-001");
    private static final StorageReplicaId STORAGE_REPLICA_ID = new StorageReplicaId("rep-001");
    private static final StorageProviderId PROVIDER_ID = new StorageProviderId("provider-001");

    private InMemoryArtifactCommitService commitService;

    @BeforeEach
    void setUp() {
        commitService = new InMemoryArtifactCommitService();
    }

    @Nested
    @DisplayName("Successful commit")
    class SuccessfulCommit {

        @Test
        @DisplayName("Commits artifact with REGISTERING -> AVAILABLE transition")
        void commitsArtifactWithTransition() {
            ArtifactCommitRequest request = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-001",
                    List.of(), NOW, NOW);

            ArtifactCommitResult result = commitService.commit(request);

            assertThat(result.artifact().state()).isEqualTo(ArtifactState.AVAILABLE);
            assertThat(result.replicaBinding().artifactId().value()).isEqualTo("art-001");
            assertThat(result.idempotencyKey()).isEqualTo("idem-001");
        }

        @Test
        @DisplayName("Commits artifact with provenance edges")
        void commitsWithProvenanceEdges() {
            // First create a parent artifact
            ArtifactCommitRequest parentRequest = new ArtifactCommitRequest(
                    new ArtifactId("parent-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    new StorageObjectId("obj-parent"), new StorageReplicaId("rep-parent"),
                    PROVIDER_ID, ReplicaRole.PRIMARY, "us-east-1", "idem-parent",
                    List.of(), NOW, NOW);
            commitService.commit(parentRequest);

            // Then create a child with provenance
            ArtifactCommitRequest childRequest = new ArtifactCommitRequest(
                    new ArtifactId("child-001"), "tenant-1", DIGEST, 500L,
                    ArtifactMediaType.VIDEO, ArtifactKind.DERIVED_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-child",
                    List.of(new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                            new ArtifactId("parent-001"),
                            ProvenanceRelationType.TRANSCODED_FROM,
                            "op-1", 1, "attempt-1",
                            "req-digest", "res-digest"
                    )), NOW, NOW);

            ArtifactCommitResult result = commitService.commit(childRequest);

            assertThat(result.provenanceEdges()).hasSize(1);
            assertThat(result.provenanceEdges().get(0).parentArtifactId().value()).isEqualTo("parent-001");
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("Same idempotency key returns same result")
        void sameIdempotencyKeyReturnsSameResult() {
            ArtifactCommitRequest request = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-001",
                    List.of(), NOW, NOW);

            ArtifactCommitResult result1 = commitService.commit(request);
            ArtifactCommitResult result2 = commitService.commit(request);

            assertThat(result1.artifact().artifactId()).isEqualTo(result2.artifact().artifactId());
            assertThat(result1).isEqualTo(result2);
        }

        @Test
        @DisplayName("Different request with same idempotency key causes conflict")
        void differentRequestSameIdempotencyKeyConflict() {
            ArtifactCommitRequest request1 = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-001",
                    List.of(), NOW, NOW);
            commitService.commit(request1);

            ContentDigest differentDigest = ContentDigest.sha256("b".repeat(64));
            ArtifactCommitRequest request2 = new ArtifactCommitRequest(
                    new ArtifactId("art-002"), "tenant-1", differentDigest, 2000L,
                    ArtifactMediaType.AUDIO, ArtifactKind.SOURCE_MEDIA, 1,
                    new StorageObjectId("obj-002"), new StorageReplicaId("rep-002"),
                    PROVIDER_ID, ReplicaRole.SECONDARY, "us-west-2", "idem-001",
                    List.of(), NOW, NOW);

            assertThatThrownBy(() -> commitService.commit(request2))
                    .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class)
                    .satisfies(e -> assertThat(((ArtifactErrorCode.ArtifactDomainException) e).code())
                            .isEqualTo(ArtifactErrorCode.Code.ARTIFACT_IDEMPOTENCY_CONFLICT));
        }

        @Test
        @DisplayName("findByIdempotencyKey returns previous result")
        void findByIdempotencyKeyReturnsPreviousResult() {
            ArtifactCommitRequest request = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-001",
                    List.of(), NOW, NOW);

            commitService.commit(request);
            Optional<ArtifactCommitResult> found = commitService.findByIdempotencyKey("tenant-1", "idem-001");

            assertThat(found).isPresent();
            assertThat(found.get().artifact().artifactId().value()).isEqualTo("art-001");
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("Duplicate artifact ID is rejected")
        void duplicateArtifactIdRejected() {
            ArtifactCommitRequest request = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-001",
                    List.of(), NOW, NOW);

            commitService.commit(request);

            ArtifactCommitRequest duplicate = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-002",
                    List.of(), NOW, NOW);

            assertThatThrownBy(() -> commitService.commit(duplicate))
                    .isInstanceOf(ArtifactErrorCode.ArtifactDomainException.class);
        }

        @Test
        @DisplayName("Self-reference provenance is rejected")
        void selfReferenceProvenanceRejected() {
            ArtifactCommitRequest request = new ArtifactCommitRequest(
                    new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                    STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                    ReplicaRole.PRIMARY, "us-east-1", "idem-001",
                    List.of(new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                            new ArtifactId("art-001"),
                            ProvenanceRelationType.GENERATED_FROM,
                            "op-1", 1, "attempt-1",
                            "req-digest", "res-digest"
                    )), NOW, NOW);

            assertThatThrownBy(() -> commitService.commit(request))
                    .isInstanceOf(ArtifactErrorCode.ProvenanceException.class)
                    .satisfies(e -> assertThat(((ArtifactErrorCode.ProvenanceException) e).code())
                            .isEqualTo(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_SELF_REFERENCE));
        }
    }
}
