package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.ArtifactCommitRequest.ProvenanceEdgeDeclaration;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Artifact metadata supplied before output bytes exist; it contains no storage identity. */
public record ArtifactCommitMetadata(
        ArtifactId artifactId,
        String tenantId,
        ArtifactMediaType mediaType,
        ArtifactKind artifactKind,
        int schemaVersion,
        ReplicaRole replicaRole,
        String region,
        List<ProvenanceEdgeDeclaration> provenanceDeclarations,
        Instant evaluatedAt,
        Instant createdAt,
        String renderJobId,
        String projectId) {

    public ArtifactCommitMetadata {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(artifactKind, "artifactKind");
        Objects.requireNonNull(replicaRole, "replicaRole");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(provenanceDeclarations, "provenanceDeclarations");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (tenantId.isBlank() || schemaVersion < 1) {
            throw new IllegalArgumentException("tenantId and positive schemaVersion are required");
        }
        provenanceDeclarations = List.copyOf(provenanceDeclarations);
    }
}
