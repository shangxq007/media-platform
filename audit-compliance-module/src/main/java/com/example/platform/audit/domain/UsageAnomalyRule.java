package com.example.platform.audit.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Rule for detecting usage anomalies.
 */
public record UsageAnomalyRule(
        String ruleId,
        String ruleType,
        String name,
        String description,
        String severity,
        Map<String, Object> thresholds,
        boolean enabled) {

    public static UsageAnomalyRule renderBurst() {
        return new UsageAnomalyRule("rule-render-burst", "render_burst",
                "Render Burst Detection",
                "Detects when a user submits an unusually high number of render jobs in a short period",
                "MEDIUM", Map.of("maxJobsPerHour", 10, "maxJobsPerMinute", 3), true);
    }

    public static UsageAnomalyRule repeatedFailures() {
        return new UsageAnomalyRule("rule-repeated-failures", "repeated_render_failures",
                "Repeated Render Failure Detection",
                "Detects when a user has multiple consecutive render failures",
                "LOW", Map.of("maxConsecutiveFailures", 5), true);
    }

    public static UsageAnomalyRule gpuCostSpike() {
        return new UsageAnomalyRule("rule-gpu-cost-spike", "gpu_cost_spike",
                "GPU Cost Spike Detection",
                "Detects abnormal GPU cost increases",
                "HIGH", Map.of("maxGpuCostIncreasePercent", 200.0), true);
    }

    public static UsageAnomalyRule remoteWorkerAbuse() {
        return new UsageAnomalyRule("rule-remote-worker-abuse", "remote_worker_abuse",
                "Remote Worker Abuse Detection",
                "Detects potential abuse of remote worker resources",
                "HIGH", Map.of("maxRemoteJobsPerHour", 20), true);
    }

    public static UsageAnomalyRule storageEgressSpike() {
        return new UsageAnomalyRule("rule-storage-egress-spike", "storage_egress_spike",
                "Storage/Egress Spike Detection",
                "Detects abnormal storage or egress usage",
                "MEDIUM", Map.of("maxEgressGbPerDay", 100.0), true);
    }

    public static UsageAnomalyRule aiProviderSpike() {
        return new UsageAnomalyRule("rule-ai-provider-spike", "ai_provider_spike",
                "AI Provider Spike Detection",
                "Detects abnormal AI provider usage",
                "MEDIUM", Map.of("maxAiCallsPerHour", 100), true);
    }

    public static UsageAnomalyRule subtitleFontAbuse() {
        return new UsageAnomalyRule("rule-subtitle-font-abuse", "subtitle_font_upload_abuse",
                "Subtitle Font Upload Abuse",
                "Detects excessive font uploads",
                "LOW", Map.of("maxFontUploadsPerDay", 10), true);
    }

    public static UsageAnomalyRule apiKeyMultiRegion() {
        return new UsageAnomalyRule("rule-api-key-multi-region", "api_key_multi_region_spike",
                "API Key Multi-Region Usage",
                "Detects API key usage from multiple regions simultaneously",
                "HIGH", Map.of("maxRegionsPerHour", 3), true);
    }
}
