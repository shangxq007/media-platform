package com.example.platform.render.app.timeline.compile;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for TimelineRevision render execution mode.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>Usage in application.yml/application.properties:
 * <pre>
 * media.render.timeline.execution-mode: PLAN_BASED
 * </pre>
 *
 * <p>Default is LEGACY for safe backward compatibility.</p>
 */
@ConfigurationProperties(prefix = "media.render.timeline")
public record TimelineRenderExecutionProperties(
        /**
         * Execution mode for TimelineRevision rendering.
         * Default: LEGACY (safe, backward-compatible).
         * Set to PLAN_BASED to use the compile pipeline + LocalExecutionPlanRunner.
         */
        TimelineRenderExecutionMode executionMode) {

    /**
     * Default properties with LEGACY mode.
     */
    public static TimelineRenderExecutionProperties defaults() {
        return new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.LEGACY);
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
