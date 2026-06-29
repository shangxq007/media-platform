package com.example.platform.render.domain.timeline.version;

/**
 * Identifier for a timeline commit.
 * Internal domain model. Distinct from diff.TimelineCommitId.
 */
public record TimelineCommitId(String value) {
    public TimelineCommitId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineCommitId must not be blank");
    }
}
