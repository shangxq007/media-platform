package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "渲染 cache 预签名下载 URL 列表")
public record RenderCachePresignResponseDto(
        String jobId,
        List<RenderCacheEntryPresignDto> entries) {}
