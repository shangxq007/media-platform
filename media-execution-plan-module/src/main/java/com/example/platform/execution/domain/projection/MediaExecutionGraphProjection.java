package com.example.platform.execution.domain;

import java.util.List;

/**
 * Projects an execution plan into a deterministic graph representation.
 *
 * <p>Provides a graph digest that is invariant to insertion order,
 * HashMap iteration, locale, timezone, or machine architecture.
 */
public final class MediaExecutionGraphProjection {

    private final MediaExecutionPlan plan;
    private final List<ExecutionStepId> topologicalOrder;
    private final String graphDigest;

    private MediaExecutionGraphProjection(
            MediaExecutionPlan plan,
            List<ExecutionStepId> topologicalOrder,
            String graphDigest) {
        this.plan = plan;
        this.topologicalOrder = topologicalOrder;
        this.graphDigest = graphDigest;
    }

    /**
     * Creates a graph projection from an execution plan.
     */
    public static MediaExecutionGraphProjection fromPlan(MediaExecutionPlan plan) {
        List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);
        String digest = computeGraphDigest(plan, order);
        return new MediaExecutionGraphProjection(plan, order, digest);
    }

    /**
     * Returns the execution plan.
     */
    public MediaExecutionPlan plan() {
        return plan;
    }

    /**
     * Returns the deterministic topological order of steps.
     */
    public List<ExecutionStepId> topologicalOrder() {
        return topologicalOrder;
    }

    /**
     * Returns the deterministic graph digest.
     */
    public String graphDigest() {
        return graphDigest;
    }

    /**
     * Computes a deterministic graph digest from the plan and topological order.
     */
    private static String computeGraphDigest(MediaExecutionPlan plan, List<ExecutionStepId> order) {
        StringBuilder sb = new StringBuilder("graph{");
        sb.append("plan=").append(plan.digest().value());
        sb.append(",order=[");
        order.stream()
                .map(ExecutionStepId::value)
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append(",adj=[");
        // Build sorted adjacency representation
        for (ExecutionStepId stepId : order) {
            sb.append(stepId.value()).append("->[");
            plan.edges().stream()
                    .filter(e -> e.fromStepId().equals(stepId))
                    .map(ExecutionDependency::toStepId)
                    .map(ExecutionStepId::value)
                    .sorted()
                    .forEach(s -> sb.append(s).append(','));
            sb.append("];");
        }
        sb.append(']');

        sb.append('}');
        return ExecutionPlanCanonicalSerializer.sha256Hex(sb.toString());
    }
}
