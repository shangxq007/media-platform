package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "字幕模板渲染响应")
public record CaptionTemplateRenderApiResponse(
        @Schema(description = "渲染作业ID") String renderJobId,
        @Schema(description = "输出Product ID") String outputProductId,
        @Schema(description = "状态") String status,
        @Schema(description = "是否就绪") boolean ready,
        @Schema(description = "输出配置") CaptionOutputProfileDto outputProfile,
        @Schema(description = "验证错误") List<CaptionTemplateValidationErrorDto> validationErrors,
        @Schema(description = "消息") String message) {

    public static CaptionTemplateRenderApiResponse success(
            String renderJobId, String outputProductId,
            CaptionOutputProfileDto profile) {
        return new CaptionTemplateRenderApiResponse(
                renderJobId, outputProductId, "READY", true,
                profile, List.of(), "Caption template render completed");
    }

    public static CaptionTemplateRenderApiResponse validationFailed(
            List<CaptionTemplateValidationErrorDto> errors) {
        return new CaptionTemplateRenderApiResponse(
                null, null, "VALIDATION_FAILED", false,
                null, errors, "Caption template render request is invalid");
    }

    public static CaptionTemplateRenderApiResponse failed(String message) {
        return new CaptionTemplateRenderApiResponse(
                null, null, "FAILED", false,
                null, List.of(), message);
    }
}
