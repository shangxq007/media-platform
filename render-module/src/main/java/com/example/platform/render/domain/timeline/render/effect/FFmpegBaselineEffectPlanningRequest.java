package com.example.platform.render.domain.timeline.render.effect;

import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Request for FFmpeg baseline effect planning.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectPlanningRequest(
        FFmpegBaselineEffectPlanningRequestId id,
        TimelineSpec timeline,
        FFmpegBaselineEffectPolicy policy,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineEffectPlanningRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
