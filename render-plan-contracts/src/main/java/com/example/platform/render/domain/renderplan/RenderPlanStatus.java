package com.example.platform.render.domain.renderplan;

/**
 * Render-plan lifecycle status (C4/C24). Describes whether the logical plan is
 * executable-ready given the current source-resolution state.
 */
public enum RenderPlanStatus {
    /** Logical plan + graph constructible and executable-ready given current resolution state. */
    PLANNABLE,
    /** Hard failure: typed diagnostics explain why rendering is impossible. */
    UNRENDERABLE,
    /** Executable only after bounded preparation completes (sources pending/blocked/unavailable). */
    PREPARATION_REQUIRED
}
