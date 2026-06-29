package com.example.platform.render.domain.timeline.render.transition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single FFmpeg baseline transition operation in a plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionOperation(
        FFmpegBaselineTransitionOperationId id,
        FFmpegBaselineTransitionOperationType type,
        FFmpegBaselineTransitionOperationTarget target,
        List<FFmpegBaselineTransitionOperationParameter> parameters,
        FFmpegBaselineTransitionOperationSource source,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionOperation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(source, "source must not be null");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
