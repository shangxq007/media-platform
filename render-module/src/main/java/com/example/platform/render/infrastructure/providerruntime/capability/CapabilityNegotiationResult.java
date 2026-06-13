package com.example.platform.render.infrastructure.providerruntime.capability;

import java.util.List;
import java.util.Set;

/**
 * Result of capability negotiation between providers and requirements.
 */
public record CapabilityNegotiationResult(
        boolean hasCapableProvider,
        List<String> supportedProviders,
        List<String> fullyCapableProviders,
        Set<String> unsupportedCapabilities,
        String selectionReason
) {
    /**
     * Get the best provider from the negotiation result.
     * Returns the first fully capable provider, or the first supported provider.
     */
    public String getBestProvider() {
        if (!fullyCapableProviders.isEmpty()) {
            return fullyCapableProviders.get(0);
        }
        if (!supportedProviders.isEmpty()) {
            return supportedProviders.get(0);
        }
        return null;
    }

    /**
     * Check if a specific provider is fully capable.
     */
    public boolean isFullyCapable(String providerName) {
        return fullyCapableProviders.contains(providerName);
    }

    /**
     * Check if a specific provider is supported (fully or partially).
     */
    public boolean isSupported(String providerName) {
        return supportedProviders.contains(providerName);
    }
}
