package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Request to commit a new Artifact.
 *
 * <p>Contains all information needed for atomic creation of an Artifact,
 * its replica binding, provenance edges, and idempotency record.
 */
public record ArtifactCommitRequest(
        ArtifactId artifactId,
        String tenantId,
        ContentDigest contentDigest,
        long byteLength,
        ArtifactMediaType mediaType,
        ArtifactKind artifactKind,
        int schemaVersion,
        StorageObjectId storageObjectId,
        StorageReplicaId storageReplicaId,
        StorageProviderId providerId,
        ReplicaRole replicaRole,
        String region,
        String idempotencyKey,
        List<ProvenanceEdgeDeclaration> provenanceDeclarations,
        Instant evaluatedAt,
        Instant createdAt,
        // GCR-2: nullable provenance/association trace (render_job_id / project_id
        // on the canonical record). NOT identity — never used for lookup/equality.
        String renderJobId,
        String projectId
) {
    public ArtifactCommitRequest {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (byteLength < 0) throw new IllegalArgumentException("byteLength must be non-negative");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(artifactKind, "artifactKind");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
        Objects.requireNonNull(storageObjectId, "storageObjectId");
        Objects.requireNonNull(storageReplicaId, "storageReplicaId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(replicaRole, "replicaRole");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        provenanceDeclarations = provenanceDeclarations != null ? List.copyOf(provenanceDeclarations) : List.of();
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        // Trace fields are nullable by contract (render_job_id/project_id association only).
    }

    /**
     * Declaration of a provenance relationship to create alongside the artifact.
     */
    public record ProvenanceEdgeDeclaration(
            ArtifactId parentArtifactId,
            ProvenanceRelationType relationType,
            String operationId,
            int operationVersion,
            String attemptId,
            String requestDigest,
            String resultDigest
    ) {
        public ProvenanceEdgeDeclaration {
            Objects.requireNonNull(parentArtifactId, "parentArtifactId");
            Objects.requireNonNull(relationType, "relationType");
            // Operation fields are deliberately retained as declared here. The canonical
            // commit boundary validates them through ProvenanceValidator so malformed
            // provenance is reported with ARTIFACT_PROVENANCE_OPERATION_INVALID rather
            // than escaping as IllegalArgumentException during request construction.
        }
    }
}
