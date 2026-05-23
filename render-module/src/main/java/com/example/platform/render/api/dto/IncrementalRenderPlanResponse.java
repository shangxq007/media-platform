package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "增量渲染计划预览响应")
public record IncrementalRenderPlanResponse(
        @Schema(description = "FULL 或 INCREMENTAL") String mode,
        boolean fullReRenderRequired,
        int baseRevision,
        int targetRevision,
        List<String> executeTaskIds,
        List<String> reuseTaskIds,
        List<String> dirtyScopes,
        int changeCount,
        String planId,
        String timelineId,
        String finalComposer,
        Map<String, Object> metadata,
        List<IncrementalPlanTaskDto> tasks,
        List<IncrementalReuseArtifactDto> reuse) {}
