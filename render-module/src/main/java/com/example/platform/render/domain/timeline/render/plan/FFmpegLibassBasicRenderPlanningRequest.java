package com.example.platform.render.domain.timeline.render.plan;

import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Request for FFmpeg/libass basic render planning.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderPlanningRequest(
        FFmpegLibassBasicRenderPlanningRequestId id,
        TimelineSpec timeline,
        FFmpegLibassBasicRenderPolicy policy,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderPlanningRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
