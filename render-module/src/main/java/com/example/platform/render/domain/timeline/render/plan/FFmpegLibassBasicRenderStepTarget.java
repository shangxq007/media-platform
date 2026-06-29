package com.example.platform.render.domain.timeline.render.plan;

import java.util.Map;
import java.util.Objects;

/**
 * Semantic target for a render step.
 * References timeline entities only — no provider/storage internals.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderStepTarget(
        FFmpegLibassBasicRenderStepTargetType targetType,
        String targetId,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderStepTarget {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        if (targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
