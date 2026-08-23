package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalExecutionNode;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderExtent;
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
 * GENERAL_COST_OPTIMIZATION DEFERRED. Structural partition only, with typed
 * semantic direction: SourceArtifact → InputBinding; Intermediate/Final
 * artifact expectations → OutputDeclaration. NO provider/worker/device/queue/
 * availability binding.
 */
public final class PhysicalPlannerV1 {

    /** Structural edge projection used to unify SourceArtifact + edge inputs. */
    public record RenderDependencyEdgeLike(
            String producerLogicalNodeId,
            com.example.platform.render.domain.renderplan.RenderNodeId producerRenderNodeId,
            com.example.platform.render.domain.renderplan.RenderDependency dependencyVariant) {

        public static RenderDependencyEdgeLike of(LogicalExecutionGraph.LogicalDependencyEdge e) {
            return new RenderDependencyEdgeLike(e.producerLogicalNodeId(), e.producerRenderNodeId(),
                    e.dependencyVariant());
        }
    }

    private PhysicalPlannerV1() {
    }

    public static PhysicalExecutionPlan plan(LogicalExecutionGraph logical,
                                             RenderExtent requestedExtent,
                                             ExecutionPlanId planId) {
        Objects.requireNonNull(logical, "logical");
        Objects.requireNonNull(planId, "planId — explicit non-semantic identity input (frozen: id != digest)");
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

            // typed inputs: (a) every declared SourceArtifact on the node is a
            // pinned consumed input (semantic direction: SourceArtifact ->
            // InputBinding, ALWAYS — even without a graph producer edge, e.g.
            // a DECODE root node consuming source media); (b) graph edges into
            // the node bind the exact dependency variant
            var inputs = new ArrayList<InputBinding>();
            int inputIdx = 0;
            var srcArtifacts = node.artifactReferences().stream()
                    .filter(a -> a instanceof RenderArtifactReference.SourceArtifact)
                    .map(a -> (RenderArtifactReference.SourceArtifact) a)
                    .toList();
            var incomingEdges = logical.edges().stream()
                    .filter(e -> e.consumerLogicalNodeId().equals(node.logicalNodeId()))
                    .map(RenderDependencyEdgeLike::of)
                    .toList();
            for (int i = 0; i < srcArtifacts.size(); i++) {
                RenderDependencyEdgeLike edgeLike = i < incomingEdges.size() ? incomingEdges.get(i) : null;
                inputs.add(new InputBinding(
                        new ExecutionInputId(node.sourceRenderNodeId().value() + "#in" + inputIdx),
                        node.logicalNodeId(),
                        new ExecutionStepId(node.logicalNodeId()),
                        node.sourceRenderNodeId(),
                        edgeLike != null ? edgeLike.producerLogicalNodeId() : null,
                        edgeLike != null ? new ExecutionStepId(edgeLike.producerLogicalNodeId()) : null,
                        edgeLike != null ? edgeLike.producerRenderNodeId() : null,
                        edgeLike != null ? edgeLike.dependencyVariant() : null,
                        srcArtifacts.get(i),
                        node.requiredSampleWindow()));
                inputIdx++;
            }
            for (int i = srcArtifacts.size(); i < incomingEdges.size(); i++) {
                var edge = incomingEdges.get(i);
                inputs.add(new InputBinding(
                        new ExecutionInputId(node.sourceRenderNodeId().value() + "#in" + inputIdx),
                        node.logicalNodeId(),
                        new ExecutionStepId(node.logicalNodeId()),
                        node.sourceRenderNodeId(),
                        edge.producerLogicalNodeId(),
                        new ExecutionStepId(edge.producerLogicalNodeId()),
                        edge.producerRenderNodeId(),
                        edge.dependencyVariant(),
                        null,
                        node.requiredSampleWindow()));
                inputIdx++;
            }

            // typed outputs: exact #20 output/materialization requirements +
            // Intermediate/Final artifact expectations (semantic direction)
            var outputs = new ArrayList<OutputDeclaration>();
            var intermediate = node.artifactReferences().stream()
                    .filter(a -> a instanceof RenderArtifactReference.IntermediateArtifactExpectation)
                    .map(a -> (RenderArtifactReference.IntermediateArtifactExpectation) a)
                    .toList();
            var finals = node.artifactReferences().stream()
                    .filter(a -> a instanceof RenderArtifactReference.FinalArtifactExpectation)
                    .map(a -> (RenderArtifactReference.FinalArtifactExpectation) a)
                    .toList();
            outputs.add(new OutputDeclaration(
                    new ExecutionOutputId(node.sourceRenderNodeId().value() + "#out"),
                    node.logicalNodeId(),
                    node.sourceRenderNodeId(),
                    node.outputRequirements(),
                    node.materializationRequirements(),
                    intermediate,
                    finals));

            var deps = new ArrayList<LogicalDependencyEdge>();
            for (var edge : logical.edges()) {
                if (edge.producerLogicalNodeId().equals(node.logicalNodeId())
                        || edge.consumerLogicalNodeId().equals(node.logicalNodeId())) {
                    deps.add(edge);
                }
            }

            units.add(new PhysicalPlanUnit(
                    new ExecutionStepId(node.logicalNodeId()),
                    node.logicalNodeId(),
                    node.sourceRenderNodeId(),
                    node.sourceRenderNodeKind(),
                    node.operationKey(),
                    inputs,
                    outputs,
                    deps,
                    node.requiredSampleWindow(),
                    node.executionCoverage(),
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
        // ExecutionPlanId: independent identity supplied by the caller
        // (planning context) — NEVER derived from semantic fingerprint
        ExecutionPlanSchemaVersion schema = new ExecutionPlanSchemaVersion(1);
        return new PhysicalExecutionPlan(
                "physical-execution-plan-v1",
                planId,
                schema,
                logical.planFingerprint(),
                units,
                requestedExtent,
                PhysicalExecutionPlanDigest.compute(
                        "physical-execution-plan-v1", schema, units,
                        logical.planFingerprint(), requestedExtent));
    }
}
