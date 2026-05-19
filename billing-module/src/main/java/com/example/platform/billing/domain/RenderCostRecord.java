package com.example.platform.billing.domain;

import java.time.OffsetDateTime;

/**
 * Cost record for a single render job execution.
 */
public record RenderCostRecord(
        String recordId,
        String tenantId,
        String userId,
        String renderJobId,
        String providerKey,
        String workerId,
        String preset,
        String outputFormat,
        long durationSeconds,
        long cpuSeconds,
        long gpuSeconds,
        long storageBytes,
        long egressBytes,
        int thirdPartyCalls,
        double estimatedCost,
        double actualCost,
        String currency,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime finalizedAt) {

    public static RenderCostRecord estimated(String tenantId, String userId, String renderJobId,
            String providerKey, String preset, String outputFormat, double estimatedCost, String currency) {
        return new RenderCostRecord(
                java.util.UUID.randomUUID().toString(),
                tenantId, userId, renderJobId, providerKey, null,
                preset, outputFormat, 0L, 0L, 0L, 0L, 0L, 0,
                estimatedCost, 0.0, currency, "ESTIMATED",
                OffsetDateTime.now(), null);
    }

    public RenderCostRecord withActual(double actualCost, long durationSeconds, long cpuSeconds,
            long gpuSeconds, long storageBytes, long egressBytes, String workerId) {
        return new RenderCostRecord(
                recordId, tenantId, userId, renderJobId, providerKey, workerId,
                preset, outputFormat, durationSeconds, cpuSeconds, gpuSeconds,
                storageBytes, egressBytes, thirdPartyCalls,
                estimatedCost, actualCost, currency, "FINALIZED",
                createdAt, OffsetDateTime.now());
    }
}
