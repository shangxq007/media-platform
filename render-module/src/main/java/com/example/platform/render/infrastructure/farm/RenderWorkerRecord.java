package com.example.platform.render.infrastructure.farm;

import java.time.Instant;

/**
 * Persisted render worker record.
 */
public record RenderWorkerRecord(
        String id,
        String workerId,
        RenderWorkerStatus status,
        String workerType,
        String version,
        String imageTag,
        String hostname,
        String zone,
        String providerIds,
        String capabilitiesJson,
        Integer maxConcurrentJobs,
        Integer activeJobCount,
        Integer cpuCores,
        Integer memoryMb,
        Integer gpuCount,
        String gpuType,
        Long diskFreeMb,
        Instant lastHeartbeatAt,
        Instant registeredAt,
        Instant expiresAt,
        String metadataJson
) {
    /**
     * Check if this worker can accept more jobs.
     */
    public boolean canAcceptJobs() {
        if (status != RenderWorkerStatus.IDLE && status != RenderWorkerStatus.BUSY) {
            return false;
        }
        if (maxConcurrentJobs != null && activeJobCount != null) {
            return activeJobCount < maxConcurrentJobs;
        }
        return true;
    }
}
