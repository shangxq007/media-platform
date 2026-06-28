package com.example.platform.render.app.timeline.compile;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for TimelineRevision render execution mode.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>Usage in application.yml/application.properties:
 * <pre>
 * media.render.timeline.execution-mode: PLAN_BASED  # default
 * media.render.timeline.execution-mode: LEGACY       # rollback
 * </pre>
 *
 * <p>Default is PLAN_BASED. Set to LEGACY for rollback.</p>
 */
@ConfigurationProperties(prefix = "media.render.timeline")
public record TimelineRenderExecutionProperties(
        /**
         * Execution mode for TimelineRevision rendering.
         * Default: PLAN_BASED (compile pipeline + LocalExecutionPlanRunner).
         * Set to LEGACY for rollback to direct FFmpeg path.
         */
        TimelineRenderExecutionMode executionMode) {

    /**
     * Default properties with PLAN_BASED mode.
     */
    public static TimelineRenderExecutionProperties defaults() {
        return new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED);
    }

    /**
     * Returns true if plan-based execution is enabled.
     */
    public boolean isPlanBasedEnabled() {
        return executionMode == TimelineRenderExecutionMode.PLAN_BASED;
    }

    /**
     * Returns true if legacy execution is enabled.
     */
    public boolean isLegacyEnabled() {
        return executionMode == TimelineRenderExecutionMode.LEGACY;
    }
}
