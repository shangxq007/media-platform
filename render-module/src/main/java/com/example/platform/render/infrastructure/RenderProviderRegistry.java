package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all render providers and their capabilities.
 *
 * <p>Provides capability lookup, health status tracking, and provider resolution.</p>
 */
@Component
public class RenderProviderRegistry {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderRegistry.class);

    private final Map<String, RenderProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, RenderProviderCapability> capabilities = new ConcurrentHashMap<>();
    private final Map<String, RenderProviderHealthCheck> healthChecks = new ConcurrentHashMap<>();

    public void register(String key, RenderProvider provider, RenderProviderCapability capability) {
        providers.put(key, provider);
        capabilities.put(key, capability);
        log.info("Registered render provider: {} with {} effects", key, capability.supportedEffects().size());
    }

    public Optional<RenderProvider> getProvider(String key) {
        return Optional.ofNullable(providers.get(key));
    }

    public List<RenderProvider> getAllProviders() {
        return List.copyOf(providers.values());
    }

    public Optional<RenderProviderCapability> getCapability(String key) {
        return Optional.ofNullable(capabilities.get(key));
    }

    public List<RenderProviderCapability> getAllCapabilities() {
        return List.copyOf(capabilities.values());
    }

    public List<RenderProviderCapability> getCapabilitiesForProfile(String profile) {
        return capabilities.values().stream()
                .filter(cap -> cap.availableForProfile(profile))
                .toList();
    }

    public void updateHealthCheck(String key, RenderProviderHealthCheck health) {
        healthChecks.put(key, health);
    }

    public RenderProviderHealthCheck getHealthCheck(String key) {
        return healthChecks.get(key);
    }

    public Map<String, RenderProviderHealthCheck> getAllHealthChecks() {
        return Map.copyOf(healthChecks);
    }

    public List<String> getAvailableEffects() {
        return capabilities.values().stream()
                .flatMap(cap -> cap.supportedEffects().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> getEffectsForProfile(String profile) {
        return capabilities.values().stream()
                .filter(cap -> cap.availableForProfile(profile))
                .flatMap(cap -> cap.supportedEffects().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
