package com.example.platform.render.api.dto;

import com.example.platform.render.domain.timeline.internal.ReusableArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "预览增量渲染计划（语义 Diff + reuse/execute）")
public record GenerateIncrementalPlanRequest(
        @NotBlank @Schema(description = "新 Internal Timeline 1.0 JSON") String newTimelineJson,
        @Schema(description = "旧 1.0 JSON；省略时从 baseJobId 加载") String oldTimelineJson,
        @Schema(example = "default_1080p") String profile,
        @Schema(example = "PRO") String tier,
        @Schema(example = "mp4") String outputFormat,
        @Schema(description = "基准已完成作业 ID") String baseJobId,
        @Schema(description = "显式可复用产物（可选）") List<ReusableArtifact> reuseArtifacts) {

    public String profileOrDefault() {
        return profile == null || profile.isBlank() ? "default_1080p" : profile;
    }

    public String tierOrDefault() {
        return tier == null || tier.isBlank() ? "PRO" : tier;
    }

    public String outputFormatOrDefault() {
        return outputFormat == null || outputFormat.isBlank() ? "mp4" : outputFormat;
    }
}
