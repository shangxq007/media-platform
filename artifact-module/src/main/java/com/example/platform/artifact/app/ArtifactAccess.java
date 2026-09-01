package com.example.platform.artifact.app;

import com.example.platform.shared.identity.ArtifactId;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Ephemeral, explicitly requested Artifact access grant. Never persisted. */
public record ArtifactAccess(ArtifactId artifactId, URI accessUrl, Instant expiresAt) {
    public ArtifactAccess {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(accessUrl, "accessUrl");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
