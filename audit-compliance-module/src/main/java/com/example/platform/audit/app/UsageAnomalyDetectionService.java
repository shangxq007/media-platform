package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import com.example.platform.shared.events.UsageAnomalyDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects usage anomalies and triggers appropriate mitigation actions.
 * Prioritizes user experience: warn first, degrade gracefully, block only as last resort.
 */
@Service
public class UsageAnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(UsageAnomalyDetectionService.class);

    private final ConcurrentHashMap<String, List<UsageAnomalyEvent>> anomalyHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<UsageAlert>> activeAlerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UsageRiskProfile> riskProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<UsageMitigationAction>> mitigationHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> jobCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> costAccumulators = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    public UsageAnomalyDetectionService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Analyze a render job submission for anomaly risk.
     */
    public AnomalyCheckResult analyzeSubmission(String tenantId, String userId,
            String preset, String providerKey) {
        String userKey = tenantId + ":" + userId;
        List<String> detectedAnomalies = new ArrayList<>();
        List<UsageMitigationAction> actions = new ArrayList<>();
        double maxScore = 0.0;

        // Check render burst
        int recentJobs = incrementAndGetJobCount(userKey);
        if (recentJobs > 10) {
            detectedAnomalies.add("render_burst");
            double score = Math.min(1.0, recentJobs / 20.0);
            maxScore = Math.max(maxScore, score);
            actions.add(createAction(tenantId, userId, "render_burst",
                    score > 0.7 ? UsageMitigationAction.ACTION_SOFT_LIMIT : UsageMitigationAction.ACTION_WARN,
                    "High render job frequency detected", null));
        }

        // Check GPU cost spike
        if (preset.startsWith("gpu_")) {
            double currentCost = costAccumulators.getOrDefault(userKey + ":gpu", 0.0) + 1.0;
            costAccumulators.put(userKey + ":gpu", currentCost);
            if (currentCost > 5.0) {
                detectedAnomalies.add("gpu_cost_spike");
                maxScore = Math.max(maxScore, 0.6);
                actions.add(createAction(tenantId, userId, "gpu_cost_spike",
                        UsageMitigationAction.ACTION_WARN,
                        "GPU usage is higher than typical", "default_1080p"));
            }
        }

        // Check remote worker abuse
        if (providerKey.startsWith("remote")) {
            int remoteJobs = jobCounts.getOrDefault(userKey + ":remote", 0) + 1;
            jobCounts.put(userKey + ":remote", remoteJobs);
            if (remoteJobs > 20) {
                detectedAnomalies.add("remote_worker_abuse");
                maxScore = Math.max(maxScore, 0.8);
                actions.add(createAction(tenantId, userId, "remote_worker_abuse",
                        UsageMitigationAction.ACTION_DEGRADE,
                        "Remote worker usage is very high", "javacv"));
            }
        }

        // Apply user experience protection
        String tier = resolveTier(tenantId);
        actions = applyExperienceProtection(tenantId, userId, tier, detectedAnomalies, actions);

        // Emit events and record
        for (UsageMitigationAction action : actions) {
            recordMitigation(tenantId, userId, action);
        }

        if (!detectedAnomalies.isEmpty()) {
            UsageAnomalyEvent event = new UsageAnomalyEvent(
                    java.util.UUID.randomUUID().toString(),
                    tenantId, userId, String.join(",", detectedAnomalies),
                    "Anomaly Detection", UsageAnomalyScore.riskLevel(maxScore),
                    actions.isEmpty() ? "OBSERVE" : actions.get(0).actionType(),
                    maxScore, Map.of("anomalies", detectedAnomalies),
                    OffsetDateTime.now());
            recordAnomaly(event);
            eventPublisher.publishEvent(new com.example.platform.shared.events.UsageAnomalyDetectedEvent(
                    event.eventId(), tenantId, userId, event.ruleType(),
                    event.severity(), event.action(), event.score(),
                    event.context(), java.time.Instant.now()));
        }

        updateRiskProfile(tenantId, userId, maxScore, detectedAnomalies);

        return new AnomalyCheckResult(detectedAnomalies.isEmpty(), detectedAnomalies,
                maxScore, actions, buildRecommendedPreset(tier, detectedAnomalies));
    }

    /**
     * Record a render failure for anomaly tracking.
     */
    public void recordFailure(String tenantId, String userId, String errorType) {
        String userKey = tenantId + ":" + userId;
        int failures = failureCounts.getOrDefault(userKey, 0) + 1;
        failureCounts.put(userKey, failures);

        if (failures >= 5) {
            log.warn("UsageAnomalyDetectionService: repeated failures for user={}/{}: {}",
                    tenantId, userId, failures);
            UsageAnomalyEvent event = new UsageAnomalyEvent(
                    java.util.UUID.randomUUID().toString(),
                    tenantId, userId, "repeated_render_failures",
                    "Repeated Failure Detection", "LOW",
                    UsageMitigationAction.ACTION_WARN,
                    Math.min(1.0, failures / 10.0),
                    Map.of("consecutiveFailures", failures),
                    OffsetDateTime.now());
            recordAnomaly(event);
        }
    }

    /**
     * Apply user experience protection: prioritize warn/degrade over block.
     */
    private List<UsageMitigationAction> applyExperienceProtection(String tenantId, String userId,
            String tier, List<String> anomalies, List<UsageMitigationAction> proposedActions) {
        List<UsageMitigationAction> protectedActions = new ArrayList<>();

        boolean isHighValue = "ENTERPRISE".equalsIgnoreCase(tier) || "EXPERIMENTAL".equalsIgnoreCase(tier);

        for (UsageMitigationAction action : proposedActions) {
            if (isHighValue && action.actionType().equals(UsageMitigationAction.ACTION_HARD_BLOCK)) {
                // High-value users go to review instead of block
                protectedActions.add(new UsageMitigationAction(
                        action.actionId(), tenantId, userId, action.ruleType(),
                        UsageMitigationAction.ACTION_REQUIRE_REVIEW,
                        "High-value user: escalated to manual review instead of auto-block",
                        action.targetPreset(), false, OffsetDateTime.now()));
            } else if (action.actionType().equals(UsageMitigationAction.ACTION_HARD_BLOCK)) {
                // Regular users get degrade instead of block
                protectedActions.add(new UsageMitigationAction(
                        action.actionId(), tenantId, userId, action.ruleType(),
                        UsageMitigationAction.ACTION_DEGRADE,
                        "Auto-degraded to protect service quality",
                        action.targetPreset() != null ? action.targetPreset() : "default_720p",
                        false, OffsetDateTime.now()));
            } else {
                protectedActions.add(action);
            }
        }

        return protectedActions;
    }

    private String resolveTier(String tenantId) {
        // Default tier resolution - in production this would use TenantContext or a tier service
        if (tenantId.startsWith("enterprise")) return "ENTERPRISE";
        if (tenantId.startsWith("team")) return "TEAM";
        if (tenantId.startsWith("pro")) return "PRO";
        if (tenantId.startsWith("experimental")) return "EXPERIMENTAL";
        return "FREE";
    }

    private UsageMitigationAction createAction(String tenantId, String userId, String ruleType,
            String actionType, String reason, String targetPreset) {
        return new UsageMitigationAction(
                java.util.UUID.randomUUID().toString(),
                tenantId, userId, ruleType, actionType, reason, targetPreset,
                false, OffsetDateTime.now());
    }

    private void recordAnomaly(UsageAnomalyEvent event) {
        String key = event.tenantId() + ":" + event.userId();
        anomalyHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
    }

    private void recordMitigation(String tenantId, String userId, UsageMitigationAction action) {
        String key = tenantId + ":" + userId;
        mitigationHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(action);
    }

    private void updateRiskProfile(String tenantId, String userId, double score, List<String> anomalies) {
        String key = tenantId + ":" + userId;
        String riskLevel = UsageAnomalyScore.riskLevel(score);
        List<String> recentMitigations = mitigationHistory.getOrDefault(key, List.of()).stream()
                .map(UsageMitigationAction::actionType).toList();
        UsageRiskProfile profile = new UsageRiskProfile(
                tenantId, userId, score, riskLevel, anomalies,
                recentMitigations, Map.of(), OffsetDateTime.now());
        riskProfiles.put(key, profile);
    }

    private int incrementAndGetJobCount(String userKey) {
        String hourKey = userKey + ":" + OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        return jobCounts.merge(hourKey, 1, Integer::sum);
    }

    private String buildRecommendedPreset(String tier, List<String> anomalies) {
        if (anomalies.contains("gpu_cost_spike")) {
            return "default_1080p";
        }
        if (anomalies.contains("render_burst")) {
            return "preview_720p";
        }
        return null;
    }

    public UsageRiskProfile getRiskProfile(String tenantId, String userId) {
        return riskProfiles.get(tenantId + ":" + userId);
    }

    public List<UsageAlert> getActiveAlerts(String tenantId, String userId) {
        return activeAlerts.getOrDefault(tenantId + ":" + userId, List.of());
    }

    public record AnomalyCheckResult(
            boolean clean,
            List<String> detectedAnomalies,
            double riskScore,
            List<UsageMitigationAction> mitigationActions,
            String recommendedPreset) {}
}
