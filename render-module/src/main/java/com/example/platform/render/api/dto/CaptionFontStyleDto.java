package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "字幕字体样式")
public record CaptionFontStyleDto(
        @Schema(description = "字体名称", example = "Inter") String family,
        @Schema(description = "字重", example = "600") Integer weight,
        @Schema(description = "文字颜色(HEX)", example = "#FFFFFF") String color,
        @Schema(description = "描边颜色(HEX)", example = "#000000") String outlineColor,
        @Schema(description = "描边宽度", example = "2") Integer outlineWidth,
        @Schema(description = "背景颜色(HEX)") String backgroundColor) {}
