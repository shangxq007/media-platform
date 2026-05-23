package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Internal Timeline 1.0 预览结果")
public record TimelineInternalPreviewResponse(
        @Schema(description = "规范化后的 Internal Timeline 1.0 JSON") String internalTimelineJson,
        @Schema(description = "检测到的源 schema") String sourceSchema,
        @Schema(description = "输入是否已是 Internal 1.0") boolean alreadyInternal,
        @Schema(description = "源轨/层数量") int sourceTrackOrLayerCount,
        @Schema(description = "目标轨/层数量") int internalTrackOrLayerCount,
        @Schema(description = "源片段数（估算）") int sourceClipCount,
        @Schema(description = "目标片段数") int internalClipCount,
        @Schema(description = "目标 revision") int targetRevision,
        @Schema(description = "JSON 字节差（internal - source）") int jsonByteDelta) {}
