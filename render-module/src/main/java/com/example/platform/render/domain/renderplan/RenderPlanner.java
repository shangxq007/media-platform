package com.example.platform.render.domain.renderplan;

/**
 * Planning phase (C16): one deterministic planning pass that materializes
 * requirements, builds + validates the graph, computes fingerprints, and derives
 * the plan status. Pure function of the planning input.
 */
public interface RenderPlanner {

    /** Run the full planning pass. */
    RenderPlanningResult plan(RenderPlanningInput input);
}
