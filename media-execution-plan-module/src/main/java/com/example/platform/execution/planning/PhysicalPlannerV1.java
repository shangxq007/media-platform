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
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.ArrayList;
import java.util.Comparator;
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
final class PhysicalPlannerV1 {

    /** Structural edge projection used to unify SourceArtifact + edge inputs. */
    record RenderDependencyEdgeLike(
            String producerLogicalNodeId,
            String consumerLogicalNodeId,
            com.example.platform.render.domain.renderplan.RenderNodeId producerRenderNodeId,
            com.example.platform.render.domain.renderplan.RenderNodeId consumerRenderNodeId,
            com.example.platform.render.domain.renderplan.RenderDependency dependencyVariant) {

        static RenderDependencyEdgeLike of(LogicalExecutionGraph.LogicalDependencyEdge e) {
            return new RenderDependencyEdgeLike(e.producerLogicalNodeId(), e.consumerLogicalNodeId(),
                    e.producerRenderNodeId(), e.consumerRenderNodeId(),
                    e.dependencyVariant());
        }
    }

    /**
     * Deterministic structurally-framed sort key for incoming-edge input
     * records (C8-A): complete independent edge semantics, never traversal
     * position or delimiter-only tuple framing.
     */
    static String edgeCanonical(RenderDependencyEdgeLike e) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("EDGE_INPUT");
        w.field("producerLogicalNodeId", e.producerLogicalNodeId());
        w.field("producerRenderNodeId",
                e.producerRenderNodeId() != null ? e.producerRenderNodeId().value() : null);
        w.field("consumerLogicalNodeId", e.consumerLogicalNodeId());
        w.field("consumerRenderNodeId",
                e.consumerRenderNodeId() != null ? e.consumerRenderNodeId().value() : null);
        w.field("dependency", Canonical.dependency(e.dependencyVariant()));
        return w.build();
    }

    private record PendingInput(
            String consumerLogicalNodeId,
            ExecutionStepId consumerStepId,
            RenderNodeId consumerRenderNodeId,
            String producerLogicalNodeId,
            ExecutionStepId producerStepId,
            RenderNodeId producerRenderNodeId,
            RenderDependency dependencyVariant,
            RenderArtifactReference.SourceArtifact sourceArtifact,
            RenderSampleWindow requiredSampleWindow) {

        static PendingInput source(LogicalExecutionNode node, RenderArtifactReference.SourceArtifact sourceArtifact) {
            return new PendingInput(
                    node.logicalNodeId(),
                    new ExecutionStepId(node.logicalNodeId()),
                    node.sourceRenderNodeId(),
                    null, null, null, null,
                    sourceArtifact,
                    node.requiredSampleWindow());
        }

        static PendingInput edge(LogicalExecutionNode node, RenderDependencyEdgeLike edge) {
            return new PendingInput(
                    node.logicalNodeId(),
                    new ExecutionStepId(node.logicalNodeId()),
                    node.sourceRenderNodeId(),
                    edge.producerLogicalNodeId(),
                    new ExecutionStepId(edge.producerLogicalNodeId()),
                    edge.producerRenderNodeId(),
                    edge.dependencyVariant(),
                    null,
                    node.requiredSampleWindow());
        }

        String canonicalKey() {
            return PlanningCanonicalOrder.inputBindingSemanticKey(
                    consumerLogicalNodeId,
                    consumerStepId != null ? consumerStepId.value() : null,
                    consumerRenderNodeId,
                    producerLogicalNodeId,
                    producerStepId != null ? producerStepId.value() : null,
                    producerRenderNodeId,
                    dependencyVariant,
                    sourceArtifact,
                    requiredSampleWindow);
        }

        InputBinding toInputBinding(ExecutionInputId inputId) {
            return new InputBinding(
                    inputId,
                    consumerLogicalNodeId,
                    consumerStepId,
                    consumerRenderNodeId,
                    producerLogicalNodeId,
                    producerStepId,
                    producerRenderNodeId,
                    dependencyVariant,
                    sourceArtifact,
                    requiredSampleWindow);
        }
    }

    private PhysicalPlannerV1() {
    }

    static PhysicalExecutionPlan plan(LogicalExecutionGraph logical,
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
            // the node bind the exact dependency variant.
            //
            // C7-A (final determinism closure): NO positional SourceArtifact ↔
            // edge pairing. #20 does NOT establish sourceArtifacts[i] belongs
            // to incomingEdges[i] merely because both are Lists, so each
            // SourceArtifact becomes its own independent InputBinding
            // (producer/dependency null — root/pinned) and each incoming edge
            // its own independent InputBinding (sourceArtifact null).
            // ExecutionInputId ordinals are assigned AFTER canonical sorting
            // of the unified independent input model — never from
            // pre-normalization traversal position (law:planning-deterministic).
            var pendingInputs = new ArrayList<PendingInput>();
            var srcArtifacts = node.artifactReferences().stream()
                    .filter(a -> a instanceof RenderArtifactReference.SourceArtifact)
                    .map(a -> (RenderArtifactReference.SourceArtifact) a)
                    .toList();
            var incomingEdges = logical.edges().stream()
                    .filter(e -> e.consumerLogicalNodeId().equals(node.logicalNodeId()))
                    .map(RenderDependencyEdgeLike::of)
                    .toList();
            for (var srcArtifact : srcArtifacts) {
                pendingInputs.add(PendingInput.source(node, srcArtifact));
            }
            for (var edge : incomingEdges) {
                pendingInputs.add(PendingInput.edge(node, edge));
            }
            pendingInputs.sort(Comparator.comparing(PendingInput::canonicalKey));
            var inputs = new ArrayList<InputBinding>();
            for (int i = 0; i < pendingInputs.size(); i++) {
                inputs.add(pendingInputs.get(i).toInputBinding(
                        new ExecutionInputId(node.sourceRenderNodeId().value() + "#in" + i)));
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
                    PlanningCanonicalOrder.outputRequirements(node.outputRequirements()),
                    PlanningCanonicalOrder.materializations(node.materializationRequirements()),
                    PlanningCanonicalOrder.intermediateArtifacts(intermediate),
                    PlanningCanonicalOrder.finalArtifacts(finals)));

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
                    PlanningCanonicalOrder.logicalEdges(deps),
                    node.requiredSampleWindow(),
                    node.executionCoverage(),
                    PlanningCanonicalOrder.capabilities(node.capabilityRequirements()).stream()
                            .map(ExecutionIoProjection.CapabilityRequirementRef::new)
                            .toList(),
                    PlanningCanonicalOrder.executionRequirements(node.executionRequirements()).stream()
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
        ExecutionPlanSchemaVersion schema = ExecutionPlanSchemaVersion.V1;
        var normalizedUnits = PlanningCanonicalOrder.physicalUnits(units);
        return new PhysicalExecutionPlan(
                "physical-execution-plan-v1",
                planId,
                schema,
                logical.planFingerprint(),
                normalizedUnits,
                requestedExtent,
                PhysicalExecutionPlanDigest.compute(
                        "physical-execution-plan-v1", schema, normalizedUnits,
                        logical.planFingerprint(), requestedExtent));
    }
}
