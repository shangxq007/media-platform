package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.Map;

/**
 * Clip snapshot for diff input. Internal domain model. No provider/storage fields.
 */
public record CanonicalTimelineClipSnapshot(
        String clipId,
        String assetBindingId,
        long startMs,
        long durationMs,
        long sourceStartMs,
        long sourceDurationMs,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineClipSnapshot {
        if (clipId == null || clipId.isBlank())
            throw new IllegalArgumentException("clipId must not be blank");
        if (startMs < 0) throw new IllegalArgumentException("startMs must be non-negative");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must be non-negative");
        if (sourceStartMs < 0) throw new IllegalArgumentException("sourceStartMs must be non-negative");
        if (sourceDurationMs < 0) throw new IllegalArgumentException("sourceDurationMs must be non-negative");
    }
}
