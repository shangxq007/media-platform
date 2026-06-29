package com.example.platform.render.domain.timeline.render.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FFmpeg/libass basic timeline render plan.
 * Pure, side-effect-free. Internal domain model.
 *
 * <p>Contains only internal stage/step vocabulary — no raw FFmpeg commands,
 * no shell commands, no provider-specific parameters.</p>
 */
public record FFmpegLibassBasicRenderPlan(
        FFmpegLibassBasicRenderPlanId id,
        FFmpegLibassBasicRenderPlanStatus status,
        List<FFmpegLibassBasicRenderStage> stages,
        FFmpegLibassBasicRenderPlanSummary summary,
        List<FFmpegLibassBasicRenderPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        stages = stages == null ? List.of() : List.copyOf(stages);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
