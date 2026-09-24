package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Optional;

/** Owner-provided workspace access query for Composition and other platform consumers. */
public interface ArtifactWorkspaceAccessQuery {
    Optional<Artifact> findArtifact(String tenantId, String workspaceId, ArtifactId artifactId);
}
