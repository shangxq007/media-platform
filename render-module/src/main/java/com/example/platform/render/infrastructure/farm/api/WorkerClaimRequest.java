package com.example.platform.render.infrastructure.farm.api;

import java.util.List;

/**
 * Request from a worker to claim the next available job.
 */
public record WorkerClaimRequest(
        List<String> providerIds,
        String capabilitiesJson,
        boolean allowPoc,
        String mode
) {
    public WorkerClaimRequest {
        if (mode == null || mode.isBlank()) mode = "PRODUCTION";
        if (providerIds == null) providerIds = List.of();
    }
}
