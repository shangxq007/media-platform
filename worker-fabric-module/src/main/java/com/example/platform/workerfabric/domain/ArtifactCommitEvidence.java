package com.example.platform.workerfabric.domain;

import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/** Exact immutable Artifact identity returned by the Artifact commit authority. */
public record ArtifactCommitEvidence(ArtifactId artifactId) {
    public ArtifactCommitEvidence {
        Objects.requireNonNull(artifactId, "artifactId");
    }
}
