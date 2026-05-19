package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estimates render job costs based on provider profiles and preset configurations.
 */
@Service
public class CostEstimationService {

    private static final Logger log = LoggerFactory.getLogger(CostEstimationService.class);

    private final Map<String, ProviderCostProfile> providerProfiles = new ConcurrentHashMap<>();
    private final Map<String, Double> presetBaseCosts = new ConcurrentHashMap<>();

    public CostEstimationService() {
        initializeDefaultProfiles();
    }

    private void initializeDefaultProfiles() {
        // Default provider cost profiles - sourced from config in production
        providerProfiles.put("javacv", new ProviderCostProfile(
                "javacv", 0.05, 0.0, 0.02, 0.08, 0.001, "USD",
                Map.of("default_1080p", 1.0, "default_720p", 0.6, "4k_2160p", 3.5,
                        "h265", 1.3, "vp9", 1.1, "preview_720p", 0.3,
                        "free_720p_watermarked", 0.4, "pro_1080p", 1.0, "team_4k", 3.5)));
        providerProfiles.put("ofx", new ProviderCostProfile(
                "ofx", 0.08, 0.0, 0.02, 0.08, 0.002, "USD",
                Map.of("ofx_1080p", 1.5, "ofx_720p", 0.9, "enterprise_4k_ofx", 5.0)));
        providerProfiles.put("gpac", new ProviderCostProfile(
                "gpac", 0.03, 0.0, 0.02, 0.12, 0.001, "USD",
                Map.of("default", 0.5)));
        providerProfiles.put("mlt", new ProviderCostProfile(
                "mlt", 0.04, 0.0, 0.02, 0.08, 0.001, "USD",
                Map.of("default", 0.8)));
        providerProfiles.put("gstreamer", new ProviderCostProfile(
                "gstreamer", 0.04, 0.0, 0.02, 0.08, 0.001, "USD",
                Map.of("default", 0.7)));
        providerProfiles.put("remote-javacv", new ProviderCostProfile(
                "remote-javacv", 0.06, 0.25, 0.02, 0.08, 0.001, "USD",
                Map.of("default_1080p", 1.2, "gpu_h264", 1.8, "gpu_h265", 2.0)));
    }

    /**
     * Estimate the cost for a render job.
     */
    public CostEstimate estimate(String providerKey, String preset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu) {
        ProviderCostProfile profile = providerProfiles.getOrDefault(providerKey,
                new ProviderCostProfile(providerKey, 0.05, 0.0, 0.02, 0.08, 0.001, "USD", Map.of()));

        double multiplier = profile.multiplierFor(preset);
        double hours = estimatedDurationSeconds / 3600.0;
        double computeCost = useGpu
                ? hours * profile.gpuCostPerHour() * multiplier
                : hours * profile.cpuCostPerHour() * multiplier;
        double storageCost = profile.storageCostPerGbMonth() * 0.001 * multiplier;
        double total = computeCost + storageCost + profile.apiCallCost();

        log.debug("CostEstimationService: estimated {} {} GPU={} duration={}s = ${}",
                providerKey, preset, useGpu, estimatedDurationSeconds, String.format("%.4f", total));

        return new CostEstimate(total, profile.currency(), providerKey, preset,
                estimatedDurationSeconds, useGpu);
    }

    /**
     * Estimate the best provider/preset combination for a given budget constraint.
     */
    public CostEstimate estimateBest(String requestedPreset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu, double maxBudget) {
        CostEstimate best = null;
        for (var entry : providerProfiles.entrySet()) {
            CostEstimate estimate = estimate(entry.getKey(), requestedPreset, outputFormat,
                    estimatedDurationSeconds, useGpu);
            if (estimate.estimatedCost() <= maxBudget) {
                if (best == null || estimate.estimatedCost() < best.estimatedCost()) {
                    best = estimate;
                }
            }
        }
        if (best == null) {
            // Fallback to cheapest option
            best = estimate("javacv", "preview_720p", outputFormat, estimatedDurationSeconds, false);
        }
        return best;
    }

    public void registerProviderProfile(ProviderCostProfile profile) {
        providerProfiles.put(profile.providerKey(), profile);
    }

    public void registerPresetCost(String preset, double baseCost) {
        presetBaseCosts.put(preset, baseCost);
    }

    public record CostEstimate(
            double estimatedCost,
            String currency,
            String providerKey,
            String preset,
            long estimatedDurationSeconds,
            boolean useGpu) {}
}
