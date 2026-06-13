package com.example.platform.render.infrastructure.evolution;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Evolution Safety Controller - ensures system mutations are safe.
 * 
 * <p>Safety requirements:
 * <ul>
 *   <li>Simulation before apply</li>
 *   <li>Rollback capability</li>
 *   <li>A/B validation window</li>
 *   <li>Mutation approval thresholds</li>
 * </ul>
 */
@Service
public class EvolutionSafetyController {

    private static final Logger log = LoggerFactory.getLogger(EvolutionSafetyController.class);

    // Safety thresholds
    private static final double MAX_RISK_SCORE = 0.8;
    private static final double MAX_WEIGHT_CHANGE = 0.3;
    private static final int MIN_HISTORICAL_SAMPLES = 5;

    /**
     * Validate mutations before applying.
     */
    public SystemMutationGraph validateMutations(
            SystemMutationGraph mutationGraph,
            List<SystemCanonicalGraph> historicalGraphs) {

        SystemMutationGraph validated = mutationGraph;

        for (SystemMutationGraph.MutationNode mutation : mutationGraph.mutations()) {
            ValidationResult result = validateMutation(mutation, historicalGraphs);

            if (result.isValid()) {
                validated = validated.addMutation(new SystemMutationGraph.MutationNode(
                        mutation.mutationId(),
                        mutation.targetSystem(),
                        mutation.mutationType(),
                        mutation.description(),
                        mutation.beforeState(),
                        mutation.afterState(),
                        mutation.justification(),
                        mutation.riskScore(),
                        mutation.proposedAt(),
                        null,
                        SystemMutationGraph.MutationStatus.VALIDATED
                ));
            } else {
                log.warn("Mutation {} rejected: {}", mutation.mutationId(), result.reason());
            }
        }

        return validated.markValidated();
    }

    /**
     * Check if mutations are safe to apply.
     */
    public boolean isSafeToApply(SystemMutationGraph mutationGraph) {
        // Check all mutations are validated
        boolean allValidated = mutationGraph.mutations().stream()
                .allMatch(m -> m.status() == SystemMutationGraph.MutationStatus.VALIDATED);

        if (!allValidated) {
            log.warn("Not all mutations are validated");
            return false;
        }

        // Check risk scores
        boolean anyHighRisk = mutationGraph.mutations().stream()
                .anyMatch(SystemMutationGraph.MutationNode::isHighRisk);

        if (anyHighRisk) {
            log.warn("High risk mutations detected");
            return false;
        }

        // Check total weight changes
        double totalWeightChange = mutationGraph.getMutationsByTarget("SOUL").stream()
                .mapToDouble(this::calculateWeightChange)
                .sum();

        if (totalWeightChange > MAX_WEIGHT_CHANGE) {
            log.warn("Total weight change {} exceeds maximum {}", totalWeightChange, MAX_WEIGHT_CHANGE);
            return false;
        }

        return true;
    }

    /**
     * Simulate mutation effects.
     */
    public SimulationResult simulate(
            SystemMutationGraph mutationGraph,
            List<SystemCanonicalGraph> historicalGraphs) {

        // Simplified simulation
        double expectedImprovement = 0;
        double riskScore = 0;

        for (SystemMutationGraph.MutationNode mutation : mutationGraph.mutations()) {
            expectedImprovement += estimateImprovement(mutation);
            riskScore = Math.max(riskScore, mutation.riskScore());
        }

        return new SimulationResult(
                expectedImprovement,
                riskScore,
                expectedImprovement > riskScore,
                String.format("Expected improvement: %.2f%%, Risk: %.2f%%",
                        expectedImprovement * 100, riskScore * 100)
        );
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private ValidationResult validateMutation(
            SystemMutationGraph.MutationNode mutation,
            List<SystemCanonicalGraph> historicalGraphs) {

        // Check risk score
        if (mutation.riskScore() > MAX_RISK_SCORE) {
            return new ValidationResult(false, "Risk score too high: " + mutation.riskScore());
        }

        // Check historical data
        if (historicalGraphs.size() < MIN_HISTORICAL_SAMPLES) {
            return new ValidationResult(false, "Insufficient historical data");
        }

        // Check weight changes
        if (mutation.mutationType() == SystemMutationGraph.MutationType.WEIGHT_ADJUSTMENT) {
            double weightChange = calculateWeightChange(mutation);
            if (weightChange > MAX_WEIGHT_CHANGE) {
                return new ValidationResult(false, "Weight change too large: " + weightChange);
            }
        }

        return new ValidationResult(true, "Validation passed");
    }

    private double calculateWeightChange(SystemMutationGraph.MutationNode mutation) {
        Map<String, Object> before = mutation.beforeState();
        Map<String, Object> after = mutation.afterState();

        double totalChange = 0;
        for (String key : new String[]{"costWeight", "speedWeight", "qualityWeight", "reliabilityWeight"}) {
            Object beforeVal = before.get(key);
            Object afterVal = after.get(key);
            if (beforeVal instanceof Number b && afterVal instanceof Number a) {
                totalChange += Math.abs(a.doubleValue() - b.doubleValue());
            }
        }
        return totalChange;
    }

    private double estimateImprovement(SystemMutationGraph.MutationNode mutation) {
        // Simplified improvement estimation
        return switch (mutation.mutationType()) {
            case WEIGHT_ADJUSTMENT -> 0.1;
            case PROVIDER_SCORE_UPDATE -> 0.05;
            case POLICY_UPDATE -> 0.08;
            case STRATEGY_UPDATE -> 0.12;
            case THRESHOLD_ADJUSTMENT -> 0.03;
            case RULE_CHANGE -> 0.15;
        };
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ValidationResult(
            boolean isValid,
            String reason
    ) {}

    public record SimulationResult(
            double expectedImprovement,
            double riskScore,
            boolean isSafe,
            String summary
    ) {}
}
