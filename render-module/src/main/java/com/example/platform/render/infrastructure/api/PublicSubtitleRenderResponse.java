package com.example.platform.render.infrastructure.api;

import java.time.Instant;
import java.util.List;

public record PublicSubtitleRenderResponse(
        String jobId,
        String status,
        String jobType,
        String mode,
        Instant createdAt,
        Instant expiresAt,
        String statusUrl,
        String traceUrl
) {}
