package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "字幕模板样式")
public record CaptionTemplateStyleDto(
        @Schema(description = "位置", example = "BOTTOM_CENTER") String placement,
        @Schema(description = "字体样式") CaptionFontStyleDto font,
        @Schema(description = "字号", example = "48") Integer fontSize,
        @Schema(description = "最大行数", example = "2") Integer maxLines,
        @Schema(description = "行高", example = "1.2") Double lineHeight,
        @Schema(description = "对齐方式", example = "CENTER") String textAlign) {}
