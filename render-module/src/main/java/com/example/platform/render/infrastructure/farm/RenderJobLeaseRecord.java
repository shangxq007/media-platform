package com.example.platform.render.infrastructure.farm;

import java.time.Instant;

/**
 * Persisted render job lease record.
 */
public record RenderJobLeaseRecord(
        String id,
        String leaseId,
        String jobId,
        String tenantId,
        String workerId,
        String providerId,
        RenderJobLeaseStatus status,
        long leaseVersion,
        Instant claimedAt,
        Instant leaseUntil,
        Instant renewedAt,
        Instant releasedAt,
        int attempt,
        int maxAttempts,
        String heartbeatTokenHash,
        String failureReason,
        String failureErrorCode,
        String createdByScheduler,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Check if this lease is still active (CLAIMED, RUNNING, or RENEWED).
     */
    public boolean isActive() {
        return status == RenderJobLeaseStatus.CLAIMED
                || status == RenderJobLeaseStatus.RUNNING
                || status == RenderJobLeaseStatus.RENEWED;
    }

    /**
     * Check if this lease has expired.
     */
    public boolean isExpired(Instant now) {
        return isActive() && leaseUntil != null && leaseUntil.isBefore(now);
    }

    /**
     * Check if this lease can be retried (failed but attempts remaining).
     */
    public boolean canRetry() {
        return status == RenderJobLeaseStatus.FAILED || status == RenderJobLeaseStatus.EXPIRED;
    }
}
