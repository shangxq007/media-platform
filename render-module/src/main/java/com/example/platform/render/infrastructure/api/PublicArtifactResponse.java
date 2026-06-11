package com.example.platform.render.infrastructure.api;

import java.time.Instant;

public record PublicArtifactResponse(
        String artifactId,
        String artifactType,
        String url,
        String downloadUrl,
        String mimeType,
        Long sizeBytes,
        Long durationMs,
        Integer width,
        Integer height,
        Integer fps,
        String createdByStepId,
        Instant createdAt
) {}
