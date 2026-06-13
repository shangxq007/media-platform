package com.example.platform.render.infrastructure.soul;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preference Learning Layer - learns user preferences from historical behavior.
 * 
 * <p>Learns from:
 * <ul>
 *   <li>Past render jobs</li>
 *   <li>Cost acceptance patterns</li>
 *   <li>Quality choices</li>
 *   <li>Retry behavior</li>
 * </ul>
 * 
 * <p>Updates GlobalObjectiveFunction weights dynamically.
 */
@Service
public class PreferenceLearningLayer {

    private static final Logger log = LoggerFactory.getLogger(PreferenceLearningLayer.class);

    private final SystemEventBus eventBus;
    private final Map<String, UserPreferenceProfile> profiles = new ConcurrentHashMap<>();

    public PreferenceLearningLayer(SystemEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Learn weights for a tenant based on historical behavior.
     */
    public GlobalObjectiveFunction.ObjectiveWeights learnWeights(String tenantId) {
        UserPreferenceProfile profile = profiles.get(tenantId);
        if (profile == null) {
            log.info("No preference profile for tenant {}, using defaults", tenantId);
            return GlobalObjectiveFunction.ObjectiveWeights.balanced();
        }

        return profile.learnedWeights();
    }

    /**
     * Record a user's decision for learning.
     */
    public void recordDecision(String tenantId, UserDecision decision) {
        UserPreferenceProfile profile = profiles.computeIfAbsent(
                tenantId, id -> new UserPreferenceProfile(id, List.of(),
                        GlobalObjectiveFunction.ObjectiveWeights.balanced()));

        profile = profile.addDecision(decision);
        profiles.put(tenantId, profile);

        // Update learned weights
        GlobalObjectiveFunction.ObjectiveWeights newWeights = calculateWeights(profile);
        profile = profile.withWeights(newWeights);
        profiles.put(tenantId, profile);

        log.debug("Recorded decision for tenant {}: {}", tenantId, decision.type());
    }

    /**
     * Analyze historical events to learn preferences.
     */
    public void analyzeHistory(String tenantId, List<SystemCanonicalEvent> events) {
        log.info("Analyzing {} events for tenant {}", events.size(), tenantId);

        // Analyze cost acceptance
        long costAccepted = events.stream()
                .filter(e -> e.eventType().equals(SystemCanonicalEvent.BILLING_DECISION))
                .filter(e -> "ALLOW".equals(e.getStringPayload("decision", "")))
                .count();

        long costRejected = events.stream()
                .filter(e -> e.eventType().equals(SystemCanonicalEvent.BILLING_DECISION))
                .filter(e -> "DENY".equals(e.getStringPayload("decision", "")))
                .count();

        // Analyze quality choices
        long highQuality = events.stream()
                .filter(e -> e.eventType().equals(SystemCanonicalEvent.PROVIDER_DECISION))
                .filter(e -> {
                    String provider = e.getStringPayload("selectedProvider", "");
                    return "ffmpeg".equals(provider); // FFmpeg = high quality
                })
                .count();

        // Create or update profile
        UserPreferenceProfile profile = profiles.getOrDefault(
                tenantId, new UserPreferenceProfile(tenantId, List.of(),
                        GlobalObjectiveFunction.ObjectiveWeights.balanced()));

        // Update weights based on analysis
        double costSensitivity = costRejected > 0 ? 
                (double) costRejected / (costAccepted + costRejected) : 0.5;
        double qualityPreference = highQuality > 0 ? 0.7 : 0.3;

        GlobalObjectiveFunction.ObjectiveWeights newWeights = new GlobalObjectiveFunction.ObjectiveWeights(
                0.3 + (costSensitivity * 0.2),  // cost weight
                0.2,                              // speed weight
                0.2 + (qualityPreference * 0.1),  // quality weight
                0.2,                              // reliability weight
                0.05,                             // compliance weight
                0.05                              // preference weight
        );

        profile = profile.withWeights(newWeights);
        profiles.put(tenantId, profile);

        log.info("Updated preferences for tenant {}: cost={}, quality={}",
                tenantId, costSensitivity, qualityPreference);
    }

    /**
     * Get user preference profile.
     */
    public UserPreferenceProfile getProfile(String tenantId) {
        return profiles.get(tenantId);
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private GlobalObjectiveFunction.ObjectiveWeights calculateWeights(UserPreferenceProfile profile) {
        List<UserDecision> decisions = profile.decisions();
        if (decisions.isEmpty()) {
            return GlobalObjectiveFunction.ObjectiveWeights.balanced();
        }

        // Analyze decision patterns
        long costFocused = decisions.stream()
                .filter(d -> d.type() == DecisionType.COST_OPTIMIZED)
                .count();

        long qualityFocused = decisions.stream()
                .filter(d -> d.type() == DecisionType.QUALITY_OPTIMIZED)
                .count();

        long speedFocused = decisions.stream()
                .filter(d -> d.type() == DecisionType.SPEED_OPTIMIZED)
                .count();

        double total = decisions.size();
        double costRatio = costFocused / total;
        double qualityRatio = qualityFocused / total;
        double speedRatio = speedFocused / total;

        return new GlobalObjectiveFunction.ObjectiveWeights(
                0.2 + (costRatio * 0.3),
                0.2 + (speedRatio * 0.3),
                0.2 + (qualityRatio * 0.3),
                0.2,
                0.1,
                0.1
        );
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record UserPreferenceProfile(
            String tenantId,
            List<UserDecision> decisions,
            GlobalObjectiveFunction.ObjectiveWeights learnedWeights
    ) {
        public UserPreferenceProfile addDecision(UserDecision decision) {
            List<UserDecision> newDecisions = new java.util.ArrayList<>(decisions);
            newDecisions.add(decision);
            return new UserPreferenceProfile(tenantId, List.copyOf(newDecisions), learnedWeights);
        }

        public UserPreferenceProfile withWeights(GlobalObjectiveFunction.ObjectiveWeights newWeights) {
            return new UserPreferenceProfile(tenantId, decisions, newWeights);
        }
    }

    public record UserDecision(
            String decisionId,
            DecisionType type,
            String providerKey,
            double cost,
            boolean accepted,
            Instant timestamp
    ) {}

    public enum DecisionType {
        COST_OPTIMIZED,
        QUALITY_OPTIMIZED,
        SPEED_OPTIMIZED,
        RELIABILITY_OPTIMIZED,
        DEFAULT
    }
}
