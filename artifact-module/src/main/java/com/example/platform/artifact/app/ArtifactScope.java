package com.example.platform.artifact.app;

import java.util.Objects;

/**
 * Explicit application discovery scope for Artifact reads.
 *
 * <p>The scope is deliberately tenant + project + producing render job. It does
 * not infer any of those identities from a storage location.</p>
 */
public record ArtifactScope(String tenantId, String projectId, String renderJobId) {

    public ArtifactScope {
        requireText(tenantId, "tenantId");
        requireText(projectId, "projectId");
        requireText(renderJobId, "renderJobId");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
