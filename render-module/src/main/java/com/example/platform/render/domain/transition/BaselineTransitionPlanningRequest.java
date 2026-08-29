package com.example.platform.render.domain.transition;

import com.example.platform.render.domain.interchange.TimelineSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Request for baseline transition planning.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionPlanningRequest(
        BaselineTransitionPlanningRequestId id,
        TimelineSpec timeline,
        BaselineTransitionPolicy policy,
        Map<String, String> safeMetadata
) {
    public BaselineTransitionPlanningRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
