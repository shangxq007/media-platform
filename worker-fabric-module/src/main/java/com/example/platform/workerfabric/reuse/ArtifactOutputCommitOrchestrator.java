package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactState;
import java.util.Objects;

/** Gates the Artifact-owned commit boundary with the exact measured staged output. */
public final class ArtifactOutputCommitOrchestrator {

    private final ArtifactCommitService artifactCommitService;

    public ArtifactOutputCommitOrchestrator(ArtifactCommitService artifactCommitService) {
        this.artifactCommitService = Objects.requireNonNull(
                artifactCommitService, "artifactCommitService");
    }

    public ArtifactCommitResult commit(
            StagedExecutionOutput stagedOutput,
            ArtifactCommitRequest commitRequest) {
        Objects.requireNonNull(stagedOutput, "stagedOutput");
        Objects.requireNonNull(commitRequest, "commitRequest");
        if (!stagedOutput.contentDigest().matches(commitRequest.contentDigest())) {
            throw new IllegalArgumentException(
                    "Artifact commit content digest must equal staged output digest");
        }
        if (stagedOutput.byteLength() != commitRequest.byteLength()) {
            throw new IllegalArgumentException(
                    "Artifact commit byte length must equal staged output length");
        }
        ArtifactCommitResult result = artifactCommitService.commit(commitRequest);
        if (result.artifact().state() != ArtifactState.AVAILABLE
                || !result.artifact().contentDigest().matches(stagedOutput.contentDigest())
                || result.artifact().byteLength() != stagedOutput.byteLength()) {
            throw new IllegalStateException(
                    "Artifact authority commit result does not match staged output");
        }
        return result;
    }
}
