package com.example.platform.entitlement.domain;

import java.time.Instant;

public record QuotaProfile(
        String id,
        String profileKey,
        String name,
        String description,
        long monthlyRenderMinutes,
        int dailyRenderJobs,
        int concurrentRenderJobs,
        long storageBytes,
        long gpuMinutes,
        long remoteWorkerJobs,
        long promptExecutions,
        long extensionExecutions,
        int apiCallsPerMinute,
        int mcpCallsPerMinute,
        Instant createdAt,
        Instant updatedAt
) {}
