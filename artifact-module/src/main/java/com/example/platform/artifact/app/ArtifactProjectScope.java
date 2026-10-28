package com.example.platform.artifact.app;

/** Logical project ownership for committed Artifacts, independent of their producing domain. */
public record ArtifactProjectScope(String tenantId, String projectId) {
    public ArtifactProjectScope {
        if (tenantId == null || tenantId.isBlank() || projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("tenant and project required");
        }
    }
}
