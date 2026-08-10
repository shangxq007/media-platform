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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ArtifactQueryService — bounded traversal queries.
 */
@DisplayName("ArtifactQueryService")
class ArtifactQueryServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final StorageObjectId STORAGE_OBJECT_ID = new StorageObjectId("obj-001");
    private static final StorageReplicaId STORAGE_REPLICA_ID = new StorageReplicaId("rep-001");
    private static final StorageProviderId PROVIDER_ID = new StorageProviderId("provider-001");

    private InMemoryArtifactQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new InMemoryArtifactQueryService();
    }

    @Nested
    @DisplayName("Get artifact by ID")
    class GetArtifact {

        @Test
        @DisplayName("Returns artifact when found")
        void returnsArtifactWhenFound() {
            Artifact artifact = createArtifact("art-001", "tenant-1");
            ArtifactReplicaBinding binding = createBinding("art-001");
            queryService.addArtifact(artifact, binding);

            Optional<Artifact> result = queryService.getArtifact("tenant-1", new ArtifactId("art-001"));

            assertThat(result).isPresent();
            assertThat(result.get().artifactId().value()).isEqualTo("art-001");
        }

        @Test
        @DisplayName("Returns empty when artifact not found")
        void returnsEmptyWhenNotFound() {
            Optional<Artifact> result = queryService.getArtifact("tenant-1", new ArtifactId("nonexistent"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Returns empty when tenant does not match")
        void returnsEmptyWhenTenantMismatch() {
            Artifact artifact = createArtifact("art-001", "tenant-1");
            ArtifactReplicaBinding binding = createBinding("art-001");
            queryService.addArtifact(artifact, binding);

            Optional<Artifact> result = queryService.getArtifact("tenant-2", new ArtifactId("art-001"));
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Bounded traversal")
    class BoundedTraversal {

        @Test
        @DisplayName("Bounded ancestor traversal respects depth limit")
        void boundedAncestorTraversalRespectsDepth() {
            // Create chain: A -> B -> C -> D
            addArtifactWithEdge("A", "tenant-1", null);
            addArtifactWithEdge("B", "tenant-1", "A");
            addArtifactWithEdge("C", "tenant-1", "B");
            addArtifactWithEdge("D", "tenant-1", "C");

            List<ArtifactId> ancestors = queryService.boundedAncestorTraversal("tenant-1", new ArtifactId("D"), 2);

            // Should find C (depth 1) and B (depth 2), but not A (depth 3)
            assertThat(ancestors).hasSize(2);
            assertThat(ancestors.get(0).value()).isEqualTo("C");
            assertThat(ancestors.get(1).value()).isEqualTo("B");
        }

        @Test
        @DisplayName("Bounded descendant traversal respects depth limit")
        void boundedDescendantTraversalRespectsDepth() {
            // Create chain: A -> B -> C -> D
            addArtifactWithEdge("A", "tenant-1", null);
            addArtifactWithEdge("B", "tenant-1", "A");
            addArtifactWithEdge("C", "tenant-1", "B");
            addArtifactWithEdge("D", "tenant-1", "C");

            List<ArtifactId> descendants = queryService.boundedDescendantTraversal("tenant-1", new ArtifactId("A"), 2);

            assertThat(descendants).hasSize(2);
            assertThat(descendants.get(0).value()).isEqualTo("B");
            assertThat(descendants.get(1).value()).isEqualTo("C");
        }

        @Test
        @DisplayName("Traversal with maxDepth < 1 returns empty")
        void traversalWithZeroDepthReturnsEmpty() {
            addArtifactWithEdge("A", "tenant-1", null);

            List<ArtifactId> ancestors = queryService.boundedAncestorTraversal("tenant-1", new ArtifactId("A"), 0);
            assertThat(ancestors).isEmpty();
        }

        @Test
        @DisplayName("Traversal respects tenant isolation")
        void traversalRespectsTenantIsolation() {
            addArtifactWithEdge("A", "tenant-1", null);
            addArtifactWithEdge("B", "tenant-2", "A");

            List<ArtifactId> children = queryService.boundedDescendantTraversal("tenant-1", new ArtifactId("A"), 5);
            // B belongs to tenant-2, so it should not be visible
            assertThat(children).isEmpty();
        }
    }

    @Nested
    @DisplayName("Find by content digest")
    class FindByContentDigest {

        @Test
        @DisplayName("Finds artifacts with matching digest")
        void findsArtifactsWithMatchingDigest() {
            Artifact a1 = createArtifact("art-001", "tenant-1");
            Artifact a2 = createArtifact("art-002", "tenant-1");
            queryService.addArtifact(a1, createBinding("art-001"));
            queryService.addArtifact(a2, createBinding("art-002"));

            List<Artifact> results = queryService.findByContentDigest("tenant-1", DIGEST, 10);

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("Respects result limit")
        void respectsResultLimit() {
            Artifact a1 = createArtifact("art-001", "tenant-1");
            Artifact a2 = createArtifact("art-002", "tenant-1");
            queryService.addArtifact(a1, createBinding("art-001"));
            queryService.addArtifact(a2, createBinding("art-002"));

            List<Artifact> results = queryService.findByContentDigest("tenant-1", DIGEST, 1);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("Does not return artifacts from other tenants")
        void tenantIsolation() {
            Artifact a1 = createArtifact("art-001", "tenant-1");
            Artifact a2 = createArtifact("art-002", "tenant-2");
            queryService.addArtifact(a1, createBinding("art-001"));
            queryService.addArtifact(a2, createBinding("art-002"));

            List<Artifact> results = queryService.findByContentDigest("tenant-1", DIGEST, 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).artifactId().value()).isEqualTo("art-001");
        }
    }

    private Artifact createArtifact(String id, String tenantId) {
        return new Artifact(new ArtifactId(id), tenantId, DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
    }

    private ArtifactReplicaBinding createBinding(String artifactId) {
        return new ArtifactReplicaBinding(
                "binding-" + artifactId, new ArtifactId(artifactId),
                STORAGE_OBJECT_ID, STORAGE_REPLICA_ID, PROVIDER_ID,
                ReplicaRole.PRIMARY, "us-east-1", NOW);
    }

    private void addArtifactWithEdge(String id, String tenantId, String parentId) {
        Artifact artifact = createArtifact(id, tenantId);
        ArtifactReplicaBinding binding = createBinding(id);
        queryService.addArtifact(artifact, binding);

        if (parentId != null) {
            ProvenanceEdge edge = new ProvenanceEdge(
                    "edge-" + id, tenantId, new ArtifactId(parentId), new ArtifactId(id),
                    ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                    "req-digest", "res-digest", NOW);
            queryService.addEdge(edge);
        }
    }
}
