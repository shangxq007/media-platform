package com.example.platform.render.infrastructure.api;

import java.time.Instant;
import java.util.List;

public record PublicJobStatusResponse(
        String jobId,
        String status,
        String jobType,
        String mode,
        Double progress,
        String currentStep,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {}
