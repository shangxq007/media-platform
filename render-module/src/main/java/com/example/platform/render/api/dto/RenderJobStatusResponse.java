package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response for render job status query.
 *
 * <p>Reconstructed from Product metadata and ProductDependency lineage.
 * Does not expose internal provider/backend/environment selection,
 * signed URLs, storage references, or absolute filesystem paths.</p>
 */
@Schema(description = "渲染作业状态查询结果")
public record RenderJobStatusResponse(
        @Schema(description = "渲染作业 ID") String renderJobId,
        @Schema(description = "项目 ID") String projectId,
        @Schema(description = "Timeline Revision ID") String timelineRevisionId,
        @Schema(description = "Timeline Snapshot ID") String snapshotId,
        @Schema(description = "渲染状态: READY, FAILED, RUNNING") String status,
        @Schema(description = "渲染模式") String renderMode,
        @Schema(description = "输出配置") String outputProfile,
        @Schema(description = "输出格式") String outputFormat,
        @Schema(description = "输出 Product ID") String outputProductId,
        @Schema(description = "Product 状态") String productStatus,
        @Schema(description = "输入 Product IDs") List<String> inputProductIds,
        @Schema(description = "已解析输入依赖数") int inputDependencyCount,
        @Schema(description = "创建时间") String createdAt,
        @Schema(description = "完成时间") String completedAt,
        @Schema(description = "状态消息") String message,
        @Schema(description = "结果是否可用") boolean resultAvailable) {}
