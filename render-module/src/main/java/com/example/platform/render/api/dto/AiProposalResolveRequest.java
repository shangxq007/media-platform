package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "采纳或拒绝 AI 建议")
public record AiProposalResolveRequest(
        @NotBlank @Schema(description = "当前时间线 JSON（含 platformExtensions.aiProposals）") String timelineJson,
        @Schema(description = "AI 改稿会话 ID，用于修订链分支") String editSessionId,
        @Schema(description = "采纳后写入 timeline_revision（默认 true）") Boolean persistRevision) {}
