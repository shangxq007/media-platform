package com.example.platform.render.domain.renderplan.graph;

import com.example.platform.render.domain.renderplan.RenderPlanningDiagnostic;

import java.util.List;
import java.util.Objects;

/**
 * Output of graph validation (C23): valid flag + diagnostics. Fail-closed.
 */
public record RenderGraphValidationResult(
        boolean valid,
        List<RenderPlanningDiagnostic> diagnostics) {

    public RenderGraphValidationResult {
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }
}
