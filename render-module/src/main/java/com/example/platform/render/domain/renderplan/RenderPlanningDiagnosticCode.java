package com.example.platform.render.domain.renderplan;

/**
 * Typed planning diagnostic codes (C24). CANONICAL_INVALID (authored state
 * invalid) is intentionally absent — that is authored-layer validation, never a
 * planner diagnostic.
 */
public enum RenderPlanningDiagnosticCode {
    SOURCE_UNRESOLVED,
    SOURCE_UNAVAILABLE,
    SOURCE_DIGEST_MISMATCH,
    SOURCE_RESOLUTION_PENDING,
    DEPENDENCY_MISSING,
    CAPABILITY_UNAVAILABLE,
    GRAPH_CYCLE,
    INVALID_RENDER_EXTENT,
    MATERIALIZATION_FAILED,
    PLANNING_UNSUPPORTED
}
