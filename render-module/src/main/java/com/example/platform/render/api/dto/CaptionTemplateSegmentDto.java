package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "字幕片段")
public record CaptionTemplateSegmentDto(
        @NotNull @Min(0) @Schema(description = "开始时间(ms)", example = "0") Long startMs,
        @NotNull @Min(0) @Schema(description = "结束时间(ms)", example = "2500") Long endMs,
        @NotBlank @Schema(description = "字幕文本", example = "Hello world") String text) {}
