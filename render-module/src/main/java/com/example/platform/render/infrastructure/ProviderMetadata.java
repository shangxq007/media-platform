package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Metadata for a render provider.
 */
public record ProviderMetadata(
        String name,
        ProviderStatus status,
        String priority,
        ProviderType providerType,
        List<String> declaredCapabilities,
        List<String> enabledCapabilities,
        List<String> disabledCapabilities,
        List<String> notFor,
        boolean autoDispatch,
        String runtime,
        String purpose,
        List<String> limitations
) {
    public boolean isProduction() {
        return status == ProviderStatus.PRODUCTION;
    }

    public boolean isPoc() {
        return status == ProviderStatus.POC;
    }

    public boolean isDeprecated() {
        return status == ProviderStatus.DEPRECATED;
    }

    public boolean isHold() {
        return status == ProviderStatus.HOLD;
    }

    public boolean isSpike() {
        return status == ProviderStatus.SPIKE;
    }

    public boolean isOptional() {
        return status == ProviderStatus.OPTIONAL;
    }

    public boolean participatesInAutoRouting() {
        return autoDispatch && (status == ProviderStatus.PRODUCTION || status == ProviderStatus.POC);
    }

    public boolean canHandleCapability(String capability) {
        return enabledCapabilities.contains(capability);
    }

    public boolean shouldNotHandle(String capability) {
        return notFor.contains(capability);
    }
}
