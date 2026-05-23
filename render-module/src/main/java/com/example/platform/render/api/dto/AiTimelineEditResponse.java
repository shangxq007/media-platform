package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 时间线编辑结果")
public record AiTimelineEditResponse(
        @Schema(description = "编辑后的 Internal Timeline 1.0 JSON") String timelineJson,
        @Schema(description = "AI provider") String provider,
        @Schema(description = "模型") String model,
        @Schema(description = "是否通过 JSON Patch 应用") boolean appliedPatch,
        @Schema(description = "待处理/已处理的 AI 建议") java.util.List<AiProposalDto> proposals,
        @Schema(description = "humanInTheLoop 时最新待采纳建议 ID") String pendingProposalId) {}
