package com.example.platform.render.domain.plan;

import com.example.platform.render.domain.interchange.TimelineSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Request for basic render planning.
 * Immutable. Internal domain model.
 */
public record BasicRenderPlanningRequest(
        BasicRenderPlanningRequestId id,
        TimelineSpec timeline,
        BasicRenderPolicy policy,
        Map<String, String> safeMetadata
) {
    public BasicRenderPlanningRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
