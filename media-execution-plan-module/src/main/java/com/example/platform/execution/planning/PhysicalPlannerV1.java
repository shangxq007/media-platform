package com.example.platform.execution.planning;

import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalExecutionNode;
import com.example.platform.execution.planning.PhysicalExecutionPlan.ExecutionPlanId;
import com.example.platform.execution.planning.PhysicalExecutionPlan.ExecutionPlanSchemaVersion;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Roadmap #21 PhysicalPlanner V1 (C14/C15) — provider-neutral structural
 * planning. ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT.
 *
 * <p>FUSION / TEMPORAL_CHUNKING / N_TO_M / SEMANTIC_REWRITE /
 * GENERAL_COST_OPTIMIZATION are DEFERRED. This planner performs structural
 * partition only: each logical node becomes exactly one physical plan unit
 * with typed inputs/outputs/dependencies/temporal window/extent/requirement
 * references. NO provider/worker/device/queue/availability binding.
 *
 * <p>FAOF-1 laws: law:partition-1-to-1, law:partition-preserves-logical.
 */
public final class PhysicalPlannerV1 {

    private PhysicalPlannerV1() {
    }

    public static PhysicalExecutionPlan plan(LogicalExecutionGraph logical,
                                             com.example.platform.render.domain.renderplan.RenderExtent requestedExtent) {
        Objects.requireNonNull(logical, "logical");
        var units = new ArrayList<PhysicalPlanUnit>();
        Set<String> logicalIds = new HashSet<>();
        for (var node : logical.nodes()) {
            if (!logicalIds.add(node.logicalNodeId())) {
                throw new ExecutionPlanningException(
                        ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                        new ExecutionPlanningException.DuplicateIdentityContext(
                                "logicalNodeId", node.logicalNodeId(),
                                "duplicate logical node identity in physical partition"));
            }
            var inputs = new ArrayList<InputBinding>();
            for (var edge : logical.edges()) {
                if (edge.consumerLogicalNodeId().equals(node.logicalNodeId())) {
                    inputs.add(new InputBinding(
                            node.logicalNodeId(),
                            node.sourceRenderNodeId(),
                            edge.producerLogicalNodeId(),
                            edge.producerRenderNodeId(),
                            edge.dependencyVariant(),
                            null,
                            node.requiredSampleWindow()));
                }
            }
            var outputs = new ArrayList<OutputDeclaration>();
            outputs.add(new OutputDeclaration(
                    node.logicalNodeId(),
                    node.sourceRenderNodeId(),
                    node.outputRequirements(),
                    node.materializationRequirements(),
                    node.artifactReferences()));
            var deps = new ArrayList<LogicalDependencyEdge>();
            for (var edge : logical.edges()) {
                if (edge.producerLogicalNodeId().equals(node.logicalNodeId())
                        || edge.consumerLogicalNodeId().equals(node.logicalNodeId())) {
                    deps.add(edge);
                }
            }
            units.add(new PhysicalPlanUnit(
                    "pu-" + node.logicalNodeId(),
                    node.logicalNodeId(),
                    node.sourceRenderNodeId(),
                    node.sourceRenderNodeKind(),
                    node.operationKey(),
                    inputs,
                    outputs,
                    deps,
                    node.requiredSampleWindow(),
                    node.capabilityRequirements().stream()
                            .map(ExecutionIoProjection.CapabilityRequirementRef::new)
                            .toList(),
                    node.executionRequirements().stream()
                            .map(ExecutionIoProjection.ExecutionIntentRef::new)
                            .toList(),
                    requestedExtent,
                    node.executionRequirements().isEmpty()
                            || node.executionRequirements().stream()
                                    .allMatch(er -> er.determinism()
                                            == com.example.platform.render.domain.renderplan
                                                    .RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC)));
        }
        return new PhysicalExecutionPlan(
                "physical-execution-plan-v1",
                new ExecutionPlanId("pep-" + LogicalExecutionGraphDigest.sha256(
                        logical.planFingerprint().sha256Hex()).substring(0, 12)),
                new ExecutionPlanSchemaVersion(1, 0),
                logical.planFingerprint(),
                units,
                requestedExtent,
                PhysicalExecutionPlanDigest.compute(units, logical.planFingerprint(), requestedExtent));
    }
}
