package com.example.platform.render.infrastructure.providerruntime.capability;

import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderCapability;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for capability descriptors.
 * Caches descriptors to avoid repeated computation.
 */
@Component
public class CapabilityDescriptorRegistry {

    private final Map<String, CapabilityDescriptor> descriptorCache = new ConcurrentHashMap<>();

    /**
     * Get or create a capability descriptor for a provider.
     */
    public CapabilityDescriptor getOrCreateDescriptor(RenderProvider provider) {
        String providerName = provider.getClass().getSimpleName();
        return descriptorCache.computeIfAbsent(providerName, k -> buildDescriptor(provider));
    }

    /**
     * Get a descriptor by provider name.
     */
    public CapabilityDescriptor getDescriptor(String providerName) {
        return descriptorCache.get(providerName);
    }

    /**
     * Check if a descriptor is registered for a provider.
     */
    public boolean hasDescriptor(String providerName) {
        return descriptorCache.containsKey(providerName);
    }

    /**
     * Get all registered descriptors.
     */
    public Map<String, CapabilityDescriptor> getAllDescriptors() {
        return Map.copyOf(descriptorCache);
    }

    /**
     * Clear the cache (for testing).
     */
    public void clear() {
        descriptorCache.clear();
    }

    private CapabilityDescriptor buildDescriptor(RenderProvider provider) {
        RenderProviderCapability capability = provider.getCapability();

        Set<String> supportedCapabilities = capability.supportedEffects() != null
                ? Set.copyOf(capability.supportedEffects())
                : Set.of();

        Set<String> supportedEffects = capability.supportedEffects() != null
                ? Set.copyOf(capability.supportedEffects())
                : Set.of();

        Set<String> supportedFormats = capability.supportedFormats() != null
                ? Set.copyOf(capability.supportedFormats())
                : Set.of();

        Set<String> supportedResolutions = capability.supportedCodecs() != null
                ? Set.copyOf(capability.supportedCodecs())
                : Set.of();

        Set<String> limitations = provider.getLimitations() != null
                ? Set.copyOf(provider.getLimitations())
                : Set.of();

        return new CapabilityDescriptor(
                provider.getClass().getSimpleName(),
                supportedCapabilities,
                supportedEffects,
                supportedFormats,
                supportedResolutions,
                limitations,
                capability.maxResolution() != null ? capability.maxResolution() : "1920x1080",
                capability.requiresGpu(),
                false, // remoteEnabled not available in current capability model
                capability.supportedSubtitleModes() != null && !capability.supportedSubtitleModes().isEmpty()
        );
    }
}
