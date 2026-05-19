package com.example.platform.shared.cost;

/**
 * Port interface for cost estimation, implemented by billing module.
 */
public interface CostEstimationPort {
    CostEstimate estimate(String providerKey, String preset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu);
    CostEstimate estimateBest(String requestedPreset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu, double maxBudget);

    record CostEstimate(
            double estimatedCost,
            String currency,
            String providerKey,
            String preset,
            long estimatedDurationSeconds,
            boolean useGpu) {}
}
