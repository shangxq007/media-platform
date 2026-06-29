package com.example.platform.render.domain.timeline.version;

import java.util.Map;

/**
 * Parent reference in a timeline commit.
 * Internal domain model. At most one primary parent.
 */
public record TimelineCommitParent(
        TimelineCommitId parentCommitId,
        boolean primaryParent,
        Map<String, String> safeMetadata) {

    public TimelineCommitParent {
        if (parentCommitId == null)
            throw new IllegalArgumentException("Parent commit ID must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineCommitParent primary(TimelineCommitId parentId) {
        return new TimelineCommitParent(parentId, true, Map.of());
    }

    public static TimelineCommitParent secondary(TimelineCommitId parentId) {
        return new TimelineCommitParent(parentId, false, Map.of());
    }
}
