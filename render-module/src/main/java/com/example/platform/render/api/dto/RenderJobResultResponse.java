package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response for render job result query.
 *
 * <p>Contains the output Product summary with safe metadata fields.
 * Does not expose internal provider/backend/environment selection,
 * signed URLs, storage references, or absolute filesystem paths.</p>
 */
@Schema(description = "渲染作业结果查询")
public record RenderJobResultResponse(
        @Schema(description = "渲染作业 ID") String renderJobId,
        @Schema(description = "项目 ID") String projectId,
        @Schema(description = "Timeline Revision ID") String timelineRevisionId,
        @Schema(description = "Timeline Snapshot ID") String snapshotId,
        @Schema(description = "输出 Product ID") String outputProductId,
        @Schema(description = "Product 状态") String productStatus,
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
        @Schema(description = "创建时间") String createdAt,
        @Schema(description = "完成时间") String completedAt,
        @Schema(description = "状态消息") String message) {}
