package com.example.platform.artifact.domain.typed;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fail-closed identity checks shared by conversion and retrieval boundaries. */
public final class ArtifactIdentityGuard {
    private ArtifactIdentityGuard() {}

    public static List<TypedArtifactValidationError> validateUniqueAndScoped(
            List<TypedArtifact> artifacts, String tenantId, String workspaceId, String location) {
        if (artifacts == null) return List.of(new TypedArtifactValidationError("ARTIFACT_FACTS_MISSING", location, "artifact facts are required"));
        Set<String> ids = new HashSet<>();
        java.util.ArrayList<TypedArtifactValidationError> errors = new java.util.ArrayList<>();
        for (int i = 0; i < artifacts.size(); i++) {
            TypedArtifact artifact = artifacts.get(i);
            String at = location + "[" + i + "]";
            if (artifact == null) { errors.add(new TypedArtifactValidationError("ARTIFACT_FACTS_MISSING", at, "artifact facts are required")); continue; }
            if (!ids.add(artifact.artifactId())) errors.add(new TypedArtifactValidationError("DUPLICATE_ARTIFACT_ID", at + ".artifactId", "artifact identity is duplicated"));
            if (!java.util.Objects.equals(tenantId, artifact.tenantId())) errors.add(new TypedArtifactValidationError("CROSS_TENANT_REFERENCE", at + ".tenantId", "tenant mismatch"));
            if (!java.util.Objects.equals(workspaceId, artifact.workspaceId())) errors.add(new TypedArtifactValidationError("CROSS_WORKSPACE_REFERENCE", at + ".workspaceId", "workspace mismatch"));
            if (artifact.storageReference() == null || artifact.storageReference().isBlank()) errors.add(new TypedArtifactValidationError("STORAGE_FACTS_MISSING", at + ".storageReference", "storage reference is required"));
        }
        return List.copyOf(errors);
    }
}
