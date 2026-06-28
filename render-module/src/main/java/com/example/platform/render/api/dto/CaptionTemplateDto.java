package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "字幕模板")
public record CaptionTemplateDto(
        @Schema(description = "模板ID") String templateId,
        @Schema(description = "模板名称", example = "Basic Caption") String name,
        @Schema(description = "样式") CaptionTemplateStyleDto style) {}
