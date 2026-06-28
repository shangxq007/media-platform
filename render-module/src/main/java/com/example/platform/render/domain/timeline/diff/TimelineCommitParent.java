package com.example.platform.render.domain.timeline.diff;

/**
 * Parent reference in a timeline commit.
 * Internal domain model.
 */
public record TimelineCommitParent(
        TimelineCommitId parentId,
        String relationship) {

    public TimelineCommitParent {
        if (parentId == null) throw new IllegalArgumentException("Parent commit ID must not be null");
    }

    public static TimelineCommitParent primary(TimelineCommitId parentId) {
        return new TimelineCommitParent(parentId, "PRIMARY");
    }

    public static TimelineCommitParent mergeFrom(TimelineCommitId parentId) {
        return new TimelineCommitParent(parentId, "MERGE_FROM");
    }
}
