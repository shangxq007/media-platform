package com.example.platform.workerfabric.reuse;

import java.util.Objects;

/** Successful durable commit and fenced reuse publication for one executed task. */
public record RuntimeClosedLoopTaskResult(
        DurableArtifactCommitResult durableArtifactCommit,
        FencedReuseCompletionResult fencedCompletion) {

    public RuntimeClosedLoopTaskResult {
        Objects.requireNonNull(durableArtifactCommit, "durableArtifactCommit");
        Objects.requireNonNull(fencedCompletion, "fencedCompletion");
    }
}
