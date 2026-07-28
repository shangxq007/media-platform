package com.example.platform.artifact.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ProvenanceGraphProjection — deterministic graph digest.
 */
@DisplayName("ProvenanceGraphProjection")
class ProvenanceGraphProjectionTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");

    @Test
    @DisplayName("Same graph produces same digest regardless of edge insertion order")
    void sameGraphSameDigestRegardlessOfOrder() {
        Set<String> artifacts = Set.of("A", "B", "C");

        List<ProvenanceEdge> edges1 = List.of(
                createEdge("A", "B"),
                createEdge("B", "C")
        );

        List<ProvenanceEdge> edges2 = List.of(
                createEdge("B", "C"),
                createEdge("A", "B")
        );

        ProvenanceGraphProjection proj1 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges1);
        ProvenanceGraphProjection proj2 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges2);

        assertThat(proj1.graphDigest()).isEqualTo(proj2.graphDigest());
    }

    @Test
    @DisplayName("Different graphs produce different digests")
    void differentGraphsDifferentDigests() {
        Set<String> artifacts1 = Set.of("A", "B");
        Set<String> artifacts2 = Set.of("A", "B", "C");

        List<ProvenanceEdge> edges1 = List.of(createEdge("A", "B"));
        List<ProvenanceEdge> edges2 = List.of(createEdge("A", "B"), createEdge("B", "C"));

        ProvenanceGraphProjection proj1 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts1, edges1);
        ProvenanceGraphProjection proj2 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts2, edges2);

        assertThat(proj1.graphDigest()).isNotEqualTo(proj2.graphDigest());
    }

    @Test
    @DisplayName("Different tenants produce different digests for same graph structure")
    void differentTenantsDifferentDigests() {
        Set<String> artifacts = Set.of("A", "B");
        List<ProvenanceEdge> edges = List.of(createEdge("A", "B"));

        ProvenanceGraphProjection proj1 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges);
        ProvenanceGraphProjection proj2 = ProvenanceGraphProjection.fromEdges("tenant-2", artifacts, edges);

        assertThat(proj1.graphDigest()).isNotEqualTo(proj2.graphDigest());
    }

    @Test
    @DisplayName("Empty graph produces valid digest")
    void emptyGraphProducesValidDigest() {
        ProvenanceGraphProjection proj = ProvenanceGraphProjection.fromEdges("tenant-1", Set.of(), List.of());

        assertThat(proj.graphDigest()).isNotNull();
        assertThat(proj.graphDigest()).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("Graph digest is deterministic across multiple calls")
    void digestDeterministicAcrossCalls() {
        Set<String> artifacts = Set.of("A", "B", "C");
        List<ProvenanceEdge> edges = List.of(createEdge("A", "B"), createEdge("A", "C"));

        ProvenanceGraphProjection proj1 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges);
        ProvenanceGraphProjection proj2 = ProvenanceGraphProjection.fromEdges("tenant-1", artifacts, edges);

        assertThat(proj1.graphDigest()).isEqualTo(proj2.graphDigest());
    }

    private ProvenanceEdge createEdge(String parentId, String childId) {
        return new ProvenanceEdge(
                "edge-" + parentId + "-" + childId, "tenant-1",
                new ArtifactId(parentId), new ArtifactId(childId),
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest", "res-digest", NOW);
    }
}
