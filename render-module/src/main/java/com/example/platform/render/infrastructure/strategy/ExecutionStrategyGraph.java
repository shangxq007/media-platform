package com.example.platform.render.infrastructure.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Execution Strategy Graph - transforms SystemCanonicalGraph from passive event log
 * into an ACTIVE execution optimization system.
 * 
 * <p>This is a "projection layer" on top of canonical events.
 * It remains read-only from the canonical graph.
 */
public record ExecutionStrategyGraph(
        String strategyId,
        String jobId,
        String tenantId,
        List<StrategyNode> strategyNodes,
        List<OptimizationGoal> optimizationGoals,
        StrategyStatus status,
        double overallConfidence,
        Instant createdAt,
        Instant completedAt,
        Map<String, Object> metadata
) {
    /**
     * Create a new strategy graph.
     */
    public static ExecutionStrategyGraph create(String jobId, String tenantId,
                                                  List<OptimizationGoal> goals) {
        return new ExecutionStrategyGraph(
                "strategy-" + jobId,
                jobId,
                tenantId,
                List.of(),
                goals,
                StrategyStatus.PLANNING,
                0.0,
                Instant.now(),
                null,
                Map.of()
        );
    }

    /**
     * Add a strategy node.
     */
    public ExecutionStrategyGraph addNode(StrategyNode node) {
        List<StrategyNode> newNodes = new java.util.ArrayList<>(strategyNodes);
        newNodes.add(node);
        double avgConfidence = newNodes.stream()
                .mapToDouble(StrategyNode::confidenceScore)
                .average()
                .orElse(0.0);
        return new ExecutionStrategyGraph(
                strategyId, jobId, tenantId,
                List.copyOf(newNodes), optimizationGoals,
                status, avgConfidence, createdAt, completedAt, metadata
        );
    }

    /**
     * Mark strategy as executed.
     */
    public ExecutionStrategyGraph markExecuted() {
        return new ExecutionStrategyGraph(
                strategyId, jobId, tenantId,
                strategyNodes, optimizationGoals,
                StrategyStatus.EXECUTED, overallConfidence,
                createdAt, Instant.now(), metadata
        );
    }

    /**
     * Mark strategy as completed.
     */
    public ExecutionStrategyGraph markCompleted() {
        return new ExecutionStrategyGraph(
                strategyId, jobId, tenantId,
                strategyNodes, optimizationGoals,
                StrategyStatus.COMPLETED, overallConfidence,
                createdAt, Instant.now(), metadata
        );
    }

    /**
     * Get the selected option for a step type.
     */
    public StrategyNode getStepByType(StepType stepType) {
        return strategyNodes.stream()
                .filter(n -> n.stepType() == stepType)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all strategy nodes.
     */
    public List<StrategyNode> getNodes() {
        return strategyNodes;
    }

    /**
     * Get the number of strategy steps.
     */
    public int size() {
        return strategyNodes.size();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record StrategyNode(
            String nodeId,
            StepType stepType,
            List<ExecutionOption> options,
            ExecutionOption selectedOption,
            double confidenceScore,
            String reasoning,
            Instant decidedAt
    ) {
        public StrategyNode selectOption(ExecutionOption option, String reasoning) {
            return new StrategyNode(
                    nodeId, stepType, options, option,
                    option.confidenceScore(), reasoning, Instant.now()
            );
        }
    }

    public record ExecutionOption(
            String optionId,
            String providerKey,
            String preset,
            double estimatedCost,
            long estimatedDurationMs,
            double confidenceScore,
            double qualityScore,
            double reliabilityScore,
            Map<String, Object> metadata
    ) {
        public double getScore(OptimizationGoal goal) {
            return switch (goal) {
                case COST -> 1.0 - (estimatedCost / 10.0); // Normalize
                case SPEED -> 1.0 - (estimatedDurationMs / 600000.0); // Normalize to 10 min
                case QUALITY -> qualityScore;
                case RELIABILITY -> reliabilityScore;
            };
        }
    }

    public enum StepType {
        PROVIDER_SELECTION,
        COST_OPTIMIZATION,
        POLICY_ADAPTATION,
        PARALLEL_EXECUTION,
        RETRY_STRATEGY,
        FALLBACK_PATH
    }

    public enum OptimizationGoal {
        COST,
        SPEED,
        QUALITY,
        RELIABILITY
    }

    public enum StrategyStatus {
        PLANNING,
        PLANNED,
        EXECUTING,
        EXECUTED,
        COMPLETED,
        FAILED
    }
}
