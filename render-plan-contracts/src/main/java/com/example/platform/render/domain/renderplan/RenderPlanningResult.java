package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * Output of the planning pass (C16): plan + graph + status + diagnostics.
 * Diagnostics are deterministically ordered (by code, then node id, then message).
 */
public record RenderPlanningResult(
        RenderPlan plan,
        RenderGraph graph,
        RenderPlanStatus status,
        List<RenderPlanningDiagnostic> diagnostics) {

    public RenderPlanningResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }
}
