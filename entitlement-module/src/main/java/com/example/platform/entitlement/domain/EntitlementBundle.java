package com.example.platform.entitlement.domain;

import java.time.Instant;

public record EntitlementBundle(
        String id,
        String bundleKey,
        String name,
        String description,
        String status,
        String allowedProviders,
        String allowedPresets,
        boolean gpuAllowed,
        boolean remoteWorkerAllowed,
        boolean customFontsAllowed,
        int maxSubtitleTracks,
        int maxConcurrentJobs,
        long monthlyRenderMinutes,
        long storageLimitBytes,
        boolean watermarkRequired,
        boolean priorityQueueAllowed,
        boolean betaEffectsAllowed,
        long promptExecutionLimit,
        boolean extensionExecutionAllowed,
        boolean apiAccessAllowed,
        boolean mcpAccessAllowed,
        Instant createdAt,
        Instant updatedAt
) {}
