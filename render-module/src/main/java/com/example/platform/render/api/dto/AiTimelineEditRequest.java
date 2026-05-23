package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 自然语言编辑 Internal Timeline 1.0")
public record AiTimelineEditRequest(
        @NotBlank @Schema(description = "租户 ID") String tenantId,
        @NotBlank @Schema(description = "项目 ID") String projectId,
        @Schema(description = "基准作业 ID（与 baseTimelineJson 二选一）") String baseJobId,
        @Schema(description = "基准时间线 JSON（Internal Timeline 1.0）") String baseTimelineJson,
        @NotBlank @Schema(description = "编辑指令") String instruction,
        @Schema(description = "多轮编辑会话 ID") String editSessionId,
        @Schema(description = "意图标签") String intent,
        @Schema(description = "对话 ID") String conversationId,
        @Schema(description = "为 true 时 Patch 写入 aiProposals 待人工采纳，不立即改时间线") Boolean humanInTheLoop) {}
