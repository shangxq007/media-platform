package com.example.platform.render.domain.timeline.compile.binding;

import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;

/**
 * Reference to a bound provider for a capability node.
 *
 * <p>Internal only — captures the selected provider metadata
 * without exposing it in public APIs.</p>
 *
 * @param providerName    provider name (e.g., "ffmpeg", "remotion")
 * @param providerStatus  provider status (PRODUCTION, POC, etc.)
 * @param providerType    provider type (RENDER, PACKAGING, etc.)
 * @param priority        provider priority (P0, P1, etc.)
 * @param autoDispatch    whether provider supports auto-dispatch
 * @param toolAvailable   whether the provider's tool/binary is available
 * @param toolVersion     detected tool version (null if unavailable)
 * @param score           binding score (lower = preferred)
 */
public record BoundProviderRef(
        String providerName,
        ProviderStatus providerStatus,
        ProviderType providerType,
        String priority,
        boolean autoDispatch,
        boolean toolAvailable,
        String toolVersion,
        int score) {

    /**
     * Returns true if this provider is production-eligible for auto-dispatch.
     */
    public boolean isProductionEligible() {
        return providerStatus == ProviderStatus.PRODUCTION && autoDispatch;
    }

    /**
     * Returns true if this provider is available for manual/experiment binding.
     */
    public boolean isManualEligible() {
        return providerStatus.canBeConfiguredForDispatch() && toolAvailable;
    }
}
