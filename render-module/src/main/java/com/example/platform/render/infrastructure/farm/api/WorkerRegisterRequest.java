package com.example.platform.render.infrastructure.farm.api;

import java.util.List;

/**
 * Request to register a render worker.
 */
public record WorkerRegisterRequest(
        String workerId,
        String workerType,
        String version,
        String imageTag,
        String hostname,
        String zone,
        List<String> providerIds,
        String capabilitiesJson,
        int maxConcurrentJobs,
        Integer cpuCores,
        Integer memoryMb,
        int gpuCount,
        String gpuType,
        Long diskFreeMb
) {
    public WorkerRegisterRequest {
        if (workerType == null || workerType.isBlank()) workerType = "RENDER";
        if (maxConcurrentJobs <= 0) maxConcurrentJobs = 1;
    }
}
