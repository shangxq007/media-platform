package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "单条 cache 预签名下载")
public record RenderCacheEntryPresignDto(
        String cacheKey,
        String segmentId,
        String taskId,
        String kind,
        String sourceUri,
        String downloadUrl,
        long expiresInSeconds) {}
