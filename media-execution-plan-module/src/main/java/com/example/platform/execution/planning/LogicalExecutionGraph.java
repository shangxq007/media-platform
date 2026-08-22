package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 LogicalExecutionGraph (C6-C11).
 *
 * <p>Deterministic typed DAG projection of a validated RenderGraph.
 * RENDER_NODE_TO_LOGICAL_NODE=1_TO_1 — each logical node retains its exact
 * source RenderNode identity/reference (RenderNodeId, RenderNodeKind,
 * operationKey). Logical dependencies preserve the exact RenderDependencyEdge /
 * RenderDependency semantic variants — NO generic DATA/CONTROL/VALIDATION
 * authority, NO invented barrier, NO float-time authority.
 */
public record LogicalExecutionGraph(
        String formatVersion,
        RenderPlanFingerprint planFingerprint,
        List<LogicalExecutionNode> nodes,
        List<LogicalDependencyEdge> edges,
        LogicalExecutionGraphDigest digest) {

    public LogicalExecutionGraph {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(digest, "digest");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** 1:1 logical node — exact source RenderNode reference + typed declared refs. */
    public record LogicalExecutionNode(
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            String sourceRenderNodeKind,
            String operationKey,
            List<ExecutionRequirement.CapabilityRequirementRef> capabilityRequirementRefs,
            List<ExecutionRequirement.ExecutionIntentRef> executionIntentRefs,
            List<String> outputRequirementSourceNodeIds,
            List<String> materializationRequirementSourceNodeIds) {

        public LogicalExecutionNode {
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            Objects.requireNonNull(sourceRenderNodeKind, "sourceRenderNodeKind");
            capabilityRequirementRefs = capabilityRequirementRefs == null
                    ? List.of() : List.copyOf(capabilityRequirementRefs);
            executionIntentRefs = executionIntentRefs == null
                    ? List.of() : List.copyOf(executionIntentRefs);
            outputRequirementSourceNodeIds = outputRequirementSourceNodeIds == null
                    ? List.of() : List.copyOf(outputRequirementSourceNodeIds);
            materializationRequirementSourceNodeIds = materializationRequirementSourceNodeIds == null
                    ? List.of() : List.copyOf(materializationRequirementSourceNodeIds);
        }
    }

    /**
     * Logical dependency edge — preserves the EXACT RenderDependencyEdge /
     * RenderDependency semantic variant. The edge is a typed projection; the
     * variant object is the authoritative semantic carrier.
     */
    public record LogicalDependencyEdge(
            String edgeId,
            String producerLogicalNodeId,
            String consumerLogicalNodeId,
            RenderNodeId producerRenderNodeId,
            RenderNodeId consumerRenderNodeId,
            RenderDependency dependencyVariant) {

        public LogicalDependencyEdge {
            Objects.requireNonNull(edgeId, "edgeId");
            Objects.requireNonNull(producerLogicalNodeId, "producerLogicalNodeId");
            Objects.requireNonNull(consumerLogicalNodeId, "consumerLogicalNodeId");
            Objects.requireNonNull(dependencyVariant, "dependencyVariant — exact RenderDependency variant required");
        }
    }

    /** Canonical projection from a validated RenderGraph (1:1). */
    public static LogicalExecutionGraph fromRenderGraph(RenderGraph graph) {
        Objects.requireNonNull(graph, "graph");
        // deterministic node order: preserve graph's validated topological order
        var logicalNodes = new java.util.ArrayList<LogicalExecutionNode>();
        for (RenderNode node : graph.nodes()) {
            var capRefs = new java.util.ArrayList<ExecutionRequirement.CapabilityRequirementRef>();
            if (node.capabilityRequirements() != null) {
                for (int i = 0; i < node.capabilityRequirements().size(); i++) {
                    var cr = node.capabilityRequirements().get(i);
                    capRefs.add(new ExecutionRequirement.CapabilityRequirementRef(
                            node.id().value(), i, cr.capabilityId(), cr.contractRange(),
                            cr.required(), cr.alternatives()));
                }
            }
            var intentRefs = new java.util.ArrayList<ExecutionRequirement.ExecutionIntentRef>();
            if (node.executionRequirements() != null) {
                for (int i = 0; i < node.executionRequirements().size(); i++) {
                    var er = node.executionRequirements().get(i);
                    intentRefs.add(new ExecutionRequirement.ExecutionIntentRef(
                            node.id().value(), i, er.determinism(), er.sandboxedIntent()));
                }
            }
            logicalNodes.add(new LogicalExecutionNode(
                    "ln-" + node.id().value(),
                    node.id(),
                    node.kind() != null ? node.kind().toString() : "",
                    node.operationKey(),
                    capRefs,
                    intentRefs,
                    node.outputRequirements() != null
                            ? node.outputRequirements().stream().map(o -> node.id().value()).toList()
                            : List.of(),
                    node.materializationRequirements() != null
                            ? node.materializationRequirements().stream().map(m -> node.id().value()).toList()
                            : List.of()));
        }
        var edges = new java.util.ArrayList<LogicalDependencyEdge>();
        if (graph.edges() != null) {
            for (RenderDependencyEdge edge : graph.edges()) {
                edges.add(new LogicalDependencyEdge(
                        "le-" + edge.producerId().value() + "-" + edge.consumerId().value(),
                        "ln-" + edge.producerId().value(),
                        "ln-" + edge.consumerId().value(),
                        edge.producerId(),
                        edge.consumerId(),
                        edge.dependency()));
            }
        }
        return new LogicalExecutionGraph(
                graph.formatVersion(),
                graph.planFingerprint(),
                logicalNodes,
                edges,
                LogicalExecutionGraphDigest.compute(logicalNodes, edges, graph.planFingerprint()));
    }
}
