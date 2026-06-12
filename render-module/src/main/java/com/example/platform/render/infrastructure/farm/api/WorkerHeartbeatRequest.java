package com.example.platform.render.infrastructure.farm.api;

/**
 * Heartbeat report from a render worker.
 */
public record WorkerHeartbeatRequest(
        String status,
        int activeJobCount,
        Integer cpuCores,
        Integer memoryMb,
        int gpuCount,
        String gpuType,
        Long diskFreeMb,
        String metadataJson
) {}
