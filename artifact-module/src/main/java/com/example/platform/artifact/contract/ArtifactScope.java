package com.example.platform.artifact.contract;

import java.util.Objects;

/** Mandatory authorization scope carried by every Artifact-native feature contract. */
public record ArtifactScope(String tenantId, String workspaceId) {
    public ArtifactScope {
        requireText(tenantId, "tenantId");
        requireText(workspaceId, "workspaceId");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new ArtifactContractException(
                ArtifactContractErrorCode.MISSING_REQUIRED_FACT, name + " is required");
    }

    public String canonicalForm() { return "tenant=" + tenantId + ",workspace=" + workspaceId; }
}
