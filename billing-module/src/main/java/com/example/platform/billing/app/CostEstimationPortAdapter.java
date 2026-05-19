package com.example.platform.billing.app;

import com.example.platform.shared.cost.CostEstimationPort;
import org.springframework.stereotype.Component;

/**
 * Adapter that exposes CostEstimationService as CostEstimationPort.
 */
@Component
public class CostEstimationPortAdapter implements CostEstimationPort {

    private final CostEstimationService costEstimationService;

    public CostEstimationPortAdapter(CostEstimationService costEstimationService) {
        this.costEstimationService = costEstimationService;
    }

    @Override
    public CostEstimate estimate(String providerKey, String preset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu) {
        CostEstimationService.CostEstimate est = costEstimationService.estimate(
                providerKey, preset, outputFormat, estimatedDurationSeconds, useGpu);
        return new CostEstimate(est.estimatedCost(), est.currency(), est.providerKey(),
                est.preset(), est.estimatedDurationSeconds(), est.useGpu());
    }

    @Override
    public CostEstimate estimateBest(String requestedPreset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu, double maxBudget) {
        CostEstimationService.CostEstimate est = costEstimationService.estimateBest(
                requestedPreset, outputFormat, estimatedDurationSeconds, useGpu, maxBudget);
        return new CostEstimate(est.estimatedCost(), est.currency(), est.providerKey(),
                est.preset(), est.estimatedDurationSeconds(), est.useGpu());
    }
}
