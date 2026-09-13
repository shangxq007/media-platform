package com.example.platform.artifact.app;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;
/** Scoped identity of an output accepted by Artifact. Never physical coordinates. */
public record ArtifactOutputReference(ArtifactScope scope, ArtifactId artifactId) {
    public ArtifactOutputReference { Objects.requireNonNull(scope); Objects.requireNonNull(artifactId); }
}
