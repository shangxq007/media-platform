package com.example.platform.render.infrastructure.farm.api;

/**
 * Response from worker registration.
 */
public record WorkerRegisterResponse(
        String workerId,
        int heartbeatIntervalSeconds,
        int leaseDurationSeconds,
        String status
) {}
