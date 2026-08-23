package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Internal typed ordering helpers for Roadmap #21 non-semantic model lists.
 */
final class PlanningCanonicalOrder {

    private PlanningCanonicalOrder() {
    }

    static List<RenderArtifactReference> artifacts(List<RenderArtifactReference> values) {
        return sorted(values, Comparator.comparing(Canonical::artifact));
    }

    static List<CapabilityRequirement> capabilities(List<CapabilityRequirement> values) {
        return sorted(values, Comparator.comparing(Canonical::capability));
    }

    static List<RenderExecutionRequirement> executionRequirements(List<RenderExecutionRequirement> values) {
        return sorted(values, Comparator.comparing(Canonical::executionIntent));
    }

    static List<RenderOutputRequirement> outputRequirements(List<RenderOutputRequirement> values) {
        return sorted(values, Comparator.comparing(Canonical::outputRequirement));
    }

    static List<RenderMaterializationRequirement> materializations(List<RenderMaterializationRequirement> values) {
        return sorted(values, Comparator.comparing(Canonical::materialization));
    }

    static List<RenderArtifactReference.IntermediateArtifactExpectation> intermediateArtifacts(
            List<RenderArtifactReference.IntermediateArtifactExpectation> values) {
        return sorted(values, Comparator.comparing(Canonical::intermediateArtifact));
    }

    static List<RenderArtifactReference.FinalArtifactExpectation> finalArtifacts(
            List<RenderArtifactReference.FinalArtifactExpectation> values) {
        return sorted(values, Comparator.comparing(Canonical::finalArtifact));
    }

    static List<LogicalExecutionGraph.LogicalExecutionNode> logicalNodes(
            List<LogicalExecutionGraph.LogicalExecutionNode> values) {
        return sorted(values, Comparator.comparing(LogicalExecutionGraph.LogicalExecutionNode::logicalNodeId));
    }

    static List<LogicalExecutionGraph.LogicalDependencyEdge> logicalEdges(
            List<LogicalExecutionGraph.LogicalDependencyEdge> values) {
        return sorted(values, Comparator.comparing(PlanningCanonicalOrder::logicalEdge));
    }

    static List<LogicalExecutionGraph.PruningEvidence.EliminatedNode> eliminatedNodes(
            List<LogicalExecutionGraph.PruningEvidence.EliminatedNode> values) {
        return sorted(values, Comparator.comparing(PlanningCanonicalOrder::eliminatedNode));
    }

    static List<PhysicalExecutionPlan.PhysicalPlanUnit> physicalUnits(
            List<PhysicalExecutionPlan.PhysicalPlanUnit> values) {
        return sorted(values, Comparator.comparing(PlanningCanonicalOrder::physicalUnit));
    }

    static List<ExecutionIoProjection.InputBinding> inputBindings(
            List<ExecutionIoProjection.InputBinding> values) {
        return sorted(values, Comparator.comparing(PlanningCanonicalOrder::inputBinding));
    }

    static List<ExecutionIoProjection.OutputDeclaration> outputDeclarations(
            List<ExecutionIoProjection.OutputDeclaration> values) {
        return sorted(values, Comparator.comparing(PlanningCanonicalOrder::outputDeclaration));
    }

    static List<ExecutionIoProjection.CapabilityRequirementRef> capabilityRequirementRefs(
            List<ExecutionIoProjection.CapabilityRequirementRef> values) {
        return sorted(values, Comparator.comparing(ref -> Canonical.capability(ref.declaration())));
    }

    static List<ExecutionIoProjection.ExecutionIntentRef> executionIntentRefs(
            List<ExecutionIoProjection.ExecutionIntentRef> values) {
        return sorted(values, Comparator.comparing(ref -> Canonical.executionIntent(ref.declaration())));
    }

    static String logicalEdge(LogicalExecutionGraph.LogicalDependencyEdge e) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("LOGICAL_EDGE");
        w.field("edgeId", e.edgeId().value());
        w.field("producerLogicalNodeId", e.producerLogicalNodeId());
        w.field("consumerLogicalNodeId", e.consumerLogicalNodeId());
        w.field("producerRenderNodeId",
                e.producerRenderNodeId() != null ? e.producerRenderNodeId().value() : null);
        w.field("consumerRenderNodeId",
                e.consumerRenderNodeId() != null ? e.consumerRenderNodeId().value() : null);
        w.field("dependency", Canonical.dependency(e.dependencyVariant()));
        return w.build();
    }

    static String inputBindingSemanticKey(
            String consumerLogicalNodeId,
            String consumerStepId,
            RenderNodeId consumerRenderNodeId,
            String producerLogicalNodeId,
            String producerStepId,
            RenderNodeId producerRenderNodeId,
            RenderDependency dependencyVariant,
            RenderArtifactReference.SourceArtifact sourceArtifact,
            RenderSampleWindow requiredSampleWindow) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("INPUT_BINDING_SEMANTICS");
        w.field("consumerLogicalNodeId", consumerLogicalNodeId);
        w.field("consumerStepId", consumerStepId);
        w.field("consumerRenderNodeId", consumerRenderNodeId != null ? consumerRenderNodeId.value() : null);
        w.field("producerLogicalNodeId", producerLogicalNodeId);
        w.field("producerStepId", producerStepId);
        w.field("producerRenderNodeId", producerRenderNodeId != null ? producerRenderNodeId.value() : null);
        w.field("dependency", Canonical.dependency(dependencyVariant));
        w.optional(sourceArtifact != null, sourceArtifact != null ? Canonical.sourceArtifact(sourceArtifact) : null);
        w.optional(requiredSampleWindow != null,
                requiredSampleWindow != null
                        ? LogicalExecutionGraphBuilder.canonicalWindow(requiredSampleWindow) : null);
        return w.build();
    }

    private static String inputBinding(ExecutionIoProjection.InputBinding i) {
        return inputBindingSemanticKey(
                i.consumerLogicalNodeId(),
                i.consumerStepId() != null ? i.consumerStepId().value() : null,
                i.consumerRenderNodeId(),
                i.producerLogicalNodeId(),
                i.producerStepId() != null ? i.producerStepId().value() : null,
                i.producerRenderNodeId(),
                i.dependencyVariant(),
                i.sourceArtifact(),
                i.requiredSampleWindow());
    }

    private static String outputDeclaration(ExecutionIoProjection.OutputDeclaration o) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("OUTPUT_DECLARATION");
        w.field("outputId", o.outputId().value());
        w.field("logicalNodeId", o.logicalNodeId());
        w.field("sourceRenderNodeId", o.sourceRenderNodeId().value());
        w.list(CanonicalWriter.sorted(o.outputRequirements().stream()
                .map(Canonical::outputRequirement).toList()));
        w.list(CanonicalWriter.sorted(o.materializationRequirements().stream()
                .map(Canonical::materialization).toList()));
        w.list(CanonicalWriter.sorted(o.intermediateArtifactExpectations().stream()
                .map(Canonical::intermediateArtifact).toList()));
        w.list(CanonicalWriter.sorted(o.finalArtifactExpectations().stream()
                .map(Canonical::finalArtifact).toList()));
        return w.build();
    }

    private static String physicalUnit(PhysicalExecutionPlan.PhysicalPlanUnit u) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("PHYSICAL_UNIT_ORDER");
        w.field("stepId", u.stepId().value());
        w.field("logicalNodeId", u.logicalNodeId());
        return w.build();
    }

    private static String eliminatedNode(LogicalExecutionGraph.PruningEvidence.EliminatedNode n) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("ELIMINATED_NODE");
        w.field("logicalNodeId", n.logicalNodeId());
        w.field("sourceRenderNodeId", n.sourceRenderNodeId() != null ? n.sourceRenderNodeId().value() : null);
        w.field("requiredWindow", n.requiredWindow());
        w.field("extentWindow", n.extentWindow());
        w.field("reason", n.reason());
        return w.build();
    }

    private static <T> List<T> sorted(List<T> values, Comparator<? super T> comparator) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var copy = new ArrayList<>(values);
        copy.sort(comparator);
        return List.copyOf(copy);
    }
}
