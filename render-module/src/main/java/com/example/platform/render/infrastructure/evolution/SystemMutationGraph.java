package com.example.platform.render.infrastructure.evolution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * System Mutation Graph - represents changes to the system itself.
 * 
 * <p>Each mutation is traceable and reversible.
 */
public record SystemMutationGraph(
        String graphId,
        List<MutationNode> mutations,
        MutationStatus status,
        Instant createdAt,
        Instant completedAt,
        Map<String, Object> metadata
) {
    /**
     * Create a new mutation graph.
     */
    public static SystemMutationGraph create() {
        return new SystemMutationGraph(
                "mutgraph-" + Instant.now().toEpochMilli(),
                List.of(),
                MutationStatus.PROPOSED,
                Instant.now(),
                null,
                Map.of()
        );
    }

    /**
     * Add a mutation node.
     */
    public SystemMutationGraph addMutation(MutationNode mutation) {
        List<MutationNode> newMutations = new java.util.ArrayList<>(mutations);
        newMutations.add(mutation);
        return new SystemMutationGraph(
                graphId, List.copyOf(newMutations), status,
                createdAt, completedAt, metadata
        );
    }

    /**
     * Mark as validated.
     */
    public SystemMutationGraph markValidated() {
        return new SystemMutationGraph(
                graphId, mutations, MutationStatus.VALIDATED,
                createdAt, completedAt, metadata
        );
    }

    /**
     * Mark as applied.
     */
    public SystemMutationGraph markApplied() {
        return new SystemMutationGraph(
                graphId, mutations, MutationStatus.APPLIED,
                createdAt, Instant.now(), metadata
        );
    }

    /**
     * Mark as rolled back.
     */
    public SystemMutationGraph markRolledBack() {
        return new SystemMutationGraph(
                graphId, mutations, MutationStatus.ROLLED_BACK,
                createdAt, Instant.now(), metadata
        );
    }

    /**
     * Get mutations by target system.
     */
    public List<MutationNode> getMutationsByTarget(String targetSystem) {
        return mutations.stream()
                .filter(m -> m.targetSystem().equals(targetSystem))
                .toList();
    }

    /**
     * Get the number of mutations.
     */
    public int size() {
        return mutations.size();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record MutationNode(
            String mutationId,
            String targetSystem,
            MutationType mutationType,
            String description,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String justification,
            double riskScore,
            Instant proposedAt,
            Instant appliedAt,
            MutationStatus status
    ) {
        public MutationNode apply() {
            return new MutationNode(
                    mutationId, targetSystem, mutationType, description,
                    beforeState, afterState, justification, riskScore,
                    proposedAt, Instant.now(), MutationStatus.APPLIED
            );
        }

        public MutationNode rollback() {
            return new MutationNode(
                    mutationId, targetSystem, mutationType, description,
                    afterState, beforeState, "Rollback: " + justification, riskScore,
                    proposedAt, Instant.now(), MutationStatus.ROLLED_BACK
            );
        }

        public boolean isHighRisk() {
            return riskScore > 0.7;
        }
    }

    public enum MutationType {
        WEIGHT_ADJUSTMENT,
        RULE_CHANGE,
        STRATEGY_UPDATE,
        THRESHOLD_ADJUSTMENT,
        PROVIDER_SCORE_UPDATE,
        POLICY_UPDATE
    }

    public enum MutationStatus {
        PROPOSED,
        VALIDATED,
        APPROVED,
        APPLIED,
        REJECTED,
        ROLLED_BACK
    }
}
