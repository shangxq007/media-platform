package com.example.platform.render.domain.compile.executionplan;

/**
 * Target execution environment for a render execution plan.
 *
 * <p>v0 only supports LOCAL for Provider baseline.
 * OPENCUE and FUTURE_EXTERNAL are reserved.</p>
 */
public enum ExecutionEnvironmentTarget {

    /** Local execution (Provider baseline). */
    LOCAL,

    /** OpenCue cluster submission (reserved, not implemented). */
    OPENCUE,

    /** Future external execution environment (reserved). */
    FUTURE_EXTERNAL
}
