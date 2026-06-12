package com.example.platform.render.infrastructure.farm;

import java.time.Instant;

/**
 * Result of a render farm job claim. Includes job execution payload.
 */
public record RenderFarmClaimResult(
        boolean claimed,
        String leaseId,
        String jobId,
        String tenantId,
        String providerId,
        int attempt,
        int maxAttempts,
        Instant leaseUntil,
        String renderProfile,
        String timelineJson,
        String failureReason
) {
    public static RenderFarmClaimResult success(String leaseId, String jobId, String tenantId,
            String providerId, int attempt, int maxAttempts, Instant leaseUntil,
            String renderProfile, String timelineJson) {
        return new RenderFarmClaimResult(true, leaseId, jobId, tenantId, providerId,
                attempt, maxAttempts, leaseUntil, renderProfile, timelineJson, null);
    }

    public static RenderFarmClaimResult noJob(String reason) {
        return new RenderFarmClaimResult(false, null, null, null, null, 0, 0, null, null, null, reason);
    }

    public static RenderFarmClaimResult noWorker(String reason) {
        return new RenderFarmClaimResult(false, null, null, null, null, 0, 0, null, null, null, reason);
    }

    public static RenderFarmClaimResult providerIneligible(String reason) {
        return new RenderFarmClaimResult(false, null, null, null, null, 0, 0, null, null, null, reason);
    }

    public boolean isClaimed() {
        return claimed;
    }
}
