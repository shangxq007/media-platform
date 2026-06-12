package com.example.platform.render.infrastructure.farm.api;

import java.time.Instant;

/**
 * Response when a worker claims a job. Contains the lease and job execution payload.
 */
public record WorkerClaimResponse(
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
    public static WorkerClaimResponse noJob() {
        return new WorkerClaimResponse(false, null, null, null, null, 0, 0, null, null, null, "No queued jobs available");
    }

    public static WorkerClaimResponse noWorker() {
        return new WorkerClaimResponse(false, null, null, null, null, 0, 0, null, null, null, "Worker not available");
    }

    public static WorkerClaimResponse providerIneligible(String reason) {
        return new WorkerClaimResponse(false, null, null, null, null, 0, 0, null, null, null, reason);
    }

    public static WorkerClaimResponse success(String leaseId, String jobId, String tenantId,
            String providerId, int attempt, int maxAttempts, Instant leaseUntil,
            String renderProfile, String timelineJson) {
        return new WorkerClaimResponse(true, leaseId, jobId, tenantId, providerId,
                attempt, maxAttempts, leaseUntil, renderProfile, timelineJson, null);
    }
}
