package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fallback policy when the preferred render provider is unavailable.
 *
 * <p>Fallback chain:</p>
 * <ol>
 *   <li>Try preferred provider</li>
 *   <li>Try any healthy provider that supports the profile</li>
 *   <li>Try any provider that supports the profile</li>
 *   <li>Fail with descriptive error</li>
 * </ol>
 */
@Component
public class RenderProviderFallbackPolicy {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderFallbackPolicy.class);

    private final RenderProviderRegistry registry;
    private final RenderProviderSelectionPolicy selectionPolicy;

    public RenderProviderFallbackPolicy(RenderProviderRegistry registry,
                                         RenderProviderSelectionPolicy selectionPolicy) {
        this.registry = registry;
        this.selectionPolicy = selectionPolicy;
    }

    public RenderProvider resolve(String profile, List<String> effectKeys) {
        // Step 1: Try preferred provider
        Optional<RenderProvider> preferred = selectionPolicy.select(profile, effectKeys);
        if (preferred.isPresent()) {
            RenderProviderHealthCheck health = registry.getHealthCheck(preferred.get().getSupportedProfiles().get(0));
            if (health == null || health.healthy()) {
                log.debug("Using preferred provider for profile '{}'", profile);
                return preferred.get();
            }
        }

        // Step 2: Try any healthy provider
        List<RenderProviderCapability> allCaps = registry.getCapabilitiesForProfile(profile);
        for (RenderProviderCapability cap : allCaps) {
            RenderProviderHealthCheck health = registry.getHealthCheck(cap.providerKey());
            if (health != null && health.healthy()) {
                Optional<RenderProvider> provider = registry.getProvider(cap.providerKey());
                if (provider.isPresent()) {
                    log.info("Falling back to healthy provider '{}' for profile '{}'", cap.providerKey(), profile);
                    return provider.get();
                }
            }
        }

        // Step 3: Try any provider
        for (RenderProviderCapability cap : allCaps) {
            Optional<RenderProvider> provider = registry.getProvider(cap.providerKey());
            if (provider.isPresent()) {
                log.warn("Using unhealthy provider '{}' for profile '{}' (last resort)", cap.providerKey(), profile);
                return provider.get();
            }
        }

        throw new IllegalStateException("No render provider available for profile: " + profile);
    }
}
