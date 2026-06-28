package com.example.platform.render.app.timeline.compile;

/**
 * Execution mode for TimelineRevision rendering.
 *
 * <p>Internal only — controls which execution path is used
 * for rendering a TimelineRevision.</p>
 */
public enum TimelineRenderExecutionMode {

    /**
     * Legacy direct FFmpeg path (existing TimelineRevisionRenderService).
     * This is the default and safest mode.
     */
    LEGACY,

    /**
     * Plan-based execution path through compile pipeline
     * (NormalizedTimeline → ArtifactDependencyGraph → LogicalCapabilityGraph
     *  → ProviderBindingPlan → RenderExecutionPlan → LocalExecutionPlanRunner).
     */
    PLAN_BASED
}
