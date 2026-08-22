package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderPlan;
import java.util.Objects;

/**
 * Roadmap #21 planner entry point (C14) — provider-neutral structural planning.
 *
 * <p>Chain: RenderPlan + validated RenderGraph → ExecutionRequirement (pure
 * projection) → LogicalExecutionGraph (1:1) → PhysicalExecutionPlan (1:1).
 *
 * <p>Determinism contract: same frozen semantic input → same normalized
 * ExecutionRequirement → same logical graph content → same logical digest →
 * same physical plan content → same physical digest. NO mutable runtime reads,
 * NO provider/worker/device binding.
 */
public final class LogicalPhysicalPlanner {

    private LogicalPhysicalPlanner() {
    }

    /** Plan a validated RenderPlan (+ its validated RenderGraph) end to end. */
    public static PlanningResult plan(RenderPlan plan, RenderGraph graph) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(graph, "graph");

        // CR-02 consistency invariant: graph.planFingerprint == plan.fingerprint
        if (!Objects.equals(graph.planFingerprint(), plan.fingerprint())) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.UNSATISFIED_STRUCTURAL_CONSTRAINT,
                    "graph.planFingerprint != plan.fingerprint: graph="
                            + graph.planFingerprint().sha256Hex()
                            + " plan=" + plan.fingerprint().sha256Hex());
        }

        ExecutionRequirement requirement = ExecutionRequirement.derive(plan);
        LogicalExecutionGraph logical = LogicalExecutionGraph.fromRenderGraph(graph);
        validateNoCycle(logical);
        PhysicalExecutionPlan physical = PhysicalPlannerV1.plan(logical);
        return new PlanningResult(requirement, logical, physical);
    }

    private static void validateNoCycle(LogicalExecutionGraph logical) {
        // deterministic DFS cycle detection over logical dependency edges
        var adjacency = new java.util.HashMap<String, java.util.List<String>>();
        for (var e : logical.edges()) {
            adjacency.computeIfAbsent(e.producerLogicalNodeId(), k -> new java.util.ArrayList<>())
                    .add(e.consumerLogicalNodeId());
        }
        var state = new java.util.HashMap<String, Integer>();
        var order = new java.util.ArrayList<String>();
        for (var n : logical.nodes()) {
            dfs(n.logicalNodeId(), adjacency, state, order);
        }
        if (order.size() != logical.nodes().size()) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.CYCLE_DETECTED,
                    "logical execution graph contains a cycle");
        }
    }

    private static void dfs(String nodeId,
                            java.util.Map<String, java.util.List<String>> adjacency,
                            java.util.Map<String, Integer> state,
                            java.util.List<String> order) {
        if (state.getOrDefault(nodeId, 0) == 2) {
            return;
        }
        if (state.getOrDefault(nodeId, 0) == 1) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.CYCLE_DETECTED,
                    "cycle at node " + nodeId);
        }
        state.put(nodeId, 1);
        for (String next : adjacency.getOrDefault(nodeId, java.util.List.of())) {
            dfs(next, adjacency, state, order);
        }
        state.put(nodeId, 2);
        order.add(nodeId);
    }

    /** End-to-end planning result (transient derived values + digests). */
    public record PlanningResult(
            ExecutionRequirement executionRequirement,
            LogicalExecutionGraph logicalExecutionGraph,
            PhysicalExecutionPlan physicalExecutionPlan) {
    }
}
