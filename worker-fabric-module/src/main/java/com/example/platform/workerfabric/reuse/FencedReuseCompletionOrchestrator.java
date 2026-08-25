package com.example.platform.workerfabric.reuse;

import com.example.platform.workerfabric.domain.ArtifactCommitEvidence;
import com.example.platform.workerfabric.domain.CompletionAuthorityPort;
import com.example.platform.workerfabric.domain.CompletionDecision;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import java.util.Objects;

/**
 * Keeps reuse entries invisible while pending, delegates task completion to canonical fencing,
 * and activates a winner only when a matching authoritative completion exists.
 */
public final class FencedReuseCompletionOrchestrator {

    private final ArtifactReuseIndexPort index;
    private final CompletionAuthorityPort completionAuthority;

    public FencedReuseCompletionOrchestrator(
            ArtifactReuseIndexPort index,
            CompletionAuthorityPort completionAuthority) {
        this.index = Objects.requireNonNull(index, "index");
        this.completionAuthority = Objects.requireNonNull(
                completionAuthority, "completionAuthority");
    }

    public FencedReuseCompletionResult complete(
            ReusableArtifactPublication publication,
            CompletionEvidence completionEvidence,
        ArtifactCommitEvidence artifactCommitEvidence) {
        ReusePublicationResult staged = index.stageWinningPublication(publication);
        if (staged != ReusePublicationResult.STAGED_PENDING
                && staged != ReusePublicationResult.PENDING_IDEMPOTENT
                && staged != ReusePublicationResult.WINNER_IDEMPOTENT) {
            return new FencedReuseCompletionResult(staged, CompletionDecision.STALE_ATTEMPT_REJECTED);
        }
        CompletionDecision completion = completionAuthority.completeIfCurrent(
                completionEvidence, artifactCommitEvidence);
        if (completion != CompletionDecision.COMPLETED
                && completion != CompletionDecision.DUPLICATE_NOOP) {
            return new FencedReuseCompletionResult(
                    ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED,
                    completion);
        }
        ReusePublicationResult activated = index.activateWinningPublication(
                publication, completionEvidence);
        return new FencedReuseCompletionResult(activated, completion);
    }
}
