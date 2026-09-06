package com.example.platform.providerplugin.visual;

/** Eligibility status declared by a provider for one visual capability. */
public enum ProviderVisualCapabilityStatus {
    PRODUCTION(true, true),
    BASELINE_CANDIDATE(false, true),
    POC(false, false),
    SPIKE(false, false),
    FUTURE(false, false),
    RESTRICTED(false, false),
    FORBIDDEN(false, false),
    DEPRECATED(false, false);

    private final boolean productionAllowed;
    private final boolean autoDispatchAllowed;

    ProviderVisualCapabilityStatus(boolean productionAllowed, boolean autoDispatchAllowed) {
        this.productionAllowed = productionAllowed;
        this.autoDispatchAllowed = autoDispatchAllowed;
    }

    public boolean isProductionAllowed() {
        return productionAllowed;
    }

    public boolean isAutoDispatchAllowed() {
        return autoDispatchAllowed;
    }
}
