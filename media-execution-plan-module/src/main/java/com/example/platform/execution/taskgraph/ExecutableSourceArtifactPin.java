package com.example.platform.execution.taskgraph;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/** Neutral execution-plan projection of an immutable source Artifact pin. */
public record ExecutableSourceArtifactPin(ArtifactId artifactId, ContentDigest contentDigest) {
    public ExecutableSourceArtifactPin {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(contentDigest, "contentDigest");
    }
}
