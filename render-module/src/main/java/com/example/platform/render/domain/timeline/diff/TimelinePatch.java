package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Ordered set of timeline change operations with merge policy.
 * Internal domain model. Does not apply itself.
 */
public record TimelinePatch(
        TimelinePatchId id,
        String baseRevisionId,
        List<TimelineChangeOperation> operations,
        TimelineMergePolicy mergePolicy,
        Map<String, String> safeMetadata) {

    public TimelinePatch {
        if (id == null) throw new IllegalArgumentException("Patch ID must not be null");
        if (baseRevisionId == null || baseRevisionId.isBlank())
            throw new IllegalArgumentException("Base revision ID must not be blank");
    }
}
