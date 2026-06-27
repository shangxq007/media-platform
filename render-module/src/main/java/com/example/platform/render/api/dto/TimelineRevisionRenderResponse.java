package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response from rendering a TimelineRevision.
 *
 * <p>Contains the render job identifier, output product identifier,
 * input product references, and provenance metadata. Does not expose
 * internal provider/backend/environment selection, signed URLs, or
 * absolute filesystem paths.</p>
 */
@Schema(description = "TimelineRevision 渲染结果")
public record TimelineRevisionRenderResponse(
        @Schema(description = "渲染作业 ID") String renderJobId,
        @Schema(description = "Timeline Revision ID") String timelineRevisionId,
        @Schema(description = "Timeline Snapshot ID") String snapshotId,
        @Schema(description = "输出 Product ID") String outputProductId,
        @Schema(description = "Product 状态") String productStatus,
        @Schema(description = "存储引用 ID") String storageReferenceId,
        @Schema(description = "MIME 类型") String mimeType,
        @Schema(description = "输出格式") String outputFormat,
        @Schema(description = "宽度") int width,
        @Schema(description = "高度") int height,
        @Schema(description = "帧率") int fps,
        @Schema(description = "时长（秒）") double durationSeconds,
        @Schema(description = "是否包含字幕") boolean hasSubtitles,
        @Schema(description = "基线渲染器") String baselineRenderer,
        @Schema(description = "渲染模式") String renderMode,
        @Schema(description = "输入 Product IDs") List<String> inputProductIds,
        @Schema(description = "已解析输入依赖数") int inputDependencyCount,
        @Schema(description = "状态消息") String message) {

    /**
     * Creates a success response from a RevisionRenderResult.
     */
    public static TimelineRevisionRenderResponse success(
            com.example.platform.render.app.timeline.TimelineRevisionRenderService.RevisionRenderResult result) {
        return new TimelineRevisionRenderResponse(
                result.renderJobId(),
                result.timelineRevisionId(),
                result.snapshotId(),
                result.outputProductId(),
                result.productStatus(),
                result.storageReferenceId(),
                result.mimeType(),
                result.outputFormat(),
                result.width(),
                result.height(),
                result.fps(),
                result.durationSeconds(),
                result.hasSubtitles(),
                result.baselineRenderer(),
                result.renderMode(),
                result.inputProductIds(),
                result.inputDependencyCount(),
                "Timeline revision rendered successfully");
    }

    /**
     * Creates a failure response.
     */
    public static TimelineRevisionRenderResponse failure(
            String timelineRevisionId, String message) {
        return new TimelineRevisionRenderResponse(
                null,
                timelineRevisionId,
                null,
                null,
                "FAILED",
                null,
                null,
                null,
                0, 0, 0, 0.0,
                false,
                null,
                null,
                null,
                0,
                message);
    }
}
