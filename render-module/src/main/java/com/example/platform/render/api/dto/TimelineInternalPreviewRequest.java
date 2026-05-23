package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "将编辑器/遗留时间线预览为 Internal Timeline 1.0")
public record TimelineInternalPreviewRequest(
        @NotBlank @Schema(description = "源时间线 JSON（编辑器 2.0、OTIO 或已是 1.0）") String timelineJson) {}
