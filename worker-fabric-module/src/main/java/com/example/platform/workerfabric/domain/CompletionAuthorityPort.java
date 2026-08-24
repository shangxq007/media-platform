package com.example.platform.workerfabric.domain;

/**
 * Canonical database completion boundary.
 *
 * <p>The implementation must lock or compare-and-set the task ownership row and atomically re-check
 * the exact current attempt, current generation, expected task, output validation, and Artifact
 * commit evidence before transitioning the executable task. It must deduplicate by completion event
 * in that same transaction. Backend success and delivery state are never task authority.
 */
@FunctionalInterface
public interface CompletionAuthorityPort {

    CompletionDecision completeIfCurrent(
            CompletionEvidence completionEvidence,
            ArtifactCommitEvidence artifactCommitEvidence);
}
