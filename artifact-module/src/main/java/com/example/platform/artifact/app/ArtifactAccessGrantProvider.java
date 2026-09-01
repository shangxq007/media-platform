package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * Storage-mechanics port used only after Artifact scope and lifecycle checks.
 * Implementations may issue a signed URL but never return raw coordinates.
 */
public interface ArtifactAccessGrantProvider {
    Optional<Grant> grant(Artifact artifact, ArtifactReplicaBinding replica);

    record Grant(URI accessUrl, Instant expiresAt) {}
}
