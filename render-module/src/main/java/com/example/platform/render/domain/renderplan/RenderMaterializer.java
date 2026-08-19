package com.example.platform.render.domain.renderplan;

/**
 * Materialization phase (C16): transforms canonical authored semantics + resolved
 * immutable source semantics into provider-neutral render requirements
 * (nodes + typed requirements). Pure function; never executes the render.
 */
public interface RenderMaterializer {

    /** Materialize nodes + edges + diagnostics from the planning input. */
    RenderMaterializationResult materialize(RenderPlanningInput input);
}
