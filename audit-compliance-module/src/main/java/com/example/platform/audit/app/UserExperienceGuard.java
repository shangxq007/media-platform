package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Guards user experience during anomaly mitigation.
 * Ensures: no cancellation of running jobs, graceful degradation, clear communication.
 */
@Service
public class UserExperienceGuard {

    private static final Logger log = LoggerFactory.getLogger(UserExperienceGuard.class);

    private final UsageAnomalyDetectionService anomalyDetectionService;

    public UserExperienceGuard(UsageAnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    /**
     * Determine the appropriate mitigation action for a user.
     * Never cancels running jobs. Always provides alternatives.
     */
    public ExperienceGuardResult guard(String tenantId, String userId, String tier,
            List<String> anomalies, List<UsageMitigationAction> proposedActions) {
        boolean isHighValue = "ENTERPRISE".equalsIgnoreCase(tier) || "EXPERIMENTAL".equalsIgnoreCase(tier);

        // Never cancel running jobs
        boolean allowNewJobs = true;
        String recommendedPreset = null;
        String userMessage = null;
        String action = "ALLOW";

        if (anomalies.isEmpty()) {
            return new ExperienceGuardResult(true, false, null, null,
                    "All clear", "ALLOW");
        }

        // Determine the most appropriate action
        boolean hasHardBlock = proposedActions.stream()
                .anyMatch(a -> a.actionType().equals(UsageMitigationAction.ACTION_HARD_BLOCK));
        boolean hasDegrade = proposedActions.stream()
                .anyMatch(a -> a.actionType().equals(UsageMitigationAction.ACTION_DEGRADE));
        boolean hasSoftLimit = proposedActions.stream()
                .anyMatch(a -> a.actionType().equals(UsageMitigationAction.ACTION_SOFT_LIMIT));

        if (hasHardBlock && isHighValue) {
            // High-value users: require review instead of blocking
            allowNewJobs = true;
            action = "REVIEW";
            userMessage = "Your account has been flagged for review. You can continue using the service while our team reviews your usage.";
            recommendedPreset = proposedActions.stream()
                    .filter(a -> a.targetPreset() != null)
                    .map(UsageMitigationAction::targetPreset)
                    .findFirst().orElse(null);
        } else if (hasHardBlock) {
            // Regular users: degrade instead of blocking
            allowNewJobs = true;
            action = "DEGRADE";
            userMessage = "We've noticed unusual usage patterns. Your exports have been temporarily adjusted to a lower quality preset to ensure service stability.";
            recommendedPreset = proposedActions.stream()
                    .filter(a -> a.targetPreset() != null)
                    .map(UsageMitigationAction::targetPreset)
                    .findFirst().orElse("default_720p");
        } else if (hasDegrade) {
            allowNewJobs = true;
            action = "DEGRADE";
            userMessage = "Your export quality has been temporarily adjusted for optimal performance.";
            recommendedPreset = proposedActions.stream()
                    .filter(a -> a.targetPreset() != null)
                    .map(UsageMitigationAction::targetPreset)
                    .findFirst().orElse(null);
        } else if (hasSoftLimit) {
            allowNewJobs = true;
            action = "SOFT_LIMIT";
            userMessage = "You're approaching your usage limits. Consider upgrading your plan for uninterrupted service.";
        } else {
            action = "WARN";
            userMessage = "We've noticed increased usage. Everything is working normally.";
        }

        log.info("UserExperienceGuard: tenant={} tier={} action={} allowJobs={}",
                tenantId, tier, action, allowNewJobs);

        return new ExperienceGuardResult(allowNewJobs, !anomalies.isEmpty(),
                recommendedPreset, userMessage, userMessage, action);
    }

    public record ExperienceGuardResult(
            boolean allowNewJobs,
            boolean hasAnomaly,
            String recommendedPreset,
            String userMessage,
            String userFriendlyMessage,
            String action) {}
}
