package com.example.platform.render.domain.timeline.render.transition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FFmpeg baseline transition plan.
 * Pure, side-effect-free. Internal domain model.
 *
 * <p>Contains only internal operation vocabulary — no raw FFmpeg xfade strings,
 * no filter_complex, no shell commands, no provider-specific parameters.</p>
 */
public record FFmpegBaselineTransitionPlan(
        FFmpegBaselineTransitionPlanId id,
        FFmpegBaselineTransitionPlanStatus status,
        List<FFmpegBaselineTransitionOperation> operations,
        FFmpegBaselineTransitionPlanSummary summary,
        List<FFmpegBaselineTransitionPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        operations = operations == null ? List.of() : List.copyOf(operations);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
