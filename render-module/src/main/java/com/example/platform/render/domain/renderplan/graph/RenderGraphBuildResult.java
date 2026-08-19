package com.example.platform.render.domain.renderplan.graph;

import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnostic;
import com.example.platform.graph.api.DirectedGraphView;

import java.util.List;
import java.util.Objects;

/**
 * Output of graph construction (C30): the graph, the kernel topology view, the
 * topological order, and any diagnostics (e.g. GRAPH_CYCLE).
 */
public record RenderGraphBuildResult(
        RenderGraph graph,
        DirectedGraphView<RenderNodeId> topology,
        List<RenderNodeId> topologicalOrder,
        List<RenderPlanningDiagnostic> diagnostics) {

    public RenderGraphBuildResult {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(topologicalOrder, "topologicalOrder");
        Objects.requireNonNull(diagnostics, "diagnostics");
        topologicalOrder = List.copyOf(topologicalOrder);
        diagnostics = List.copyOf(diagnostics);
    }
}
