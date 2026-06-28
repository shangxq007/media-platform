package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.Map;

/**
 * Output profile snapshot for diff input. Internal domain model.
 */
public record CanonicalTimelineOutputProfileSnapshot(
        String profileId,
        String format,
        String aspectRatio,
        int width,
        int height,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineOutputProfileSnapshot {
        if (width < 0) throw new IllegalArgumentException("width must be non-negative");
        if (height < 0) throw new IllegalArgumentException("height must be non-negative");
    }
}
