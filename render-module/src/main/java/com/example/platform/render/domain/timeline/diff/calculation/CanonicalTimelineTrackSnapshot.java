package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.List;
import java.util.Map;

/**
 * Track snapshot for diff input. Internal domain model.
 */
public record CanonicalTimelineTrackSnapshot(
        String trackId,
        int order,
        String kind,
        List<CanonicalTimelineClipSnapshot> clips,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineTrackSnapshot {
        if (trackId == null || trackId.isBlank())
            throw new IllegalArgumentException("trackId must not be blank");
        if (order < 0) throw new IllegalArgumentException("order must be non-negative");
    }
}
