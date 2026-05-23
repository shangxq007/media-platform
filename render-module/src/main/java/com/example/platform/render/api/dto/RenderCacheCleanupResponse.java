package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "渲染 cache TTL 清理结果")
public record RenderCacheCleanupResponse(
        int jobsScanned,
        int objectsDeleted,
        int jobsUpdated) {}
