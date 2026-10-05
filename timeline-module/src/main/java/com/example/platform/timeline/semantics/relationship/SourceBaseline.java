package com.example.platform.timeline.semantics.relationship;

import java.util.Objects;

/**
 * L5 lineage (Gate 7): provenance binding of an alignment's source side — the source revision
 * identity plus the canonical Timeline content digest of that revision.
 *
 * <p>Provenance, NOT intent: this never appears in an Operation parameter (owner decision 4).
 * The digest is the canonical Timeline content hash string (the same shape persisted as the
 * revision content hash and used by {@code TimelineClip.contentDigest}), so a baseline binds
 * exactly to the immutable source revision content.
 */
public record SourceBaseline(String sourceRevisionId, String sourceContentDigest) {

    public SourceBaseline {
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(sourceContentDigest, "sourceContentDigest");
        if (sourceRevisionId.isBlank()) {
            throw new IllegalArgumentException("SourceBaseline.sourceRevisionId must not be blank");
        }
        if (sourceContentDigest.isBlank()) {
            throw new IllegalArgumentException("SourceBaseline.sourceContentDigest must not be blank");
        }
    }
}
