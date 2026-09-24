package com.example.platform.artifact.domain.typed;

import com.example.platform.shared.digest.ContentDigest;
import java.time.Instant;
import java.util.Objects;

/** Platform-owned typed view over the existing Artifact catalog identity. */
public record TypedArtifact(String artifactId, String tenantId, String workspaceId, LogicalArtifactKind kind,
                            String containerFormat, String mimeType, String encoding, String schemaVersion,
                            ArtifactTechnicalProperties technical, ContentDigest contentDigest,
                            String storageReference, String sourceReference, String lineageReference, Instant createdAt) {
    public TypedArtifact {
        require(artifactId, "artifactId"); require(tenantId, "tenantId"); require(workspaceId, "workspaceId");
        Objects.requireNonNull(kind, "kind"); require(mimeType, "mimeType"); Objects.requireNonNull(contentDigest, "contentDigest");
        require(storageReference, "storageReference"); Objects.requireNonNull(createdAt, "createdAt");
        if (schemaVersion != null && schemaVersion.isBlank()) throw new IllegalArgumentException("schemaVersion must not be blank");
        if (sourceReference != null && sourceReference.isBlank() || lineageReference != null && lineageReference.isBlank()) throw new IllegalArgumentException("lineage reference must not be blank");
    }
    private static void require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); }
}
