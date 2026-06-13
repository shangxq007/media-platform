package com.example.platform.render.infrastructure.anchor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * System Anchor Function - defines core objectives and constraints
 * that the self-modifying system must always respect.
 * 
 * <p>This function is IMMUTABLE and cannot be modified by SEL.
 * It provides the stability boundary for system evolution.
 */
public record SystemAnchorFunction(
        String anchorId,
        List<CoreObjective> coreObjectives,
        List<Constraint> constraints,
        List<ForbiddenTransformation> forbiddenTransformations,
        AnchorThresholds thresholds,
        Instant createdAt
) {
    /**
     * Create the default system anchor function.
     */
    public static SystemAnchorFunction createDefault() {
        return new SystemAnchorFunction(
                "anchor-default",
                List.of(
                        CoreObjective.USER_VALUE_MAXIMIZATION,
                        CoreObjective.SYSTEM_STABILITY,
                        CoreObjective.COST_BOUNDEDNESS,
                        CoreObjective.RELIABILITY_GUARANTEE,
                        CoreObjective.PREDICTABILITY_CONSTRAINT
                ),
                List.of(
                        Constraint.NO_UNBOUNDED_DRIFT,
                        Constraint.NO_MUTATION_OUTSIDE_BOUNDARIES,
                        Constraint.PRESERVE_BEHAVIORAL_CONSISTENCY
                ),
                List.of(
                        ForbiddenTransformation.CANNOT_REMOVE_SAFETY_CONSTRAINTS,
                        ForbiddenTransformation.CANNOT_IGNORE_USER_PREFERENCES,
                        ForbiddenTransformation.CANNOT_MAKE_SYSTEM_UNSTABLE,
                        ForbiddenTransformation.CANNOT_UNBOUNDED_COST_GROWTH
                ),
                AnchorThresholds.defaults(),
                Instant.now()
        );
    }

    /**
     * Evaluate system state against anchor function.
     */
    public AnchorScore evaluate(SystemStateForAnchor state) {
        double userValueScore = evaluateUserValue(state);
        double stabilityScore = evaluateStability(state);
        double costBoundednessScore = evaluateCostBoundedness(state);
        double reliabilityScore = evaluateReliability(state);
        double predictabilityScore = evaluatePredictability(state);

        double totalScore = (userValueScore + stabilityScore + costBoundednessScore +
                reliabilityScore + predictabilityScore) / 5.0;

        boolean constraintsSatisfied = checkConstraints(state);
        boolean withinBoundaries = checkBoundaries(state);

        return new AnchorScore(
                totalScore,
                userValueScore,
                stabilityScore,
                costBoundednessScore,
                reliabilityScore,
                predictabilityScore,
                constraintsSatisfied,
                withinBoundaries,
                totalScore >= thresholds.minAnchorScore() && constraintsSatisfied && withinBoundaries,
                Instant.now()
        );
    }

    /**
     * Check if a mutation is allowed by anchor constraints.
     */
    public boolean isMutationAllowed(MutationProposal proposal) {
        // Check forbidden transformations
        for (ForbiddenTransformation forbidden : forbiddenTransformations) {
            if (violatesForbidden(proposal, forbidden)) {
                return false;
            }
        }

        // Check drift limits
        if (proposal.weightChange() > thresholds.maxWeightDrift()) {
            return false;
        }

        // Check if mutation would violate constraints
        for (Constraint constraint : constraints) {
            if (violatesConstraint(proposal, constraint)) {
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------------------------
    // Evaluation Methods
    // ---------------------------------------------------------------------------

    private double evaluateUserValue(SystemStateForAnchor state) {
        // User value is measured by successful completions and satisfaction
        return state.successRate();
    }

    private double evaluateStability(SystemStateForAnchor state) {
        // Stability is measured by consistent behavior over time
        return 1.0 - state.behaviorVariance();
    }

    private double evaluateCostBoundedness(SystemStateForAnchor state) {
        // Cost should stay within reasonable bounds
        double costDrift = state.costDriftMagnitude();
        return Math.max(0, 1.0 - costDrift);
    }

    private double evaluateReliability(SystemStateForAnchor state) {
        // Reliability is measured by provider success rates
        return state.providerReliability();
    }

    private double evaluatePredictability(SystemStateForAnchor state) {
        // Predictability is measured by consistent outcomes
        return 1.0 - state.outcomeVariance();
    }

    private boolean checkConstraints(SystemStateForAnchor state) {
        // Check all constraints are satisfied
        return state.costDriftMagnitude() < thresholds.maxCostDrift() &&
               state.behaviorVariance() < thresholds.maxBehaviorVariance();
    }

    private boolean checkBoundaries(SystemStateForAnchor state) {
        // Check system is within operational boundaries
        return state.successRate() >= thresholds.minSuccessRate() &&
               state.providerReliability() >= thresholds.minReliability();
    }

    private boolean violatesForbidden(MutationProposal proposal, ForbiddenTransformation forbidden) {
        return switch (forbidden) {
            case CANNOT_REMOVE_SAFETY_CONSTRAINTS -> 
                proposal.targetSystem().equals("SafetyController");
            case CANNOT_IGNORE_USER_PREFERENCES -> 
                proposal.mutationType().equals("REMOVE_PREFERENCE_WEIGHT");
            case CANNOT_MAKE_SYSTEM_UNSTABLE -> 
                proposal.riskScore() > 0.9;
            case CANNOT_UNBOUNDED_COST_GROWTH -> 
                proposal.weightChange() > 0.5;
        };
    }

    private boolean violatesConstraint(MutationProposal proposal, Constraint constraint) {
        return switch (constraint) {
            case NO_UNBOUNDED_DRIFT -> proposal.weightChange() > thresholds.maxWeightDrift();
            case NO_MUTATION_OUTSIDE_BOUNDARIES -> !proposal.withinBoundaries();
            case PRESERVE_BEHAVIORAL_CONSISTENCY -> proposal.behaviorChange() > thresholds.maxBehaviorChange();
        };
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public enum CoreObjective {
        USER_VALUE_MAXIMIZATION,
        SYSTEM_STABILITY,
        COST_BOUNDEDNESS,
        RELIABILITY_GUARANTEE,
        PREDICTABILITY_CONSTRAINT
    }

    public enum Constraint {
        NO_UNBOUNDED_DRIFT,
        NO_MUTATION_OUTSIDE_BOUNDARIES,
        PRESERVE_BEHAVIORAL_CONSISTENCY
    }

    public enum ForbiddenTransformation {
        CANNOT_REMOVE_SAFETY_CONSTRAINTS,
        CANNOT_IGNORE_USER_PREFERENCES,
        CANNOT_MAKE_SYSTEM_UNSTABLE,
        CANNOT_UNBOUNDED_COST_GROWTH
    }

    public record AnchorThresholds(
            double minAnchorScore,
            double maxWeightDrift,
            double maxCostDrift,
            double maxBehaviorVariance,
            double maxBehaviorChange,
            double minSuccessRate,
            double minReliability
    ) {
        public static AnchorThresholds defaults() {
            return new AnchorThresholds(0.7, 0.3, 0.5, 0.3, 0.2, 0.8, 0.85);
        }
    }

    public record AnchorScore(
            double totalScore,
            double userValueScore,
            double stabilityScore,
            double costBoundednessScore,
            double reliabilityScore,
            double predictabilityScore,
            boolean constraintsSatisfied,
            boolean withinBoundaries,
            boolean isStable,
            Instant evaluatedAt
    ) {
        public String getSummary() {
            return String.format("Anchor: %.3f (user=%.2f, stability=%.2f, cost=%.2f, reliability=%.2f, predict=%.2f) %s",
                    totalScore, userValueScore, stabilityScore, costBoundednessScore,
                    reliabilityScore, predictabilityScore, isStable ? "STABLE" : "UNSTABLE");
        }
    }

    public record SystemStateForAnchor(
            double successRate,
            double behaviorVariance,
            double costDriftMagnitude,
            double providerReliability,
            double outcomeVariance,
            Map<String, Object> metadata
    ) {}

    public record MutationProposal(
            String proposalId,
            String targetSystem,
            String mutationType,
            double weightChange,
            double riskScore,
            double behaviorChange,
            boolean withinBoundaries,
            String justification
    ) {}
}
