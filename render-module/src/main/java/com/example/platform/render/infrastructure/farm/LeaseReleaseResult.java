package com.example.platform.render.infrastructure.farm;

/**
 * Result of a job lease release.
 */
public record LeaseReleaseResult(
        boolean released,
        String leaseId,
        String jobId,
        String failureReason
) {
    public static LeaseReleaseResult success(String leaseId, String jobId) {
        return new LeaseReleaseResult(true, leaseId, jobId, null);
    }

    public static LeaseReleaseResult failure(String leaseId, String reason) {
        return new LeaseReleaseResult(false, leaseId, null, reason);
    }

    public boolean isReleased() {
        return released;
    }
}
