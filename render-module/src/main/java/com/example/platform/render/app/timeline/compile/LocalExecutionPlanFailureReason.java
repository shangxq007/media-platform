package com.example.platform.render.app.timeline.compile;

/**
 * Reasons why a local execution plan run failed.
 *
 * <p>Internal only.</p>
 */
public enum LocalExecutionPlanFailureReason {
    POLICY_REJECTED,
    NON_EXECUTABLE_PLAN,
    UNSUPPORTED_PROVIDER,
    TOOL_UNAVAILABLE,
    MATERIALIZATION_FAILED,
    EXECUTION_FAILED,
    OUTPUT_VERIFICATION_FAILED,
    OUTPUT_REGISTRATION_FAILED,
    DEPENDENCY_FAILED,
    CONTEXT_MISSING
}
