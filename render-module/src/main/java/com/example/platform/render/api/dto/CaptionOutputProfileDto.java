package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "输出配置")
public record CaptionOutputProfileDto(
        @Schema(description = "宽度", example = "1920") Integer width,
        @Schema(description = "高度", example = "1080") Integer height,
        @Schema(description = "帧率", example = "30") Double fps,
        @Schema(description = "容器格式", example = "mp4") String container) {}
