package com.example.platform.artifact.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable edge in the Artifact provenance graph.
 *
 * <p>Represents a single derivation relationship: childArtifactId was derived from
 * parentArtifactId via relationType as part of operationId (version operationVersion).
 *
 * <p>Edges are immutable and unique by edgeId. Duplicate semantic edges
 * (same parent, child, relationType, operationId, attemptId) are prohibited.
 */
public record ProvenanceEdge(
        String edgeId,
        String tenantId,
        ArtifactId parentArtifactId,
        ArtifactId childArtifactId,
        ProvenanceRelationType relationType,
        String operationId,
        int operationVersion,
        String attemptId,
        String requestDigest,
        String resultDigest,
        Instant createdAt
) implements Serializable {

    public ProvenanceEdge {
        Objects.requireNonNull(edgeId, "edgeId");
        if (edgeId.isBlank()) throw new IllegalArgumentException("edgeId must not be blank");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        Objects.requireNonNull(parentArtifactId, "parentArtifactId");
        Objects.requireNonNull(childArtifactId, "childArtifactId");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(operationId, "operationId");
        if (operationId.isBlank()) throw new IllegalArgumentException("operationId must not be blank");
        if (operationVersion < 1) throw new IllegalArgumentException("operationVersion must be >= 1");
        Objects.requireNonNull(attemptId, "attemptId");
        if (attemptId.isBlank()) throw new IllegalArgumentException("attemptId must not be blank");
        Objects.requireNonNull(requestDigest, "requestDigest");
        Objects.requireNonNull(resultDigest, "resultDigest");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Canonical serialization with deterministic field ordering.
     */
    public String canonicalForm() {
        return "edge{" +
                "id=" + edgeId +
                ",tenant=" + tenantId +
                ",parent=" + parentArtifactId.value() +
                ",child=" + childArtifactId.value() +
                ",relation=" + relationType.name() +
                ",operation=" + operationId +
                ",version=" + operationVersion +
                ",attempt=" + attemptId +
                ",requestDigest=" + requestDigest +
                ",resultDigest=" + resultDigest +
                ",created=" + createdAt.toString() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
