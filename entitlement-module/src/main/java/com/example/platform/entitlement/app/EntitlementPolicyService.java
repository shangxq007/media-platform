package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.CustomPolicyRepository;
import com.example.platform.shared.cost.BudgetGuardPort;
import com.example.platform.shared.cost.BudgetGuardPort.BudgetCheckResult;
import com.example.platform.shared.cost.CostEstimationPort;
import com.example.platform.shared.cost.CostEstimationPort.CostEstimate;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EntitlementPolicyService implements EntitlementPort {

    private static final Logger log = LoggerFactory.getLogger(EntitlementPolicyService.class);

    private final ConcurrentHashMap<String, String> userTiers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EntitlementPolicy> customPolicies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<FeatureFlag>> featureFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> decisionSources = new ConcurrentHashMap<>();
    private final CustomPolicyRepository customPolicyRepository;
    private CostEstimationPort costEstimationPort;
    private BudgetGuardPort budgetGuardPort;

    public EntitlementPolicyService(
            @Autowired(required = false) CustomPolicyRepository customPolicyRepository) {
        this.customPolicyRepository = customPolicyRepository;
        initializeDefaultTiers();
        initializeDefaultFeatureFlags();
    }

    public void setCostEstimationPort(CostEstimationPort port) {
        this.costEstimationPort = port;
    }

    public void setBudgetGuardPort(BudgetGuardPort port) {
        this.budgetGuardPort = port;
    }

    private void initializeDefaultTiers() {
        userTiers.put("tenant-1", "FREE");
        userTiers.put("tenant-pro", "PRO");
        userTiers.put("tenant-team", "TEAM");
        userTiers.put("tenant-enterprise", "ENTERPRISE");
    }

    private void initializeDefaultFeatureFlags() {
        featureFlags.put("FREE", List.of(
                new FeatureFlag("watermark", "Watermark", true, "TIER", "FREE", "Adds watermark to exports"),
                new FeatureFlag("gpu-render", "GPU Rendering", false, "TIER", "FREE", "GPU-accelerated rendering"),
                new FeatureFlag("remote-worker", "Remote Worker", false, "TIER", "FREE", "Remote render worker support")));
        featureFlags.put("PRO", List.of(
                new FeatureFlag("watermark", "Watermark", false, "TIER", "PRO", "No watermark on exports"),
                new FeatureFlag("gpu-render", "GPU Rendering", false, "TIER", "PRO", "GPU-accelerated rendering"),
                new FeatureFlag("remote-worker", "Remote Worker", false, "TIER", "PRO", "Remote render worker support"),
                new FeatureFlag("custom-fonts", "Custom Fonts", true, "TIER", "PRO", "Custom font upload support")));
        featureFlags.put("TEAM", List.of(
                new FeatureFlag("watermark", "Watermark", false, "TIER", "TEAM", "No watermark on exports"),
                new FeatureFlag("gpu-render", "GPU Rendering", true, "TIER", "TEAM", "GPU-accelerated rendering"),
                new FeatureFlag("remote-worker", "Remote Worker", true, "TIER", "TEAM", "Remote render worker support"),
                new FeatureFlag("custom-fonts", "Custom Fonts", true, "TIER", "TEAM", "Custom font upload support")));
        featureFlags.put("ENTERPRISE", List.of(
                new FeatureFlag("watermark", "Watermark", false, "TIER", "ENTERPRISE", "No watermark"),
                new FeatureFlag("gpu-render", "GPU Rendering", true, "TIER", "ENTERPRISE", "GPU-accelerated rendering"),
                new FeatureFlag("remote-worker", "Remote Worker", true, "TIER", "ENTERPRISE", "Remote render worker support"),
                new FeatureFlag("custom-fonts", "Custom Fonts", true, "TIER", "ENTERPRISE", "Custom font upload support"),
                new FeatureFlag("priority-queue", "Priority Queue", true, "TIER", "ENTERPRISE", "Priority render queue")));
        featureFlags.put("EXPERIMENTAL", List.of(
                new FeatureFlag("watermark", "Watermark", false, "TIER", "EXPERIMENTAL", "No watermark"),
                new FeatureFlag("gpu-render", "GPU Rendering", true, "TIER", "EXPERIMENTAL", "GPU-accelerated rendering"),
                new FeatureFlag("remote-worker", "Remote Worker", true, "TIER", "EXPERIMENTAL", "Remote render worker support"),
                new FeatureFlag("custom-fonts", "Custom Fonts", true, "TIER", "EXPERIMENTAL", "Custom font upload support"),
                new FeatureFlag("priority-queue", "Priority Queue", true, "TIER", "EXPERIMENTAL", "Priority render queue"),
                new FeatureFlag("beta-effects", "Beta Effects", true, "TIER", "EXPERIMENTAL", "Experimental effects")));
    }

    public EntitlementPolicy getPolicy(String tenantId) {
        if (customPolicyRepository != null) {
            try {
                Optional<EntitlementPolicy> dbPolicy = customPolicyRepository.findCustomPolicy(tenantId);
                if (dbPolicy.isPresent()) {
                    decisionSources.put(tenantId, "tenant_override");
                    log.debug("Using DB custom policy for tenant: {}", tenantId);
                    return dbPolicy.get();
                }
            } catch (Exception e) {
                log.warn("Failed to load custom policy from DB for {}: {}", tenantId, e.getMessage());
            }
        }

        EntitlementPolicy cached = customPolicies.get(tenantId);
        if (cached != null) {
            decisionSources.put(tenantId, "custom");
            return cached;
        }

        decisionSources.put(tenantId, "tier");
        return EntitlementPolicy.forTier(getTier(tenantId));
    }

    public String getDecisionSource(String tenantId) {
        return decisionSources.getOrDefault(tenantId, "tier");
    }

    public String getTier(String tenantId) {
        return userTiers.getOrDefault(tenantId, "FREE");
    }

    public void setTier(String tenantId, String tier) {
        userTiers.put(tenantId, tier);
        decisionSources.put(tenantId, "tier");
        log.info("EntitlementPolicyService: set tier={} for tenant={}", tier, tenantId);
    }

    public boolean isFeatureEnabled(String tier, String flagKey) {
        List<FeatureFlag> flags = featureFlags.getOrDefault(tier, List.of());
        return flags.stream().anyMatch(f -> f.flagKey().equals(flagKey) && f.isEnabledFor(tier));
    }

    public List<FeatureFlag> getFeatureFlags(String tier) {
        return featureFlags.getOrDefault(tier, List.of());
    }

    public ExportCapabilityPolicy getExportCapabilities(String tenantId) {
        return ExportCapabilityPolicy.forTier(getTier(tenantId));
    }

    public ProviderAccessPolicy getProviderAccess(String tenantId) {
        return ProviderAccessPolicy.forTier(getTier(tenantId));
    }

    public void refreshPolicies() {
        decisionSources.clear();
        if (customPolicyRepository != null) {
            try {
                List<Map<String, Object>> dbPolicies = customPolicyRepository.findAllCustomPolicies();
                log.info("Refreshed {} custom policies from DB", dbPolicies.size());
            } catch (Exception e) {
                log.warn("Failed to refresh custom policies from DB: {}", e.getMessage());
            }
        }
        log.info("Entitlement policies refreshed");
    }

    @Override
    public EntitlementPort.ExportValidationResult validateExport(String tenantId, String userId,
            String requestedPreset, String outputFormat, long estimatedDurationSeconds) {
        String tier = getTier(tenantId);
        EntitlementPolicy policy = getPolicy(tenantId);
        ExportCapabilityPolicy exportPolicy = ExportCapabilityPolicy.forTier(tier);
        ProviderAccessPolicy providerPolicy = ProviderAccessPolicy.forTier(tier);
        String decisionSource = getDecisionSource(tenantId);

        List<String> violations = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        boolean presetAllowed = exportPolicy.isPresetAllowed(requestedPreset);
        if (!presetAllowed) {
            violations.add("PRESET_NOT_ALLOWED");
            recommendations.add("Try a lower preset like 'default_720p' or 'preview_720p'");
        }

        boolean formatAllowed = exportPolicy.isFormatAllowed(outputFormat);
        if (!formatAllowed) {
            violations.add("FORMAT_NOT_ALLOWED");
            recommendations.add("Try a supported format like 'mp4' or 'webm'");
        }

        boolean requestedGpu = requestedPreset.startsWith("gpu_");
        if (requestedGpu && !providerPolicy.gpuAllowed()) {
            violations.add("GPU_NOT_ALLOWED");
            recommendations.add("Upgrade to TEAM tier for GPU access, or use a CPU preset");
        }

        boolean is4k = requestedPreset.contains("4k") || requestedPreset.contains("2160p");
        if (is4k && exportPolicy.maxResolutionHeight() < 2160) {
            violations.add("RESOLUTION_NOT_ALLOWED");
            recommendations.add("Upgrade to TEAM tier for 4K export");
        }

        CostEstimate costEstimate = null;
        if (costEstimationPort != null) {
            String providerKey = selectProvider(requestedPreset, tier);
            boolean useGpu = requestedGpu && providerPolicy.gpuAllowed();
            costEstimate = costEstimationPort.estimate(providerKey, requestedPreset,
                    outputFormat, estimatedDurationSeconds, useGpu);
        }

        BudgetCheckResult budgetResult = null;
        if (budgetGuardPort != null && costEstimate != null) {
            budgetResult = budgetGuardPort.checkBudget(tenantId, costEstimate.estimatedCost());
            if (!budgetResult.allowed()) {
                violations.add("BUDGET_EXCEEDED");
                recommendations.add("Reduce export quality or contact administrator to increase budget");
            } else if (budgetResult.warning()) {
                recommendations.add("Warning: approaching budget limit");
            }
        }

        String recommendedPreset = presetAllowed ? requestedPreset
                : recommendPreset(tier, outputFormat, estimatedDurationSeconds);

        List<String> providerCandidates = findProviderCandidates(requestedPreset, tier);

        boolean allowed = violations.isEmpty();

        EntitlementPort.ExportValidationResult result = new EntitlementPort.ExportValidationResult(
                allowed,
                allowed ? "ALLOWED" : violations.get(0),
                tier,
                requestedPreset,
                recommendedPreset,
                providerCandidates,
                costEstimate != null ? costEstimate.estimatedCost() : 0.0,
                costEstimate != null ? costEstimate.currency() : "USD",
                budgetResult != null ? budgetResult : new BudgetCheckResult(true, false, 0, 0, 0, null),
                buildUpgradeOptions(tier, violations),
                allowed ? "Export request validated successfully"
                        : "Export request denied: " + String.join(", ", violations),
                violations,
                recommendations
        );
        return result;
    }

    public EntitlementDecision validateExportDecision(String tenantId, String userId,
            String requestedPreset, String outputFormat, long estimatedDurationSeconds) {
        EntitlementPort.ExportValidationResult result = validateExport(
                tenantId, userId, requestedPreset, outputFormat, estimatedDurationSeconds);
        String tier = getTier(tenantId);
        return new EntitlementDecision(
                result.allowed(),
                result.allowed() ? "ALLOWED" : "DENY",
                result.allowed() ? "TIER" : "DEFAULT_DENY",
                result.userFriendlyMessage(),
                tier,
                List.of("tier:" + tier),
                null, null, null,
                null, result.recommendedPreset(),
                result.upgradeOptions(),
                null,
                false);
    }

    private String selectProvider(String preset, String tier) {
        if (preset.startsWith("gpu_") && !tier.equals("FREE") && !tier.equals("PRO")) {
            return "remote-javacv";
        }
        if (preset.startsWith("ofx_") && !tier.equals("FREE")) {
            return "ofx";
        }
        return "javacv";
    }

    private String recommendPreset(String tier, String format, long duration) {
        return switch (tier.toUpperCase()) {
            case "FREE" -> "free_720p_watermarked";
            case "PRO" -> "default_1080p";
            case "TEAM" -> "default_1080p";
            case "ENTERPRISE" -> "pro_1080p";
            case "EXPERIMENTAL" -> "default_1080p";
            default -> "preview_720p";
        };
    }

    private List<String> findProviderCandidates(String preset, String tier) {
        ProviderAccessPolicy policy = ProviderAccessPolicy.forTier(tier);
        List<String> candidates = new ArrayList<>();
        if (preset.startsWith("ofx_") && policy.isProviderAllowed("ofx")) {
            candidates.add("ofx");
        }
        if (preset.startsWith("gpu_") && policy.gpuAllowed()) {
            candidates.add("remote-javacv");
        }
        if (policy.isProviderAllowed("javacv")) {
            candidates.add("javacv");
        }
        if (candidates.isEmpty()) {
            candidates.add("javacv");
        }
        return candidates;
    }

    private List<String> buildUpgradeOptions(String currentTier, List<String> violations) {
        List<String> options = new ArrayList<>();
        if (violations.contains("PRESET_NOT_ALLOWED") || violations.contains("RESOLUTION_NOT_ALLOWED")) {
            switch (currentTier.toUpperCase()) {
                case "FREE" -> options.add("Upgrade to PRO for 1080p and more presets");
                case "PRO" -> options.add("Upgrade to TEAM for 4K and GPU rendering");
                case "TEAM" -> options.add("Upgrade to ENTERPRISE for priority rendering");
            }
        }
        if (violations.contains("GPU_NOT_ALLOWED")) {
            options.add("Upgrade to TEAM tier for GPU rendering access");
        }
        if (violations.contains("FORMAT_NOT_ALLOWED")) {
            options.add("Upgrade to PRO for additional export formats");
        }
        return options;
    }
}
