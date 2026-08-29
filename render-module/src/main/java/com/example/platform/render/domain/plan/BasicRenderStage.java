package com.example.platform.render.domain.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A render stage containing ordered steps.
 * Immutable. Internal domain model.
 */
public record BasicRenderStage(
        BasicRenderStageId id,
        BasicRenderStageType type,
        BasicRenderStageStatus status,
        List<BasicRenderStep> steps,
        Map<String, String> safeMetadata
) {
    public BasicRenderStage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        steps = steps == null ? List.of() : List.copyOf(steps);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
