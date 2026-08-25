package com.example.platform.workerfabric.reuse;

import java.util.Objects;

/** Durable Artifact exists, but fencing rejected completion; reuse remains non-winning. */
public final class NonAuthoritativeRuntimeCompletionException extends RuntimeException {

    private final DurableArtifactCommitResult durableArtifactCommit;
    private final FencedReuseCompletionResult fencedCompletion;

    public NonAuthoritativeRuntimeCompletionException(
            DurableArtifactCommitResult durableArtifactCommit,
            FencedReuseCompletionResult fencedCompletion) {
        super("fenced completion was not authoritative after durable Artifact commit");
        this.durableArtifactCommit = Objects.requireNonNull(
                durableArtifactCommit, "durableArtifactCommit");
        this.fencedCompletion = Objects.requireNonNull(fencedCompletion, "fencedCompletion");
    }

    public DurableArtifactCommitResult durableArtifactCommit() {
        return durableArtifactCommit;
    }

    public FencedReuseCompletionResult fencedCompletion() {
        return fencedCompletion;
    }
}
