package com.example.platform.artifact.domain;

import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ProvenanceValidator — O(V+E) cycle detection.
 */
@DisplayName("ProvenanceValidator")
class ProvenanceValidatorTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");

    @Test
    @DisplayName("Valid single-parent provenance edge")
    void validSingleParentEdge() {
        ArtifactId parent = new ArtifactId("parent-1");
        ArtifactId child = new ArtifactId("child-1");
        ProvenanceEdge edge = new ProvenanceEdge("edge-1", "tenant-1", parent, child,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest-1", "res-digest-1", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge, List.of(), Map.of("parent-1", "tenant-1", "child-1", "tenant-1"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("Self-reference is rejected")
    void selfReferenceRejected() {
        ArtifactId artifact = new ArtifactId("art-1");
        ProvenanceEdge edge = new ProvenanceEdge("edge-1", "tenant-1", artifact, artifact,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest-1", "res-digest-1", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge, List.of(), Map.of("art-1", "tenant-1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("SELF_REFERENCE"));
    }

    @Test
    @DisplayName("Cross-tenant edge is rejected")
    void crossTenantEdgeRejected() {
        ArtifactId parent = new ArtifactId("parent-1");
        ArtifactId child = new ArtifactId("child-1");
        ProvenanceEdge edge = new ProvenanceEdge("edge-1", "tenant-1", parent, child,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest-1", "res-digest-1", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge, List.of(), Map.of("parent-1", "tenant-2", "child-1", "tenant-1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("CROSS_TENANT"));
    }

    @Test
    @DisplayName("Missing endpoint is detected")
    void missingEndpointDetected() {
        ArtifactId parent = new ArtifactId("parent-1");
        ArtifactId child = new ArtifactId("child-1");
        ProvenanceEdge edge = new ProvenanceEdge("edge-1", "tenant-1", parent, child,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest-1", "res-digest-1", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge, List.of(), Map.of("child-1", "tenant-1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("ENDPOINT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Duplicate edgeId is rejected")
    void duplicateEdgeIdRejected() {
        ArtifactId parent = new ArtifactId("parent-1");
        ArtifactId child = new ArtifactId("child-1");
        ProvenanceEdge edge1 = new ProvenanceEdge("edge-1", "tenant-1", parent, child,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-digest-1", "res-digest-1", NOW);
        ProvenanceEdge edge2 = new ProvenanceEdge("edge-1", "tenant-1", parent, child,
                ProvenanceRelationType.GENERATED_FROM, "op-2", 1, "attempt-2",
                "req-digest-2", "res-digest-2", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge2, List.of(edge1), Map.of("parent-1", "tenant-1", "child-1", "tenant-1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("DUPLICATE"));
    }

    @Test
    @DisplayName("Direct cycle is detected")
    void directCycleDetected() {
        // A -> B, then B -> A creates a cycle
        ArtifactId a = new ArtifactId("A");
        ArtifactId b = new ArtifactId("B");
        ProvenanceEdge edge1 = new ProvenanceEdge("edge-1", "tenant-1", a, b,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-1", "res-1", NOW);
        ProvenanceEdge edge2 = new ProvenanceEdge("edge-2", "tenant-1", b, a,
                ProvenanceRelationType.GENERATED_FROM, "op-2", 1, "attempt-2",
                "req-2", "res-2", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge2, List.of(edge1), Map.of("A", "tenant-1", "B", "tenant-1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("CYCLE"));
    }

    @Test
    @DisplayName("Multi-hop cycle is detected")
    void multiHopCycleDetected() {
        // A -> B, B -> C, then C -> A creates a cycle
        ArtifactId a = new ArtifactId("A");
        ArtifactId b = new ArtifactId("B");
        ArtifactId c = new ArtifactId("C");
        ProvenanceEdge edge1 = new ProvenanceEdge("edge-1", "tenant-1", a, b,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-1", "res-1", NOW);
        ProvenanceEdge edge2 = new ProvenanceEdge("edge-2", "tenant-1", b, c,
                ProvenanceRelationType.GENERATED_FROM, "op-2", 1, "attempt-2",
                "req-2", "res-2", NOW);
        ProvenanceEdge edge3 = new ProvenanceEdge("edge-3", "tenant-1", c, a,
                ProvenanceRelationType.GENERATED_FROM, "op-3", 1, "attempt-3",
                "req-3", "res-3", NOW);

        ProvenanceValidator.ValidationResult result = ProvenanceValidator.validateEdge(
                edge3, List.of(edge1, edge2), Map.of("A", "tenant-1", "B", "tenant-1", "C", "tenant-1"));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("CYCLE"));
    }

    @Test
    @DisplayName("Acyclic graph is accepted")
    void acyclicGraphAccepted() {
        // A -> B, A -> C, B -> D, C -> D (diamond — no cycle)
        ArtifactId a = new ArtifactId("A");
        ArtifactId b = new ArtifactId("B");
        ArtifactId c = new ArtifactId("C");
        ArtifactId d = new ArtifactId("D");
        ProvenanceEdge edge1 = new ProvenanceEdge("edge-1", "tenant-1", a, b,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-1", "res-1", NOW);
        ProvenanceEdge edge2 = new ProvenanceEdge("edge-2", "tenant-1", a, c,
                ProvenanceRelationType.GENERATED_FROM, "op-2", 1, "attempt-2",
                "req-2", "res-2", NOW);
        ProvenanceEdge edge3 = new ProvenanceEdge("edge-3", "tenant-1", b, d,
                ProvenanceRelationType.GENERATED_FROM, "op-3", 1, "attempt-3",
                "req-3", "res-3", NOW);
        ProvenanceEdge edge4 = new ProvenanceEdge("edge-4", "tenant-1", c, d,
                ProvenanceRelationType.GENERATED_FROM, "op-4", 1, "attempt-4",
                "req-4", "res-4", NOW);

        assertThat(ProvenanceValidator.isAcyclic(List.of(edge1, edge2, edge3, edge4))).isTrue();
    }

    @Test
    @DisplayName("Graph with cycle is detected by isAcyclic")
    void graphWithCycleDetected() {
        // A -> B, B -> C, C -> A
        ArtifactId a = new ArtifactId("A");
        ArtifactId b = new ArtifactId("B");
        ArtifactId c = new ArtifactId("C");
        ProvenanceEdge edge1 = new ProvenanceEdge("edge-1", "tenant-1", a, b,
                ProvenanceRelationType.GENERATED_FROM, "op-1", 1, "attempt-1",
                "req-1", "res-1", NOW);
        ProvenanceEdge edge2 = new ProvenanceEdge("edge-2", "tenant-1", b, c,
                ProvenanceRelationType.GENERATED_FROM, "op-2", 1, "attempt-2",
                "req-2", "res-2", NOW);
        ProvenanceEdge edge3 = new ProvenanceEdge("edge-3", "tenant-1", c, a,
                ProvenanceRelationType.GENERATED_FROM, "op-3", 1, "attempt-3",
                "req-3", "res-3", NOW);

        assertThat(ProvenanceValidator.isAcyclic(List.of(edge1, edge2, edge3))).isFalse();
    }
}
