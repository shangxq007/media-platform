package com.example.platform.render.infrastructure.providerruntime.capability;

import java.util.Set;

/**
 * Describes a provider's capabilities in a standardized format.
 */
public record CapabilityDescriptor(
        String providerName,
        Set<String> supportedCapabilities,
        Set<String> supportedEffects,
        Set<String> supportedFormats,
        Set<String> supportedResolutions,
        Set<String> limitations,
        String maxResolution,
        boolean supportsGpu,
        boolean supportsRemote,
        boolean supportsSubtitle
) {
    /**
     * Check if this descriptor supports all required capabilities.
     */
    public boolean supportsAll(Set<String> required) {
        return supportedCapabilities.containsAll(required);
    }

    /**
     * Check if this descriptor supports a specific format.
     */
    public boolean supportsFormat(String format) {
        return supportedFormats.contains(format);
    }

    /**
     * Check if this descriptor supports a specific resolution.
     */
    public boolean supportsResolution(String resolution) {
        return supportedResolutions.contains(resolution);
    }
}
