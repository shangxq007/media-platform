package com.example.platform.render.infrastructure.soul;

import java.time.Instant;
import java.util.Map;

/**
 * Global Objective Function - unifies ALL decision systems under a single
 * global optimization function.
 * 
 * <p>This function evaluates execution paths across all dimensions:
 * cost, speed, quality, reliability, compliance, and user preferences.
 */
public record GlobalObjectiveFunction(
        String functionId,
        ObjectiveWeights weights,
        Map<String, Object> constraints,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a default objective function.
     */
    public static GlobalObjectiveFunction createDefault() {
        return new GlobalObjectiveFunction(
                "gof-default",
                ObjectiveWeights.balanced(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * Create a cost-optimized objective function.
     */
    public static GlobalObjectiveFunction costOptimized() {
        return new GlobalObjectiveFunction(
                "gof-cost",
                ObjectiveWeights.costOptimized(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * Create a speed-optimized objective function.
     */
    public static GlobalObjectiveFunction speedOptimized() {
        return new GlobalObjectiveFunction(
                "gof-speed",
                ObjectiveWeights.speedOptimized(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * Create a quality-optimized objective function.
     */
    public static GlobalObjectiveFunction qualityOptimized() {
        return new GlobalObjectiveFunction(
                "gof-quality",
                ObjectiveWeights.qualityOptimized(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * Evaluate an execution path against the objective function.
     */
    public ObjectiveScore evaluate(ExecutionPath path) {
        double costScore = evaluateCost(path);
        double speedScore = evaluateSpeed(path);
        double qualityScore = evaluateQuality(path);
        double reliabilityScore = evaluateReliability(path);
        double complianceScore = evaluateCompliance(path);
        double preferenceScore = evaluatePreference(path);

        double totalScore = 
                (costScore * weights.costWeight()) +
                (speedScore * weights.speedWeight()) +
                (qualityScore * weights.qualityWeight()) +
                (reliabilityScore * weights.reliabilityWeight()) +
                (complianceScore * weights.complianceWeight()) +
                (preferenceScore * weights.userPreferenceWeight());

        return new ObjectiveScore(
                totalScore,
                costScore,
                speedScore,
                qualityScore,
                reliabilityScore,
                complianceScore,
                preferenceScore,
                path.providerKey(),
                path.preset(),
                Map.of(
                        "costWeight", weights.costWeight(),
                        "speedWeight", weights.speedWeight(),
                        "qualityWeight", weights.qualityWeight(),
                        "reliabilityWeight", weights.reliabilityWeight()
                )
        );
    }

    /**
     * Update weights based on user preferences.
     */
    public GlobalObjectiveFunction withWeights(ObjectiveWeights newWeights) {
        return new GlobalObjectiveFunction(
                functionId, newWeights, constraints, createdAt, Instant.now()
        );
    }

    /**
     * Add a constraint.
     */
    public GlobalObjectiveFunction withConstraint(String key, Object value) {
        Map<String, Object> newConstraints = new java.util.HashMap<>(constraints);
        newConstraints.put(key, value);
        return new GlobalObjectiveFunction(
                functionId, weights, Map.copyOf(newConstraints), createdAt, Instant.now()
        );
    }

    // ---------------------------------------------------------------------------
    // Evaluation Methods
    // ---------------------------------------------------------------------------

    private double evaluateCost(ExecutionPath path) {
        double cost = path.estimatedCost();
        // Normalize: lower cost = higher score
        return Math.max(0, 1.0 - (cost / 10.0));
    }

    private double evaluateSpeed(ExecutionPath path) {
        long durationMs = path.estimatedDurationMs();
        // Normalize: lower duration = higher score
        return Math.max(0, 1.0 - (durationMs / 600000.0)); // 10 min max
    }

    private double evaluateQuality(ExecutionPath path) {
        return path.qualityScore();
    }

    private double evaluateReliability(ExecutionPath path) {
        return path.reliabilityScore();
    }

    private double evaluateCompliance(ExecutionPath path) {
        // Check policy compliance
        return path.policyCompliant() ? 1.0 : 0.0;
    }

    private double evaluatePreference(ExecutionPath path) {
        return path.preferenceScore();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ObjectiveWeights(
            double costWeight,
            double speedWeight,
            double qualityWeight,
            double reliabilityWeight,
            double complianceWeight,
            double userPreferenceWeight
    ) {
        public static ObjectiveWeights balanced() {
            return new ObjectiveWeights(0.2, 0.2, 0.2, 0.2, 0.1, 0.1);
        }

        public static ObjectiveWeights costOptimized() {
            return new ObjectiveWeights(0.5, 0.15, 0.15, 0.1, 0.05, 0.05);
        }

        public static ObjectiveWeights speedOptimized() {
            return new ObjectiveWeights(0.15, 0.5, 0.15, 0.1, 0.05, 0.05);
        }

        public static ObjectiveWeights qualityOptimized() {
            return new ObjectiveWeights(0.15, 0.15, 0.5, 0.1, 0.05, 0.05);
        }

        public static ObjectiveWeights reliabilityOptimized() {
            return new ObjectiveWeights(0.1, 0.1, 0.1, 0.5, 0.1, 0.1);
        }

        public static ObjectiveWeights complianceOptimized() {
            return new ObjectiveWeights(0.1, 0.1, 0.1, 0.1, 0.5, 0.1);
        }

        public double totalWeight() {
            return costWeight + speedWeight + qualityWeight + 
                   reliabilityWeight + complianceWeight + userPreferenceWeight;
        }
    }

    public record ExecutionPath(
            String providerKey,
            String preset,
            double estimatedCost,
            long estimatedDurationMs,
            double qualityScore,
            double reliabilityScore,
            boolean policyCompliant,
            double preferenceScore,
            Map<String, Object> metadata
    ) {}

    public record ObjectiveScore(
            double totalScore,
            double costScore,
            double speedScore,
            double qualityScore,
            double reliabilityScore,
            double complianceScore,
            double preferenceScore,
            String providerKey,
            String preset,
            Map<String, Object> metadata
    ) {
        public boolean isAcceptable(double threshold) {
            return totalScore >= threshold;
        }

        public String getSummary() {
            return String.format("Score: %.3f (cost=%.2f, speed=%.2f, quality=%.2f, reliability=%.2f)",
                    totalScore, costScore, speedScore, qualityScore, reliabilityScore);
        }
    }
}
