package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "DAG 任务摘要")
public record IncrementalPlanTaskDto(
        String taskId,
        String name,
        String type,
        String backend,
        List<String> dependsOn,
        String cacheKey,
        Map<String, String> parameters) {}
