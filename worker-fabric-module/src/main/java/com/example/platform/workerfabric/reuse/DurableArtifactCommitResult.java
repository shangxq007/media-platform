package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.ArtifactCommitResult;
import java.util.Objects;

/** Durable storage identity bound to the authoritative Artifact commit result. */
public record DurableArtifactCommitResult(
        DurableStoragePublication storagePublication,
        ArtifactCommitResult artifactCommitResult) {

    public DurableArtifactCommitResult {
        Objects.requireNonNull(storagePublication, "storagePublication");
        Objects.requireNonNull(artifactCommitResult, "artifactCommitResult");
    }
}
