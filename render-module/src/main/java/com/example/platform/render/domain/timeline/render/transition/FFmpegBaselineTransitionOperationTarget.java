package com.example.platform.render.domain.timeline.render.transition;

import java.util.Map;
import java.util.Objects;

/**
 * Semantic target for an FFmpeg baseline transition operation.
 * References timeline entities only — no provider/storage internals.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionOperationTarget(
        FFmpegBaselineTransitionOperationTargetType targetType,
        String fromClipId,
        String toClipId,
        String trackId,
        String timelineId,
        String transitionId,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionOperationTarget {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(fromClipId, "fromClipId must not be null");
        if (fromClipId.isBlank()) throw new IllegalArgumentException("fromClipId must not be blank");
        Objects.requireNonNull(toClipId, "toClipId must not be null");
        if (toClipId.isBlank()) throw new IllegalArgumentException("toClipId must not be blank");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
