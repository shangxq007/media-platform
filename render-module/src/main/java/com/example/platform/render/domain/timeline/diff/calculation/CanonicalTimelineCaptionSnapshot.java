package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.Map;

/**
 * Caption snapshot for diff input. Internal domain model.
 */
public record CanonicalTimelineCaptionSnapshot(
        String captionId,
        long startMs,
        long endMs,
        String text,
        Map<String, String> style,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineCaptionSnapshot {
        if (captionId == null || captionId.isBlank())
            throw new IllegalArgumentException("captionId must not be blank");
        if (startMs < 0) throw new IllegalArgumentException("startMs must be non-negative");
        if (endMs < startMs) throw new IllegalArgumentException("endMs must be >= startMs");
    }
}
