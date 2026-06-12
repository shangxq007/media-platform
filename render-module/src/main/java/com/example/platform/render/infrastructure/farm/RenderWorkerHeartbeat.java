package com.example.platform.render.infrastructure.farm;

import java.time.Instant;

/**
 * Heartbeat report from a render worker.
 */
public record RenderWorkerHeartbeat(
        String workerId,
        RenderWorkerStatus status,
        int activeJobCount,
        Integer cpuCores,
        Integer memoryMb,
        int gpuCount,
        String gpuType,
        Long diskFreeMb,
        String metadataJson
) {
    public RenderWorkerHeartbeat {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (status == null) {
            status = RenderWorkerStatus.IDLE;
        }
    }
}
