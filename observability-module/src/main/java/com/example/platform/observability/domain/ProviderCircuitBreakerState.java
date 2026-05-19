package com.example.platform.observability.domain;

import java.time.OffsetDateTime;

/**
 * Circuit breaker state for a provider.
 */
public record ProviderCircuitBreakerState(
        String providerKey,
        String state,
        int failureCount,
        int successCount,
        double failureRate,
        OffsetDateTime lastFailureAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextRetryAt) {

    public static final String STATE_CLOSED = "CLOSED";
    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_HALF_OPEN = "HALF_OPEN";

    public boolean isCircuitOpen() {
        return STATE_OPEN.equals(state);
    }
}
