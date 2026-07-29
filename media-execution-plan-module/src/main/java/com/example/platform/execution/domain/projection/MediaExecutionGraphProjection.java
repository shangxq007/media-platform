package com.example.platform.execution.domain.projection;

import com.example.platform.execution.domain.ExecutionDependency;
import com.example.platform.execution.domain.ExecutionPlanCanonicalSerializer;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.MediaExecutionPlan;
import com.example.platform.execution.domain.MediaExecutionStep;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphViews;

import java.util.*;

/**
 * Projects a {@link MediaExecutionPlan} into a {@link DirectedGraphView} of {@link ExecutionStepId}.
 *
 * <p>This adapter bridges the execution plan domain to the platform graph kernel.
 * The graph kernel remains domain-agnostic; this projection lives in the MEP module
 * and translates domain types to the kernel's node type ({@link ExecutionStepId}).
 *
 * <p>The projection is deterministic: same semantic plan → same graph structure,
 * regardless of insertion order.
 */
public final class MediaExecutionGraphProjection {

    private final MediaExecutionPlan plan;
    private final DirectedGraphView<ExecutionStepId> graphView;
    private final String graphDigest;

    private MediaExecutionGraphProjection(MediaExecutionPlan plan, DirectedGraphView<ExecutionStepId> graphView, String graphDigest) {
        this.plan = plan;
        this.graphView = graphView;
        this.graphDigest = graphDigest;
    }

    /**
     * Creates a graph projection from an execution plan.
     *
     * @param plan the execution plan
     * @return a directed graph view over the plan's step IDs
     */
    public static MediaExecutionGraphProjection fromPlan(MediaExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");

        Map<ExecutionStepId, Set<ExecutionStepId>> adjacency = new HashMap<>();
        for (MediaExecutionStep step : plan.steps()) {
            adjacency.put(step.stepId(), new HashSet<>());
        }
        for (ExecutionDependency edge : plan.edges()) {
            adjacency.computeIfAbsent(edge.fromStepId(), k -> new HashSet<>()).add(edge.toStepId());
        }

        // Freeze to immutable
        Map<ExecutionStepId, Set<ExecutionStepId>> frozen = new HashMap<>();
        for (var entry : adjacency.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }

        DirectedGraphView<ExecutionStepId> view = GraphViews.directedFromAdjacency(frozen);
        String digest = computeGraphDigest(plan, view);
        return new MediaExecutionGraphProjection(plan, view, digest);
    }

    /**
     * Returns the execution plan.
     */
    public MediaExecutionPlan plan() {
        return plan;
    }

    /**
     * Returns the directed graph view over step IDs.
     */
    public DirectedGraphView<ExecutionStepId> graphView() {
        return graphView;
    }

    /**
     * Returns the deterministic graph digest.
     */
    public String graphDigest() {
        return graphDigest;
    }

    /**
     * Returns the set of step IDs (nodes in the graph).
     */
    public Set<ExecutionStepId> nodes() {
        return graphView.nodes();
    }

    /**
     * Returns the set of direct successors for a step.
     */
    public Set<ExecutionStepId> successors(ExecutionStepId stepId) {
        return graphView.successors(stepId);
    }

    /**
     * Returns the set of direct predecessors for a step.
     */
    public Set<ExecutionStepId> predecessors(ExecutionStepId stepId) {
        return graphView.predecessors(stepId);
    }

    /**
     * Returns the number of steps (nodes).
     */
    public int nodeCount() {
        return graphView.nodeCount();
    }

    /**
     * Returns the number of dependency edges.
     */
    public int edgeCount() {
        return graphView.edgeCount();
    }

    /**
     * Returns true if the graph contains no steps.
     */
    public boolean isEmpty() {
        return graphView.isEmpty();
    }

    /**
     * Returns true if the graph contains the given step.
     */
    public boolean containsStep(ExecutionStepId stepId) {
        return graphView.containsNode(stepId);
    }

    /**
     * Returns root steps (no incoming edges).
     */
    public Set<ExecutionStepId> roots() {
        return graphView.roots();
    }

    /**
     * Returns sink steps (no outgoing edges).
     */
    public Set<ExecutionStepId> sinks() {
        return graphView.sinks();
    }

    /**
     * Computes a deterministic graph digest from the plan and graph view.
     */
    private static String computeGraphDigest(MediaExecutionPlan plan, DirectedGraphView<ExecutionStepId> view) {
        StringBuilder sb = new StringBuilder("graph{");
        sb.append("plan=").append(plan.digest().value());
        sb.append(",adj=[");
        for (ExecutionStepId stepId : view.nodes()) {
            sb.append(stepId.value()).append("->[");
            view.successors(stepId).stream()
                    .map(ExecutionStepId::value)
                    .sorted()
                    .forEach(s -> sb.append(s).append(','));
            sb.append("];");
        }
        sb.append("]}");
        return ExecutionPlanCanonicalSerializer.sha256Hex(sb.toString());
    }
}