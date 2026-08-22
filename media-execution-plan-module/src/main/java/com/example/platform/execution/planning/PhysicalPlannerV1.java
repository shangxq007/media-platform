package com.example.platform.execution.planning;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalPlanner V1 (C14/C15) — provider-neutral structural
 * planning. ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT.
 *
 * <p>FUSION / TEMPORAL_CHUNKING / N_TO_M / SEMANTIC_REWRITE /
 * GENERAL_COST_OPTIMIZATION are DEFERRED. This planner performs structural
 * partition only: each logical node becomes exactly one physical plan unit.
 * NO provider/worker/device/queue/availability binding.
 */
public final class PhysicalPlannerV1 {

    private PhysicalPlannerV1() {
    }

    public static PhysicalExecutionPlan plan(LogicalExecutionGraph logical) {
        Objects.requireNonNull(logical, "logical");
        var units = new ArrayList<PhysicalExecutionPlan.PhysicalPlanUnit>();
        for (var node : logical.nodes()) {
            var inputs = new ArrayList<String>();
            for (var edge : logical.edges()) {
                if (edge.consumerLogicalNodeId().equals(node.logicalNodeId())) {
                    inputs.add(edge.producerLogicalNodeId());
                }
            }
            units.add(new PhysicalExecutionPlan.PhysicalPlanUnit(
                    "pu-" + node.logicalNodeId(),
                    node.logicalNodeId(),
                    node.sourceRenderNodeId(),
                    node.sourceRenderNodeKind(),
                    node.operationKey(),
                    node.capabilityRequirementRefs().stream()
                            .map(c -> node.sourceRenderNodeId().value() + "#cap" + c.declarationIndex())
                            .toList(),
                    node.executionIntentRefs().stream()
                            .map(e -> node.sourceRenderNodeId().value() + "#intent" + e.declarationIndex())
                            .toList(),
                    inputs,
                    node.outputRequirementSourceNodeIds()));
        }
        return new PhysicalExecutionPlan(
                "physical-execution-plan-v1",
                logical.planFingerprint(),
                units,
                PhysicalExecutionPlanDigest.compute(units, logical.planFingerprint()));
    }
}
