package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "可复用段/阶段产物")
public record IncrementalReuseArtifactDto(
        String artifactId,
        String taskId,
        String uri,
        String cacheKey) {}
