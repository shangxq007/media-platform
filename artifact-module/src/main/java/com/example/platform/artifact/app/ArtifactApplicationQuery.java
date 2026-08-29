package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;
import java.util.Optional;

/** Persistence-neutral scoped query port for application Artifact discovery. */
public interface ArtifactApplicationQuery {
    List<Artifact> findArtifacts(ArtifactScope scope, int limit);

    long countArtifacts(ArtifactScope scope);

    Optional<Artifact> findArtifact(ArtifactScope scope, ArtifactId artifactId);
}
