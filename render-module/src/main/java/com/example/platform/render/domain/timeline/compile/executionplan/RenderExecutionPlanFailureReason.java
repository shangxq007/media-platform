package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Reasons why a render execution plan cannot be executed.
 *
 * <p>Internal only — captures why the plan was rejected by the policy guard
 * or why compilation produced a non-executable plan.</p>
 */
public enum RenderExecutionPlanFailureReason {

    /** Required capability node is unbound. */
    UNBOUND_CAPABILITY_NODE,

    /** Provider execution document draft is missing for a bound node. */
    MISSING_DOCUMENT_DRAFT,

    /** Non-production provider in PRODUCTION mode. */
    NON_PRODUCTION_PROVIDER_IN_PRODUCTION_MODE,

    /** Provider tool is not available. */
    PROVIDER_TOOL_UNAVAILABLE,

    /** OpenCue submit requested but not enabled. */
    OPENCUE_NOT_ENABLED,

    /** OpenFX capability without host environment. */
    OPENFX_REQUIRES_HOST,

    /** Plan exceeds maximum step count. */
    STEP_COUNT_EXCEEDED,

    /** Plan contains a cycle. */
    CYCLIC_DEPENDENCY,

    /** Missing final output step. */
    MISSING_FINAL_OUTPUT,

    /** Policy guard rejected the plan. */
    POLICY_VIOLATION
}
