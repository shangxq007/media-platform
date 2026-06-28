package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "字幕模板渲染结果查询响应")
public record CaptionTemplateRenderResultLookupResponse(
        @Schema(description = "输出Product ID") String outputProductId,
        @Schema(description = "交付状态") CaptionTemplateDeliveryStatus status,
        @Schema(description = "是否就绪") boolean ready,
        @Schema(description = "Product类型") String productType,
        @Schema(description = "是否可下载") boolean downloadAvailable,
        @Schema(description = "是否可预览") boolean previewAvailable,
        @Schema(description = "交付模式") String deliveryMode,
        @Schema(description = "消息") String message) {

    public static CaptionTemplateRenderResultLookupResponse ready(String outputProductId, String productType) {
        return new CaptionTemplateRenderResultLookupResponse(
                outputProductId, CaptionTemplateDeliveryStatus.READY, true,
                productType, false, false, "OUTPUT_PRODUCT_ID_ONLY",
                "Output product is ready. Use product delivery API when enabled.");
    }

    public static CaptionTemplateRenderResultLookupResponse notFound() {
        return new CaptionTemplateRenderResultLookupResponse(
                null, CaptionTemplateDeliveryStatus.NOT_FOUND, false,
                null, false, false, null,
                "Output product not found.");
    }

    public static CaptionTemplateRenderResultLookupResponse notDeliverable(String outputProductId, String reason) {
        return new CaptionTemplateRenderResultLookupResponse(
                outputProductId, CaptionTemplateDeliveryStatus.NOT_DELIVERABLE, false,
                null, false, false, null, reason);
    }
}
