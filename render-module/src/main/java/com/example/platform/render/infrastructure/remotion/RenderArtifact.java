package com.example.platform.render.infrastructure.remotion;

import java.time.Instant;

public record RenderArtifact(
        String artifactId,
        RenderArtifactType artifactType,
        String url,
        String localPath,
        String mimeType,
        long sizeBytes,
        String hash,
        Long durationMs,
        Integer width,
        Integer height,
        Integer fps,
        String createdByStepId,
        Instant createdAt
) {}
