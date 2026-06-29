package com.example.platform.render.domain.timeline.render.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FFmpeg baseline effect plan.
 * Pure, side-effect-free. Internal domain model.
 *
 * <p>Contains only internal operation vocabulary — no raw FFmpeg filtergraphs,
 * no shell commands, no provider-specific parameters.</p>
 */
public record FFmpegBaselineEffectPlan(
        FFmpegBaselineEffectPlanId id,
        FFmpegBaselineEffectPlanStatus status,
        List<FFmpegBaselineEffectOperation> operations,
        FFmpegBaselineEffectPlanSummary summary,
        List<FFmpegBaselineEffectPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineEffectPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        operations = operations == null ? List.of() : List.copyOf(operations);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
