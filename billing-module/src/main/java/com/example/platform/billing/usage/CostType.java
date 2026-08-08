package com.example.platform.billing.usage;

/**
 * Provenance of a provider cost observation.
 */
public enum CostType {

    /**
     * Cost as reported by the provider.
     */
    REPORTED,

    /**
     * Cost estimated by the platform (provider actual unavailable).
     */
    ESTIMATED,

    /**
     * Cost derived from other canonical observations.
     */
    DERIVED
}
