package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Target execution environment for a render execution plan.
 *
 * <p>v0 only supports LOCAL for FFmpeg baseline.
 * OPENCUE and FUTURE_EXTERNAL are reserved.</p>
 */
public enum ExecutionEnvironmentTarget {

    /** Local execution (FFmpeg baseline). */
    LOCAL,

    /** OpenCue cluster submission (reserved, not implemented). */
    OPENCUE,

    /** Future external execution environment (reserved). */
    FUTURE_EXTERNAL
}
