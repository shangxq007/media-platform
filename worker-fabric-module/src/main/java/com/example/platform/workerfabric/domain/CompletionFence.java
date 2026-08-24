package com.example.platform.workerfabric.domain;

import java.util.Objects;
import java.util.Optional;

/** Separates backend success, Artifact commit, and authoritative task completion. */
public final class CompletionFence {

    private final ArtifactCommitEvidencePort artifactCommitEvidencePort;
    private final CompletionAuthorityPort completionAuthorityPort;

    public CompletionFence(
            ArtifactCommitEvidencePort artifactCommitEvidencePort,
            CompletionAuthorityPort completionAuthorityPort) {
        this.artifactCommitEvidencePort =
                Objects.requireNonNull(artifactCommitEvidencePort, "artifactCommitEvidencePort");
        this.completionAuthorityPort =
                Objects.requireNonNull(completionAuthorityPort, "completionAuthorityPort");
    }

    public CompletionDecision tryComplete(CompletionEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.backendReportedState() != ObservedExecutionState.SUCCEEDED) {
            return CompletionDecision.BACKEND_NOT_SUCCEEDED_REJECTED;
        }
        if (!evidence.expectedOutputValidation().isValid()) {
            return CompletionDecision.EXPECTED_OUTPUT_INVALID_REJECTED;
        }
        Optional<ArtifactCommitEvidence> commitEvidence =
                artifactCommitEvidencePort.committedEvidenceFor(evidence);
        if (commitEvidence.isEmpty()) {
            return CompletionDecision.ARTIFACT_NOT_COMMITTED_REJECTED;
        }
        return completionAuthorityPort.completeIfCurrent(evidence, commitEvidence.orElseThrow());
    }
}
