package com.example.platform.render.infrastructure.providerruntime.health;

import com.example.platform.render.infrastructure.RenderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors health status of render providers.
 * Tracks failures and calculates health scores.
 */
@Component
public class ProviderHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthMonitor.class);

    private static final int MAX_FAILURES_BEFORE_UNHEALTHY = 3;
    private static final long HEALTH_CHECK_COOLDOWN_MS = 60000; // 1 minute

    private final Map<String, ProviderHealthState> healthStates = new ConcurrentHashMap<>();

    /**
     * Check health status of a provider.
     */
    public ProviderHealthStatus checkHealth(String providerName) {
        ProviderHealthState state = healthStates.get(providerName);

        if (state == null) {
            // No state recorded - assume healthy
            return ProviderHealthStatus.healthy("No issues recorded");
        }

        // Check if provider is in cooldown
        if (state.isInCooldown()) {
            return ProviderHealthStatus.unhealthy(
                    "In cooldown until " + state.cooldownUntil(),
                    state.consecutiveFailures()
            );
        }

        // Check failure count
        if (state.consecutiveFailures() >= MAX_FAILURES_BEFORE_UNHEALTHY) {
            return ProviderHealthStatus.unhealthy(
                    "Too many consecutive failures: " + state.consecutiveFailures(),
                    state.consecutiveFailures()
            );
        }

        return ProviderHealthStatus.healthy("Healthy");
    }

    /**
     * Record a successful execution.
     */
    public void recordSuccess(String providerName) {
        healthStates.compute(providerName, (k, v) -> {
            if (v == null) {
                return new ProviderHealthState(0, null, Instant.now(), null);
            }
            return v.withSuccess();
        });
    }

    /**
     * Record a failed execution.
     */
    public void recordFailure(String providerName, String reason) {
        healthStates.compute(providerName, (k, v) -> {
            if (v == null) {
                return new ProviderHealthState(1, reason, null, calculateCooldown(1));
            }
            return v.withFailure(reason);
        });

        ProviderHealthState state = healthStates.get(providerName);
        log.warn("Provider {} failure recorded: {} (consecutive: {})",
                providerName, reason, state.consecutiveFailures());
    }

    /**
     * Reset health state for a provider.
     */
    public void reset(String providerName) {
        healthStates.remove(providerName);
    }

    /**
     * Get health state for a provider.
     */
    public ProviderHealthState getState(String providerName) {
        return healthStates.get(providerName);
    }

    /**
     * Get all health states.
     */
    public Map<String, ProviderHealthState> getAllStates() {
        return Map.copyOf(healthStates);
    }

    private Instant calculateCooldown(int consecutiveFailures) {
        long cooldownMs = HEALTH_CHECK_COOLDOWN_MS * consecutiveFailures;
        return Instant.now().plusMillis(cooldownMs);
    }

    /**
     * Health state record for a provider.
     */
    public record ProviderHealthState(
            int consecutiveFailures,
            String lastFailureReason,
            Instant lastSuccess,
            Instant cooldownUntil
    ) {
        public boolean isInCooldown() {
            return cooldownUntil != null && Instant.now().isBefore(cooldownUntil);
        }

        public ProviderHealthState withSuccess() {
            return new ProviderHealthState(0, null, Instant.now(), null);
        }

        public ProviderHealthState withFailure(String reason) {
            return new ProviderHealthState(
                    consecutiveFailures + 1,
                    reason,
                    lastSuccess,
                    Instant.now().plusMillis(60000L * (consecutiveFailures + 1))
            );
        }
    }
}
