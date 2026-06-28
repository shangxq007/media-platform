package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Types of policy violations in a render execution plan.
 *
 * <p>Internal only — each violation identifies a specific constraint
 * that was not met.</p>
 */
public enum RenderPlanPolicyViolationType {

    /** Unbound capability node marked for execution. */
    UNBOUND_NODE_EXECUTABLE,

    /** Non-production provider in PRODUCTION mode. */
    NON_PRODUCTION_PROVIDER,

    /** Provider with autoDispatch=false in automatic mode. */
    AUTO_DISPATCH_DISABLED,

    /** Provider tool binary not available. */
    TOOL_UNAVAILABLE,

    /** OpenFX capability without host. */
    OPENFX_NO_HOST,

    /** OpenCue submit not enabled. */
    OPENCUE_SUBMIT_DISABLED,

    /** Raw command found in step. */
    RAW_COMMAND_EXPOSED,

    /** Process environment found in step. */
    PROCESS_ENVIRONMENT_EXPOSED,

    /** Local materialized path exposed in public surface. */
    LOCAL_PATH_EXPOSED,

    /** Storage internals exposed (bucket/key/rootPath/etc). */
    STORAGE_INTERNALS_EXPOSED,

    /** Final output missing verification or registration step. */
    OUTPUT_STEPS_MISSING,

    /** Plan dependency graph is cyclic. */
    CYCLIC_DEPENDENCY,

    /** Step ID is not deterministic. */
    NON_DETERMINISTIC_STEP_ID,

    /** Dependency graph is invalid. */
    INVALID_DEPENDENCY_GRAPH
}
