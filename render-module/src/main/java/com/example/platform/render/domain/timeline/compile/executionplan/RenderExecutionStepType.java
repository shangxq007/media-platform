package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Types of execution steps in a render execution plan.
 *
 * <p>v0 steps are planning placeholders only — they do not execute.</p>
 */
public enum RenderExecutionStepType {

    /** Materialize input from storage. */
    MATERIALIZE_INPUT,

    /** Prepare provider execution document. */
    PREPARE_PROVIDER_DOCUMENT,

    /** Execute provider (placeholder in v0, not executable). */
    EXECUTE_PROVIDER,

    /** Verify output artifact. */
    VERIFY_OUTPUT,

    /** Register output in storage/product system. */
    REGISTER_OUTPUT,

    /** Link product dependency edge. */
    LINK_PRODUCT_DEPENDENCY,

    /** Finalize render job. */
    FINALIZE_RENDER
}
