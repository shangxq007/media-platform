package com.example.platform.billing.domain;

import java.util.Map;

/**
 * Cost profile for a single provider, defining per-unit pricing.
 * Values are sourced from configuration, not hardcoded.
 */
public record ProviderCostProfile(
        String providerKey,
        double cpuCostPerHour,
        double gpuCostPerHour,
        double storageCostPerGbMonth,
        double egressCostPerGb,
        double apiCallCost,
        String currency,
        Map<String, Double> presetMultipliers) {

    public double multiplierFor(String preset) {
        return presetMultipliers != null ? presetMultipliers.getOrDefault(preset, 1.0) : 1.0;
    }
}
