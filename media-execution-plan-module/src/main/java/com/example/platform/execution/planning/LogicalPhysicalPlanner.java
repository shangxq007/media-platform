package com.example.platform.execution.planning;

import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalExecutionNode;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Roadmap #21 planner entry point (C14) — provider-neutral structural planning.
 *
 * <p>Chain: RenderPlan + validated RenderGraph → ExecutionRequirement (pure
 * projection) → LogicalExecutionGraph (1:1, extent-pruned, fail-closed
 * validated) → PhysicalExecutionPlan (1:1, typed).
 *
 * <p>Determinism contract (law:planning-deterministic): same frozen semantic
 * input → same normalized ExecutionRequirement → same logical graph content →
 * same logical digest → same physical plan content → same physical digest.
 * NO mutable runtime reads, NO provider/worker/device binding.
 */
public final class LogicalPhysicalPlanner {

    private LogicalPhysicalPlanner() {
    }

    /** Plan a validated RenderPlan (+ its validated RenderGraph) end to end. */
    public static PlanningResult plan(RenderPlan plan, RenderGraph graph,
                                      com.example.platform.execution.domain.ExecutionPlanId planId) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(planId, "planId");

        // CR-02 consistency invariant: graph.planFingerprint == plan.fingerprint
        if (!Objects.equals(graph.planFingerprint(), plan.fingerprint())) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.UNSATISFIED_STRUCTURAL_CONSTRAINT,
                    new ExecutionPlanningException.FingerprintMismatchContext(
                            graph.planFingerprint().sha256Hex(),
                            plan.fingerprint().sha256Hex(),
                            "graph.planFingerprint != plan.fingerprint"));
        }

        ExecutionRequirement requirement = ExecutionRequirement.derive(plan);
        var requestedExtent = requirement.requestedExtent();

        LogicalExecutionGraph logical = LogicalExecutionGraphBuilder.build(graph, requestedExtent);
        validateAcyclic(logical);
        validateRefsResolve(logical);

        PhysicalExecutionPlan physical = PhysicalPlannerV1.plan(logical, requestedExtent, planId);
        return new PlanningResult(requirement, logical, physical);
    }

    /** law:dag-acyclic — deterministic DFS cycle detection. */
    static void validateAcyclic(LogicalExecutionGraph logical) {
        var adjacency = new HashMap<String, List<String>>();
        for (var e : logical.edges()) {
            adjacency.computeIfAbsent(e.producerLogicalNodeId(), k -> new ArrayList<>())
                    .add(e.consumerLogicalNodeId());
        }
        var state = new HashMap<String, Integer>();
        var order = new ArrayList<String>();
        var stack = new ArrayList<String>();
        for (var n : logical.nodes()) {
            dfs(n.logicalNodeId(), adjacency, state, order, stack);
        }
        if (order.size() != logical.nodes().size()) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.CYCLE_DETECTED,
                    new ExecutionPlanningException.CycleContext(
                            List.copyOf(stack),
                            "cycle detected in logical execution graph"));
        }
    }

    private static void dfs(String nodeId, Map<String, List<String>> adjacency,
                            Map<String, Integer> state, List<String> order, List<String> stack) {
        if (state.getOrDefault(nodeId, 0) == 2) {
            return;
        }
        if (state.getOrDefault(nodeId, 0) == 1) {
            int i = stack.indexOf(nodeId);
            List<String> cycle = i >= 0
                    ? stack.subList(i, stack.size()) : List.of(nodeId);
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.CYCLE_DETECTED,
                    new ExecutionPlanningException.CycleContext(cycle,
                            "cycle at node " + nodeId));
        }
        state.put(nodeId, 1);
        stack.add(nodeId);
        for (String next : adjacency.getOrDefault(nodeId, List.of())) {
            dfs(next, adjacency, state, order, stack);
        }
        stack.remove(stack.size() - 1);
        state.put(nodeId, 2);
        order.add(nodeId);
    }

    /** law:inputs-closed — every edge endpoint resolves to a node. */
    static void validateRefsResolve(LogicalExecutionGraph logical) {
        Set<String> ids = new HashSet<>();
        for (var n : logical.nodes()) {
            if (!ids.add(n.logicalNodeId())) {
                throw new ExecutionPlanningException(
                        ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                        new ExecutionPlanningException.DuplicateIdentityContext(
                                "logicalNodeId", n.logicalNodeId(),
                                "duplicate logical node identity"));
            }
        }
        for (var e : logical.edges()) {
            if (!ids.contains(e.producerLogicalNodeId())) {
                throw new ExecutionPlanningException(
                        ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                        new ExecutionPlanningException.MissingReferenceContext(
                                "producerLogicalNode", e.producerLogicalNodeId(),
                                "dangling producer reference"));
            }
            if (!ids.contains(e.consumerLogicalNodeId())) {
                throw new ExecutionPlanningException(
                        ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                        new ExecutionPlanningException.MissingReferenceContext(
                                "consumerLogicalNode", e.consumerLogicalNodeId(),
                                "dangling consumer reference"));
            }
        }
    }

    /** End-to-end planning result (transient derived values + digests). */
    public record PlanningResult(
            ExecutionRequirement executionRequirement,
            LogicalExecutionGraph logicalExecutionGraph,
            PhysicalExecutionPlan physicalExecutionPlan) {
    }
}
