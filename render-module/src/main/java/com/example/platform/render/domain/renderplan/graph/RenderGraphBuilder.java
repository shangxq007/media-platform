package com.example.platform.render.domain.renderplan.graph;

import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import com.example.platform.graph.api.GraphViews;
import com.example.platform.graph.result.CycleDetectionResult;
import com.example.platform.graph.result.TopologicalOrderResult;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanCanonicalCodec;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnostic;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnosticCode;
import com.example.platform.render.domain.renderplan.RenderDiagnosticSeverity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a RenderGraph from a RenderPlan by delegating to the graph kernel
 * (GraphViews + GraphAlgorithms) for topology, cycle detection, and topological
 * ordering (C30). The graph's planFingerprint is taken from the plan; the graph
 * fingerprint is computed over the canonicalized topology.
 */
public final class RenderGraphBuilder {

    private static final RenderPlanCanonicalCodec CODEC = RenderPlanFingerprintCalculator.codec();

    public RenderGraphBuilder() {
    }

    /** Build the graph projection of the plan. */
    public RenderGraphBuildResult build(RenderPlan plan) {
        List<RenderNode> nodes = plan.nodes();
        List<RenderDependencyEdge> edges = plan.edges();
        List<RenderPlanningDiagnostic> diagnostics = new ArrayList<>();

        Set<RenderNodeId> nodeIds = nodes.stream().map(RenderNode::id).collect(Collectors.toSet());

        // edge list as kernel entries: producer -> consumer (data flow direction)
        List<Map.Entry<RenderNodeId, RenderNodeId>> kernelEdges = edges.stream()
                .filter(e -> nodeIds.contains(e.producerId()) && nodeIds.contains(e.consumerId()))
                .map(e -> Map.entry(e.producerId(), e.consumerId()))
                .collect(Collectors.toList());

        // delegate topology construction to the kernel
        DirectedGraphView<RenderNodeId> topology =
                GraphViews.directedFromEdges(nodeIds, kernelEdges);

        // delegate cycle detection to the kernel (fail closed)
        CycleDetectionResult<RenderNodeId> cycleResult = GraphAlgorithms.detectCycles(topology);
        List<RenderNodeId> topologicalOrder;

        if (cycleResult.hasCycle()) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.GRAPH_CYCLE,
                    RenderDiagnosticSeverity.ERROR,
                    "RenderGraph contains a directed cycle"));
            // delegate topological order computation to the kernel anyway (reports cycle)
            TopologicalOrderResult<RenderNodeId> topoResult = GraphAlgorithms.topologicalOrder(
                    topology, Comparator.comparing(RenderNodeId::value));
            topologicalOrder = extractOrder(topoResult);
        } else {
            TopologicalOrderResult<RenderNodeId> topoResult = GraphAlgorithms.topologicalOrder(
                    topology, Comparator.comparing(RenderNodeId::value));
            topologicalOrder = extractOrder(topoResult);
        }

        // canonicalized node/edge ordering for deterministic graph identity
        List<RenderNode> sortedNodes = nodes.stream()
                .sorted(Comparator.comparing(n -> n.id().value()))
                .collect(Collectors.toList());
        List<RenderDependencyEdge> sortedEdges = edges.stream()
                .sorted(Comparator
                        .comparing((RenderDependencyEdge e) -> e.producerId().value())
                        .thenComparing(e -> e.consumerId().value())
                        .thenComparing(e -> e.dependency().variantKey()))
                .collect(Collectors.toList());

        // graph fingerprint over canonical topology
        RenderGraphFingerprint graphFingerprint = computeGraphFingerprint(
                plan.fingerprint(), sortedNodes, sortedEdges);

        RenderGraph graph = new RenderGraph(
                RenderPlanCanonicalCodec.GRAPH_FORMAT_VERSION,
                plan.fingerprint(),
                sortedNodes,
                sortedEdges,
                graphFingerprint);

        return new RenderGraphBuildResult(graph, topology, topologicalOrder, diagnostics);
    }

    private List<RenderNodeId> extractOrder(TopologicalOrderResult<RenderNodeId> topoResult) {
        if (topoResult instanceof TopologicalOrderResult.Ordered<RenderNodeId> ordered) {
            return ordered.order();
        }
        // cycle detected: kernel reports CycleDetected (order() throws); return empty
        return List.of();
    }

    private RenderGraphFingerprint computeGraphFingerprint(
            RenderPlanFingerprint planFingerprint,
            List<RenderNode> sortedNodes,
            List<RenderDependencyEdge> sortedEdges) {
        String canonical = CODEC.graphFingerprintCanonical(
                new RenderGraph(RenderPlanCanonicalCodec.GRAPH_FORMAT_VERSION, planFingerprint,
                        sortedNodes, sortedEdges, new RenderGraphFingerprint("provisional")));
        return new RenderGraphFingerprint(CODEC.sha256Hex(canonical));
    }
}
