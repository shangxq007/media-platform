package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * Output of the materialization phase (C16): nodes + edges + diagnostics.
 */
public record RenderMaterializationResult(
        List<RenderNode> nodes,
        List<RenderDependencyEdge> edges,
        List<RenderPlanningDiagnostic> diagnostics) {

    public RenderMaterializationResult {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(diagnostics, "diagnostics");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        diagnostics = List.copyOf(diagnostics);
    }
}
