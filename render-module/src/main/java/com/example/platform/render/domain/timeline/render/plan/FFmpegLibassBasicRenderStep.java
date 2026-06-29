package com.example.platform.render.domain.timeline.render.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single render step in a basic render plan.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderStep(
        FFmpegLibassBasicRenderStepId id,
        FFmpegLibassBasicRenderStepType type,
        FFmpegLibassBasicRenderStepTarget target,
        List<FFmpegLibassBasicRenderStepParameter> parameters,
        FFmpegLibassBasicRenderStepSource source,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderStep {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(source, "source must not be null");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
