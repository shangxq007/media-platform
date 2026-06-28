package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "交付状态")
public enum CaptionTemplateDeliveryStatus {
    @Schema(description = "输出就绪") READY,
    @Schema(description = "处理中") PROCESSING,
    @Schema(description = "失败") FAILED,
    @Schema(description = "未找到") NOT_FOUND,
    @Schema(description = "不可交付") NOT_DELIVERABLE
}
