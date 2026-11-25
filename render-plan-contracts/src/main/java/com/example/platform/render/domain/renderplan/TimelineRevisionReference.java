package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import java.util.Objects;

/**
 * Typed reference to the immutable TimelineRevision a RenderPlan is derived from
 * (C7). The revision id and its content digest are the plan-level revision
 * context. revisionId is non-blank.
 *
 * @param revisionId      non-blank revision id
 * @param contentDigest   the revision's content digest (pinned immutable semantics)
 */
public record TimelineRevisionReference(String revisionId, ContentDigest contentDigest) {

    public TimelineRevisionReference {
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (revisionId.isBlank()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
    }
}
