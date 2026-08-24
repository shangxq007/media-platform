package com.example.platform.workerfabric.domain;

import java.util.Optional;

/**
 * Artifact-authority query seam used by the completion fence.
 *
 * <p>Implementations may return evidence only after authoritative immutable commit. Merely finding
 * backend bytes must return an empty result.
 */
@FunctionalInterface
public interface ArtifactCommitEvidencePort {

    Optional<ArtifactCommitEvidence> committedEvidenceFor(CompletionEvidence completionEvidence);
}
