package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "候选复用段/阶段；最终复用需要 Artifact 权威校验")
public record IncrementalReuseArtifactDto(
        String artifactId,
        String taskId,
        String cacheKey) {}
