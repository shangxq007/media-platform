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
        if (!publication.record().artifactPin().artifactId().equals(artifactCommitEvidence.artifactId())) {
            return new FencedReuseCompletionResult(ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED,
                    CompletionDecision.EXPECTED_OUTPUT_INVALID_REJECTED);
        }
        ReusePublicationResult staged = index.stageWinningPublication(publication);
        // Reuse is derived metadata. A different already-winning publication is retained;
        // it cannot veto a new exact current native completion or be adopted as its output.
        boolean existingWinner=false;
        if(staged==ReusePublicationResult.CONFLICT_REJECTED) {
            var old=index.lookup(publication.record().tenantId(),publication.record().executionReuseKey());
            existingWinner=old.filter(r->r.tenantId().equals(publication.record().tenantId())
                    && r.executionReuseKey().equals(publication.record().executionReuseKey())
                    && !r.artifactPin().artifactId().equals(publication.record().artifactPin().artifactId())).isPresent();
        }
        if (!existingWinner && staged != ReusePublicationResult.STAGED_PENDING
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
        if(existingWinner)return new FencedReuseCompletionResult(ReusePublicationResult.COMPLETED_WITHOUT_CACHE,completion);
        ReusePublicationResult activated = index.activateWinningPublication(
                publication, completionEvidence);
        return new FencedReuseCompletionResult(activated, completion);
    }
}
