package com.example.platform.render.infrastructure;

import java.time.Instant;

/**
 * Health check result for a render provider.
 *
 * @param providerKey  the provider identifier
 * @param healthy      whether the provider is healthy
 * @param message      status message
 * @param lastChecked  timestamp of last check
 * @param latencyMs    response latency in milliseconds (optional probe)
 */
public record RenderProviderHealthCheck(
        String providerKey,
        boolean healthy,
        String message,
        Instant lastChecked,
        long latencyMs
) {
    public static RenderProviderHealthCheck ok(String providerKey) {
        return new RenderProviderHealthCheck(providerKey, true, "OK", Instant.now(), 0);
    }

    public static RenderProviderHealthCheck ok(String providerKey, long latencyMs) {
        return new RenderProviderHealthCheck(providerKey, true, "OK", Instant.now(), latencyMs);
    }

    public static RenderProviderHealthCheck failed(String providerKey, String message) {
        return new RenderProviderHealthCheck(providerKey, false, message, Instant.now(), -1);
    }
}
