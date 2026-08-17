package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ArtifactDescriptor, ArtifactReplicaBinding, and typed errors.
 */
@DisplayName("Supporting Types")
class SupportingTypesTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");

    @Nested
    @DisplayName("ArtifactDescriptor")
    class ArtifactDescriptorTest {

        @Test
        @DisplayName("Empty descriptor is valid")
        void emptyDescriptorValid() {
            ArtifactDescriptor descriptor = ArtifactDescriptor.empty();
            assertThat(descriptor.tags()).isEmpty();
            assertThat(descriptor.customFields()).isEmpty();
        }

        @Test
        @DisplayName("Builder creates descriptor with all fields")
        void builderCreatesDescriptor() {
            ArtifactDescriptor descriptor = ArtifactDescriptor.builder()
                    .title("My Video")
                    .description("A test video")
                    .tag("category", "media")
                    .customField("resolution", "1080p")
                    .build();

            assertThat(descriptor.title()).isEqualTo("My Video");
            assertThat(descriptor.tags()).containsEntry("category", "media");
            assertThat(descriptor.customFields()).containsEntry("resolution", "1080p");
        }

        @Test
        @DisplayName("toBuilder preserves all fields")
        void toBuilderPreservesFields() {
            ArtifactDescriptor original = ArtifactDescriptor.builder()
                    .title("Original")
                    .tag("key", "value")
                    .build();

            ArtifactDescriptor copy = original.toBuilder().build();

            assertThat(copy).isEqualTo(original);
        }

        @Test
        @DisplayName("Canonical form is deterministic (tags sorted by key)")
        void canonicalFormDeterministic() {
            ArtifactDescriptor d1 = ArtifactDescriptor.builder()
                    .tag("z", "1").tag("a", "2").build();
            ArtifactDescriptor d2 = ArtifactDescriptor.builder()
                    .tag("a", "2").tag("z", "1").build();

            assertThat(d1.canonicalForm()).isEqualTo(d2.canonicalForm());
        }
    }

    @Nested
    @DisplayName("ArtifactReplicaBinding")
    class ArtifactReplicaBindingTest {

        @Test
        @DisplayName("Creates valid binding")
        void createsValidBinding() {
            ArtifactReplicaBinding binding = new ArtifactReplicaBinding(
                    "binding-001", new ArtifactId("art-001"),
                    new StorageObjectId("obj-001"), new StorageReplicaId("rep-001"),
                    new StorageProviderId("provider-001"), ReplicaRole.PRIMARY,
                    "us-east-1", NOW);

            assertThat(binding.replicaRole()).isEqualTo(ReplicaRole.PRIMARY);
            assertThat(binding.region()).isEqualTo("us-east-1");
        }

        @Test
        @DisplayName("Validates blank bindingId")
        void validatesBlankBindingId() {
            assertThatThrownBy(() -> new ArtifactReplicaBinding(
                    "", new ArtifactId("art-001"),
                    new StorageObjectId("obj-001"), new StorageReplicaId("rep-001"),
                    new StorageProviderId("provider-001"), ReplicaRole.PRIMARY,
                    "us-east-1", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Canonical form is deterministic")
        void canonicalFormDeterministic() {
            ArtifactReplicaBinding b = new ArtifactReplicaBinding(
                    "binding-001", new ArtifactId("art-001"),
                    new StorageObjectId("obj-001"), new StorageReplicaId("rep-001"),
                    new StorageProviderId("provider-001"), ReplicaRole.PRIMARY,
                    "us-east-1", NOW);

            assertThat(b.canonicalForm()).isEqualTo(b.canonicalForm());
        }
    }

    @Nested
    @DisplayName("ReplicaRole")
    class ReplicaRoleTest {

        @Test
        @DisplayName("All roles are defined")
        void allRolesDefined() {
            assertThat(ReplicaRole.values()).containsExactlyInAnyOrder(
                    ReplicaRole.PRIMARY, ReplicaRole.SECONDARY, ReplicaRole.CACHE,
                    ReplicaRole.ARCHIVE, ReplicaRole.DELIVERY);
        }
    }

    @Nested
    @DisplayName("ArtifactErrorCode")
    class ArtifactErrorCodeTest {

        @Test
        @DisplayName("All 16 error codes are defined")
        void allErrorCodesDefined() {
            assertThat(ArtifactErrorCode.Code.values()).hasSize(16);
        }

        @Test
        @DisplayName("Error codes have unique code strings")
        void errorCodesUnique() {
            long distinctCodes = java.util.Arrays.stream(ArtifactErrorCode.Code.values())
                    .map(ArtifactErrorCode.Code::codeString)
                    .distinct()
                    .count();
            assertThat(distinctCodes).isEqualTo(ArtifactErrorCode.Code.values().length);
        }

        @Test
        @DisplayName("Error builder creates error with all fields")
        void errorBuilderCreatesError() {
            ArtifactErrorCode.Error error = ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_NOT_FOUND)
                    .tenantId("tenant-1")
                    .artifactId("art-001")
                    .parentArtifactId("parent-001")
                    .childArtifactId("child-001")
                    .storageReplicaId("rep-001")
                    .operationId("op-001")
                    .attemptId("attempt-001")
                    .expected("expected-value")
                    .actual("actual-value")
                    .build();

            assertThat(error.code()).isEqualTo(ArtifactErrorCode.Code.ARTIFACT_NOT_FOUND);
            assertThat(error.tenantId()).isEqualTo("tenant-1");
            assertThat(error.artifactId()).isEqualTo("art-001");
        }

        @Test
        @DisplayName("ArtifactDomainException carries error")
        void domainExceptionCarriesError() {
            ArtifactErrorCode.Error error = ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_NOT_FOUND)
                    .tenantId("tenant-1")
                    .artifactId("art-001")
                    .build();

            ArtifactErrorCode.ArtifactDomainException ex = new ArtifactErrorCode.ArtifactDomainException(error);

            assertThat(ex.code()).isEqualTo(ArtifactErrorCode.Code.ARTIFACT_NOT_FOUND);
            assertThat(ex.error()).isEqualTo(error);
        }

        @Test
        @DisplayName("ProvenanceException carries violations")
        void provenanceExceptionCarriesViolations() {
            ArtifactErrorCode.Error error = ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CYCLE)
                    .tenantId("tenant-1")
                    .build();

            List<String> violations = List.of("Cycle detected: A -> B -> A");
            ArtifactErrorCode.ProvenanceException ex = new ArtifactErrorCode.ProvenanceException(error, violations);

            assertThat(ex.code()).isEqualTo(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CYCLE);
            assertThat(ex.violations()).isEqualTo(violations);
        }
    }

    @Nested
    @DisplayName("ProvenanceEdge")
    class ProvenanceEdgeTest {

        @Test
        @DisplayName("Creates valid edge")
        void createsValidEdge() {
            ProvenanceEdge edge = new ProvenanceEdge(
                    "edge-001", "tenant-1",
                    new ArtifactId("parent-001"), new ArtifactId("child-001"),
                    ProvenanceRelationType.GENERATED_FROM, "op-001", 1, "attempt-001",
                    "req-digest", "res-digest", NOW);

            assertThat(edge.relationType()).isEqualTo(ProvenanceRelationType.GENERATED_FROM);
            assertThat(edge.operationVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Validates operationVersion >= 1")
        void validatesOperationVersion() {
            assertThatThrownBy(() -> new ProvenanceEdge(
                    "edge-001", "tenant-1",
                    new ArtifactId("parent-001"), new ArtifactId("child-001"),
                    ProvenanceRelationType.GENERATED_FROM, "op-001", 0, "attempt-001",
                    "req-digest", "res-digest", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ProvenanceOperation")
    class ProvenanceOperationTest {

        @Test
        @DisplayName("Creates valid operation via builder")
        void createsValidOperation() {
            ProvenanceOperation op = ProvenanceOperation.builder(
                    "op-001", 1, "transcode", "attempt-001", "req-digest", "res-digest")
                    .capabilityId("cap-001")
                    .providerId("provider-001")
                    .modelId("model-001")
                    .build();

            assertThat(op.operationId()).isEqualTo("op-001");
            assertThat(op.capabilityId()).isEqualTo("cap-001");
        }

        @Test
        @DisplayName("Canonical form is deterministic")
        void canonicalFormDeterministic() {
            ProvenanceOperation op = ProvenanceOperation.builder(
                    "op-001", 1, "transcode", "attempt-001", "req-digest", "res-digest")
                    .build();

            assertThat(op.canonicalForm()).isEqualTo(op.canonicalForm());
        }
    }
}
