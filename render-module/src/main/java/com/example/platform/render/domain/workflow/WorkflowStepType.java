package com.example.platform.render.domain.workflow;

/**
 * Type of workflow step — semantic, not provider/backend/environment-specific.
 * Internal domain model.
 */
public enum WorkflowStepType {
    INGEST_PRODUCT,
    ANALYZE_ASR,
    ANALYZE_SCENE,
    VALIDATE_INPUT,
    NORMALIZE_TIMELINE,
    APPLY_TEMPLATE,
    COMPILE_TIMELINE,
    RENDER_TIMELINE,
    REGISTER_PRODUCT,
    LOOKUP_RESULT,
    DELIVER_PRODUCT,
    NOTIFY
}
