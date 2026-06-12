package com.example.platform.render.infrastructure.farm;

import java.time.Instant;

/**
 * Registration request for a new render worker.
 */
public record RenderWorkerRegistration(
        String workerId,
        String workerType,
        String version,
        String imageTag,
        String hostname,
        String zone,
        String providerIds,
        String capabilitiesJson,
        int maxConcurrentJobs,
        Integer cpuCores,
        Integer memoryMb,
        int gpuCount,
        String gpuType,
        Long diskFreeMb
) {
    public RenderWorkerRegistration {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (workerType == null || workerType.isBlank()) {
            workerType = "RENDER";
        }
        if (maxConcurrentJobs <= 0) {
            maxConcurrentJobs = 1;
        }
    }
}
