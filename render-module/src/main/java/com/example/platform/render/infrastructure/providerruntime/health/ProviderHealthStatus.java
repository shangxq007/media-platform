package com.example.platform.render.infrastructure.providerruntime.health;

/**
 * Health status of a render provider.
 */
public record ProviderHealthStatus(
        boolean isHealthy,
        String reason,
        int consecutiveFailures
) {
    public static ProviderHealthStatus healthy(String reason) {
        return new ProviderHealthStatus(true, reason, 0);
    }

    public static ProviderHealthStatus unhealthy(String reason, int consecutiveFailures) {
        return new ProviderHealthStatus(false, reason, consecutiveFailures);
    }
}
