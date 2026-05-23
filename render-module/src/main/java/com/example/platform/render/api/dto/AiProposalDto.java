package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 编辑建议（人工确认）")
public record AiProposalDto(
        @Schema(description = "建议 ID") String id,
        @Schema(description = "PENDING | ACCEPTED | REJECTED") String status,
        @Schema(description = "摘要") String summary,
        @Schema(description = "创建时间 ISO-8601") String createdAt,
        @Schema(description = "Patch 操作数") int operationCount) {}
