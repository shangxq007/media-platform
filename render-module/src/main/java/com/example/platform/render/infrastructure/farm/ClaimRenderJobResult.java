package com.example.platform.render.infrastructure.farm;

import java.time.Instant;

/**
 * Result of a job lease claim attempt.
 */
public record ClaimRenderJobResult(
        boolean claimed,
        String leaseId,
        String jobId,
        String workerId,
        String providerId,
        int attempt,
        Instant leaseUntil,
        String failureReason
) {
    public static ClaimRenderJobResult success(String leaseId, String jobId, String workerId,
            String providerId, int attempt, Instant leaseUntil) {
        return new ClaimRenderJobResult(true, leaseId, jobId, workerId, providerId, attempt, leaseUntil, null);
    }

    public static ClaimRenderJobResult failure(String reason) {
        return new ClaimRenderJobResult(false, null, null, null, null, 0, null, reason);
    }

    public boolean isClaimed() {
        return claimed;
    }
}
