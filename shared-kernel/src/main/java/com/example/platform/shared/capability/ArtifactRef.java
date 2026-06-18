package com.example.platform.shared.capability;

/**
 * Represents a reference to a platform artifact.
 *
 * <p>ArtifactRef is a reference only - it contains the logical location,
 * not the raw file path or content.</p>
 *
 * <p><strong>Contract only:</strong> This defines the artifact reference shape.
 * No artifact retrieval is implemented.</p>
 */
public record ArtifactRef(
    String artifactId,
    String tenantId,
    String mediaType,
    String contentHash,
    String storageUri,
    String logicalUri,
    ArtifactPermissions permissions
) {
    public record ArtifactPermissions(
        boolean readable,
        boolean writable,
        boolean executable
    ) {}
}
