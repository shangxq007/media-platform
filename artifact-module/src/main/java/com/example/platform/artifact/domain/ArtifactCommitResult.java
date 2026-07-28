package com.example.platform.artifact.domain;

import java.util.List;
import java.util.Objects;

/**
 * Result of a successful Artifact commit operation.
 */
public record ArtifactCommitResult(
        Artifact artifact,
        ArtifactReplicaBinding replicaBinding,
        List<ProvenanceEdge> provenanceEdges,
        String idempotencyKey
) {
    public ArtifactCommitResult {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(replicaBinding, "replicaBinding");
        provenanceEdges = provenanceEdges != null ? List.copyOf(provenanceEdges) : List.of();
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
