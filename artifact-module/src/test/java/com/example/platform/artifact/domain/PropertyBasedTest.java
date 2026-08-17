package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for canonical serialization and graph invariants.
 */
@DisplayName("Property-Based Tests")
class PropertyBasedTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    @Test
    @DisplayName("Property: canonical serialization is idempotent")
    void canonicalSerializationIdempotent() {
        Artifact artifact = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        String canonical1 = artifact.canonicalForm();
        String canonical2 = artifact.canonicalForm();

        assertThat(canonical1).isEqualTo(canonical2);
        assertThat(CanonicalSerializer.digestArtifact(artifact))
                .isEqualTo(CanonicalSerializer.digestArtifact(artifact));
    }

    @Test
    @DisplayName("Property: DAG remains acyclic after valid edge insertion")
    void dagRemainsAcyclicAfterValidInsertion() {
        // Build a valid DAG incrementally
        List<ProvenanceEdge> edges = new ArrayList<>();
        edges.add(createEdge("A", "B"));
        edges.add(createEdge("B", "C"));
        edges.add(createEdge("A", "C"));

        assertThat(ProvenanceValidator.isAcyclic(edges)).isTrue();

        // Add more valid edges
        edges.add(createEdge("C", "D"));
        edges.add(createEdge("B", "D"));

        assertThat(ProvenanceValidator.isAcyclic(edges)).isTrue();
    }

    @Test
    @DisplayName("Property: cycle insertion is always rejected")
    void cycleInsertionAlwaysRejected() {
        List<ProvenanceEdge> edges = new ArrayList<>();
        edges.add(createEdge("A", "B"));
        edges.add(createEdge("B", "C"));

        // Try to add C -> A (creates cycle)
        ProvenanceEdge cycleEdge = createEdge("C", "A");
        java.util.Map<String, String> tenants = java.util.Map.of("A", "tenant-1", "B", "tenant-1", "C", "tenant-1");

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(cycleEdge, edges, tenants);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("CYCLE"));
    }

    @Test
    @DisplayName("Property: edge ordering does not affect graph digest")
    void edgeOrderingDoesNotAffectGraphDigest() {
        Set<String> artifacts = Set.of("A", "B", "C", "D");

        List<ProvenanceEdge> edges1 = List.of(
                createEdge("A", "B"),
                createEdge("B", "C"),
                createEdge("A", "D")
        );

        List<ProvenanceEdge> edges2 = new ArrayList<>(edges1);
        Collections.shuffle(edges2);

        ProvenanceGraphProjection proj1 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges1);
        ProvenanceGraphProjection proj2 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges2);

        assertThat(proj1.graphDigest()).isEqualTo(proj2.graphDigest());
    }

    @Test
    @DisplayName("Property: same idempotency request yields same artifact")
    void sameIdempotencyRequestYieldsSameArtifact() {
        InMemoryArtifactCommitService service = new InMemoryArtifactCommitService();

        ArtifactCommitRequest request = new ArtifactCommitRequest(
                new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                new StorageObjectId("obj-001"), new StorageReplicaId("rep-001"),
                new StorageProviderId("provider-001"), ReplicaRole.PRIMARY, "us-east-1",
                "idem-001", List.of(), NOW, NOW, null, null);

        ArtifactCommitResult result1 = service.commit(request);
        ArtifactCommitResult result2 = service.commit(request);

        assertThat(result1.artifact().artifactId()).isEqualTo(result2.artifact().artifactId());
        assertThat(result1.artifact().contentDigest()).isEqualTo(result2.artifact().contentDigest());
    }

    @Test
    @DisplayName("Property: immutable artifact never changes content identity")
    void immutableArtifactNeverChangesContentIdentity() {
        Artifact original = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.REGISTERING, 1, NOW);

        Artifact transitioned = original.withState(ArtifactState.AVAILABLE);

        // Content identity fields are preserved
        assertThat(original.contentDigest()).isEqualTo(transitioned.contentDigest());
        assertThat(original.byteLength()).isEqualTo(transitioned.byteLength());
        assertThat(original.artifactId()).isEqualTo(transitioned.artifactId());
        assertThat(original.tenantId()).isEqualTo(transitioned.tenantId());
        assertThat(original.mediaType()).isEqualTo(transitioned.mediaType());
        assertThat(original.artifactKind()).isEqualTo(transitioned.artifactKind());
        assertThat(original.schemaVersion()).isEqualTo(transitioned.schemaVersion());
        assertThat(original.createdAt()).isEqualTo(transitioned.createdAt());
    }

    @Test
    @DisplayName("Property: canonical serializer produces stable digests")
    void canonicalSerializerProducesStableDigests() {
        Artifact a1 = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
        Artifact a2 = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        assertThat(CanonicalSerializer.digestArtifact(a1))
                .isEqualTo(CanonicalSerializer.digestArtifact(a2));
    }

    @Test
    @DisplayName("Property: multi-input derivation (multiple parents) is supported")
    void multiInputDerivationSupported() {
        InMemoryArtifactCommitService service = new InMemoryArtifactCommitService();

        // Create parent artifacts
        service.commit(createRequest("parent-1", "tenant-1", "idem-p1"));
        service.commit(createRequest("parent-2", "tenant-1", "idem-p2"));

        // Create child with multiple parents
        ArtifactCommitRequest childRequest = new ArtifactCommitRequest(
                new ArtifactId("child-001"), "tenant-1", DIGEST, 1500L,
                ArtifactMediaType.VIDEO, ArtifactKind.DERIVED_MEDIA, 1,
                new StorageObjectId("obj-child"), new StorageReplicaId("rep-child"),
                new StorageProviderId("provider-001"), ReplicaRole.PRIMARY, "us-east-1",
                "idem-child",
                List.of(
                        new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                                new ArtifactId("parent-1"), ProvenanceRelationType.COMPOSED_FROM,
                                "op-compose", 1, "attempt-1", "req-1", "res-1"),
                        new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                                new ArtifactId("parent-2"), ProvenanceRelationType.COMPOSED_FROM,
                                "op-compose", 1, "attempt-1", "req-2", "res-2")
                ), NOW, NOW, null, null);

        ArtifactCommitResult result = service.commit(childRequest);

        assertThat(result.provenanceEdges()).hasSize(2);
    }

    private ProvenanceEdge createEdge(String parentId, String childId) {
        return new ProvenanceEdge(
                "edge-" + parentId + "-" + childId, "tenant-1",
                new ArtifactId(parentId), new ArtifactId(childId),
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest", "res-digest", NOW);
    }

    private ArtifactCommitRequest createRequest(String id, String tenantId, String idemKey) {
        return new ArtifactCommitRequest(
                new ArtifactId(id), tenantId, DIGEST, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, 1,
                new StorageObjectId("obj-" + id), new StorageReplicaId("rep-" + id),
                new StorageProviderId("provider-001"), ReplicaRole.PRIMARY, "us-east-1",
                idemKey, List.of(), NOW, NOW, null, null);
    }
}
