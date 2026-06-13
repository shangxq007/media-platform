package com.example.platform.render.infrastructure.billing;

import java.time.Instant;

/**
 * Billing record for a render job.
 * Tracks estimated vs actual costs and usage.
 */
public record RenderBillingRecord(
        String id,
        String jobId,
        String tenantId,
        double estimatedCost,
        double actualCost,
        long usageSeconds,
        String providerId,
        long outputSizeBytes,
        BillingRecordStatus status,
        Instant createdAt,
        Instant completedAt
) {
    /**
     * Create a new billing record (before execution).
     */
    public static RenderBillingRecord create(String jobId, String tenantId, 
                                              double estimatedCost, Instant createdAt) {
        return new RenderBillingRecord(
                "bill-" + jobId,
                jobId,
                tenantId,
                estimatedCost,
                0,  // actual cost unknown yet
                0,  // usage unknown yet
                null,
                0,
                BillingRecordStatus.ESTIMATED,
                createdAt,
                null
        );
    }

    /**
     * Finalize the billing record with actual costs.
     */
    public RenderBillingRecord finalize(double actualCost, long usageSeconds, 
                                         String providerId, long outputSizeBytes) {
        return new RenderBillingRecord(
                id,
                jobId,
                tenantId,
                estimatedCost,
                actualCost,
                usageSeconds,
                providerId,
                outputSizeBytes,
                BillingRecordStatus.FINALIZED,
                createdAt,
                Instant.now()
        );
    }

    /**
     * Mark the record as failed.
     */
    public RenderBillingRecord markFailed() {
        return new RenderBillingRecord(
                id,
                jobId,
                tenantId,
                estimatedCost,
                actualCost,
                usageSeconds,
                providerId,
                outputSizeBytes,
                BillingRecordStatus.FAILED,
                createdAt,
                Instant.now()
        );
    }

    /**
     * Get the cost variance (actual - estimated).
     */
    public double costVariance() {
        return actualCost - estimatedCost;
    }

    /**
     * Get the cost variance percentage.
     */
    public double costVariancePercent() {
        if (estimatedCost == 0) return 0;
        return (costVariance() / estimatedCost) * 100;
    }

    /**
     * Check if the record is finalized.
     */
    public boolean isFinalized() {
        return status == BillingRecordStatus.FINALIZED;
    }
}
