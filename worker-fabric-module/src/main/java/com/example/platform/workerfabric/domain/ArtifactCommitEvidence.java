package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Opaque evidence reference issued by Artifact authority after an immutable commit.
 *
 * <p>This is not an Artifact identity and cannot be manufactured from a backend output path or a
 * claim that bytes exist.
 */
public record ArtifactCommitEvidence(String authorityEvidenceReference, Instant committedAt) {

    public ArtifactCommitEvidence {
        Objects.requireNonNull(authorityEvidenceReference, "authorityEvidenceReference");
        Objects.requireNonNull(committedAt, "committedAt");
        if (authorityEvidenceReference.isBlank()) {
            throw new IllegalArgumentException("authorityEvidenceReference must not be blank");
        }
    }
}
