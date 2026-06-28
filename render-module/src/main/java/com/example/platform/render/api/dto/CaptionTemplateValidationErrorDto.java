package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "验证错误")
public record CaptionTemplateValidationErrorDto(
        @Schema(description = "字段", example = "captionSegments[0].text") String field,
        @Schema(description = "错误码", example = "TEXT_BLANK") String code,
        @Schema(description = "消息", example = "Caption text must not be blank") String message) {}
