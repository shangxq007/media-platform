package com.example.platform.render.infrastructure.evolution;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Meta Learning Loop - learns from historical data to generate system-level
 * improvement suggestions.
 * 
 * <p>Learns:
 * <ul>
 *   <li>Cost drift over time</li>
 *   <li>Provider reliability changes</li>
 *   <li>User preference shifts</li>
 *   <li>Policy rejection patterns</li>
 * </ul>
 */
@Service
public class MetaLearningLoop {

    private static final Logger log = LoggerFactory.getLogger(MetaLearningLoop.class);

    /**
     * Detect drift in system behavior.
     */
    public SystemEvolutionEngine.DriftReport detectDrift(List<SystemCanonicalGraph> historicalGraphs) {
        if (historicalGraphs.size() < 2) {
            return new SystemEvolutionEngine.DriftReport(
                    false, false, false, false, 0, 0, "Insufficient data for drift detection");
        }

        // Analyze cost drift
        double costDrift = detectCostDrift(historicalGraphs);
        boolean costDriftDetected = costDrift > 0.2; // 20% drift threshold

        // Analyze provider drift
        double providerDrift = detectProviderDrift(historicalGraphs);
        boolean providerDriftDetected = providerDrift > 0.3; // 30% drift threshold

        // Analyze policy drift
        boolean policyDriftDetected = detectPolicyDrift(historicalGraphs);

        // Analyze preference drift
        boolean preferenceDriftDetected = detectPreferenceDrift(historicalGraphs);

        String summary = String.format(
                "Drift detection: cost=%.2f%%, provider=%.2f%%, policy=%s, preference=%s",
                costDrift * 100, providerDrift * 100,
                policyDriftDetected ? "detected" : "stable",
                preferenceDriftDetected ? "detected" : "stable");

        return new SystemEvolutionEngine.DriftReport(
                costDriftDetected, providerDriftDetected,
                policyDriftDetected, preferenceDriftDetected,
                costDrift, providerDrift, summary
        );
    }

    /**
     * Generate improvement suggestions based on historical data.
     */
    public List<ImprovementSuggestion> generateSuggestions(
            List<SystemCanonicalGraph> historicalGraphs,
            SystemEvolutionEngine.DriftReport drift) {

        List<ImprovementSuggestion> suggestions = new ArrayList<>();

        // Cost drift suggestions
        if (drift.costDriftDetected()) {
            suggestions.add(new ImprovementSuggestion(
                    "sug-" + Instant.now().toEpochMilli(),
                    "SOUL",
                    SystemMutationGraph.MutationType.WEIGHT_ADJUSTMENT,
                    "Adjust cost weight due to cost drift",
                    Map.of("costWeight", 0.4),
                    "Cost has drifted " + String.format("%.1f%%", drift.costDriftMagnitude() * 100),
                    0.3
            ));
        }

        // Provider drift suggestions
        if (drift.providerDriftDetected()) {
            suggestions.add(new ImprovementSuggestion(
                    "sug-" + Instant.now().toEpochMilli(),
                    "ProviderRuntime",
                    SystemMutationGraph.MutationType.PROVIDER_SCORE_UPDATE,
                    "Update provider scoring due to performance changes",
                    Map.of("reliabilityWeight", 0.3),
                    "Provider performance has drifted",
                    0.4
            ));
        }

        // Policy drift suggestions
        if (drift.policyDriftDetected()) {
            suggestions.add(new ImprovementSuggestion(
                    "sug-" + Instant.now().toEpochMilli(),
                    "PolicyEngine",
                    SystemMutationGraph.MutationType.POLICY_UPDATE,
                    "Review policy rejection patterns",
                    Map.of(),
                    "Policy rejection patterns have shifted",
                    0.5
            ));
        }

        // General optimization suggestions
        if (historicalGraphs.size() >= 10) {
            suggestions.add(new ImprovementSuggestion(
                    "sug-" + Instant.now().toEpochMilli(),
                    "SOUL",
                    SystemMutationGraph.MutationType.STRATEGY_UPDATE,
                    "Optimize execution strategy based on historical data",
                    Map.of("strategyUpdate", true),
                    "Sufficient historical data for strategy optimization",
                    0.2
            ));
        }

        return suggestions;
    }

    // ---------------------------------------------------------------------------
    // Drift Detection Methods
    // ---------------------------------------------------------------------------

    private double detectCostDrift(List<SystemCanonicalGraph> graphs) {
        if (graphs.size() < 2) return 0;

        // Compare first half vs second half average costs
        int mid = graphs.size() / 2;
        double firstHalfAvg = graphs.subList(0, mid).stream()
                .flatMap(g -> g.getBillingDecisionEvents().stream())
                .mapToDouble(e -> e.getDoublePayload("estimatedCost", 0))
                .average()
                .orElse(0);

        double secondHalfAvg = graphs.subList(mid, graphs.size()).stream()
                .flatMap(g -> g.getBillingDecisionEvents().stream())
                .mapToDouble(e -> e.getDoublePayload("estimatedCost", 0))
                .average()
                .orElse(0);

        if (firstHalfAvg == 0) return 0;
        return Math.abs(secondHalfAvg - firstHalfAvg) / firstHalfAvg;
    }

    private double detectProviderDrift(List<SystemCanonicalGraph> graphs) {
        if (graphs.size() < 2) return 0;

        // Track provider usage changes
        Map<String, Long> earlyProviders = graphs.subList(0, graphs.size() / 2).stream()
                .flatMap(g -> g.getProviderDecisionEvents().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getStringPayload("selectedProvider", "unknown"),
                        java.util.stream.Collectors.counting()));

        Map<String, Long> lateProviders = graphs.subList(graphs.size() / 2, graphs.size()).stream()
                .flatMap(g -> g.getProviderDecisionEvents().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getStringPayload("selectedProvider", "unknown"),
                        java.util.stream.Collectors.counting()));

        // Calculate distribution change
        long totalEarly = earlyProviders.values().stream().mapToLong(Long::longValue).sum();
        long totalLate = lateProviders.values().stream().mapToLong(Long::longValue).sum();

        if (totalEarly == 0 || totalLate == 0) return 0;

        double maxDiff = 0;
        for (String provider : java.util.stream.Stream.concat(
                earlyProviders.keySet().stream(), lateProviders.keySet().stream()).distinct().toList()) {
            double earlyRatio = earlyProviders.getOrDefault(provider, 0L) / (double) totalEarly;
            double lateRatio = lateProviders.getOrDefault(provider, 0L) / (double) totalLate;
            maxDiff = Math.max(maxDiff, Math.abs(lateRatio - earlyRatio));
        }

        return maxDiff;
    }

    private boolean detectPolicyDrift(List<SystemCanonicalGraph> graphs) {
        if (graphs.size() < 5) return false;

        // Check if rejection rate has changed significantly
        long earlyRejections = graphs.subList(0, graphs.size() / 2).stream()
                .flatMap(g -> g.getBillingDecisionEvents().stream())
                .filter(e -> "DENY".equals(e.getStringPayload("decision", "")))
                .count();

        long lateRejections = graphs.subList(graphs.size() / 2, graphs.size()).stream()
                .flatMap(g -> g.getBillingDecisionEvents().stream())
                .filter(e -> "DENY".equals(e.getStringPayload("decision", "")))
                .count();

        long earlyTotal = graphs.subList(0, graphs.size() / 2).stream()
                .flatMap(g -> g.getBillingDecisionEvents().stream())
                .count();

        long lateTotal = graphs.subList(graphs.size() / 2, graphs.size()).stream()
                .flatMap(g -> g.getBillingDecisionEvents().stream())
                .count();

        if (earlyTotal == 0 || lateTotal == 0) return false;

        double earlyRate = (double) earlyRejections / earlyTotal;
        double lateRate = (double) lateRejections / lateTotal;

        return Math.abs(lateRate - earlyRate) > 0.1; // 10% change threshold
    }

    private boolean detectPreferenceDrift(List<SystemCanonicalGraph> graphs) {
        // Simplified: check if provider preferences have changed
        return detectProviderDrift(graphs) > 0.2;
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ImprovementSuggestion(
            String suggestionId,
            String targetSystem,
            SystemMutationGraph.MutationType mutationType,
            String description,
            Map<String, Object> proposedChange,
            String justification,
            double riskScore
    ) {}
}
