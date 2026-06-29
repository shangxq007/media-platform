package com.example.platform.render.domain.timeline.render.transition;

import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Request for FFmpeg baseline transition planning.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionPlanningRequest(
        FFmpegBaselineTransitionPlanningRequestId id,
        TimelineSpec timeline,
        FFmpegBaselineTransitionPolicy policy,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionPlanningRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
