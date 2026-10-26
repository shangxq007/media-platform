package com.example.platform.artifact.app;

import com.example.platform.shared.identity.ArtifactId;
import java.util.Optional;

/** Implemented by Render's owner; Artifact never reads Render tables or infers the final output. */
public interface AcceptedRenderOutputQuery {
    Optional<ArtifactId> find(ArtifactScope scope);
}
