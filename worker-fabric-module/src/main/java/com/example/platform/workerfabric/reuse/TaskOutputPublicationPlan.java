package com.example.platform.workerfabric.reuse;

import java.util.Objects;

/** The complete publication intent for one task output: where it lands and what it becomes. */
public record TaskOutputPublicationPlan(
        DurableOutputTarget durableOutputTarget,
        ArtifactCommitMetadata artifactCommitMetadata) {

    public TaskOutputPublicationPlan {
        Objects.requireNonNull(durableOutputTarget, "durableOutputTarget");
        Objects.requireNonNull(artifactCommitMetadata, "artifactCommitMetadata");
    }
}
