package com.example.platform.render.domain.timeline.render.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A render stage containing ordered steps.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderStage(
        FFmpegLibassBasicRenderStageId id,
        FFmpegLibassBasicRenderStageType type,
        FFmpegLibassBasicRenderStageStatus status,
        List<FFmpegLibassBasicRenderStep> steps,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderStage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        steps = steps == null ? List.of() : List.copyOf(steps);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
