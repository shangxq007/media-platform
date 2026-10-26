package com.example.platform.artifact.app;

import com.example.platform.shared.identity.ArtifactId;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Authenticated relative download route. No bearer credential or signed URL; no independent expiry. */
public record ArtifactAccess(ArtifactId artifactId, URI accessUrl, Instant expiresAt) {
    public ArtifactAccess {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(accessUrl, "accessUrl");
        if(accessUrl.isAbsolute() || !accessUrl.getPath().startsWith("/api/"))throw new IllegalArgumentException("authenticated local API route required");
    }
}
