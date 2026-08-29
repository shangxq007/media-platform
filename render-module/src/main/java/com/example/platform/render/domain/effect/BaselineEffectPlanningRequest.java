package com.example.platform.render.domain.effect;

import com.example.platform.render.domain.interchange.TimelineSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Request for baseline effect planning.
 * Immutable. Internal domain model.
 */
public record BaselineEffectPlanningRequest(
        BaselineEffectPlanningRequestId id,
        TimelineSpec timeline,
        BaselineEffectPolicy policy,
        Map<String, String> safeMetadata
) {
    public BaselineEffectPlanningRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
