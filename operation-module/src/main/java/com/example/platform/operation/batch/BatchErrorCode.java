package com.example.platform.operation.batch;

/** Stable batch failure codes. Every failure means: no revision, no head change, no partial state. */
@org.springframework.modulith.NamedInterface("batch")
public enum BatchErrorCode {
    EMPTY_BATCH,
    MIXED_BASE,
    CROSS_AGGREGATE_BATCH_UNSUPPORTED,
    BASE_REVISION_NOT_FOUND,
    STEP_RESOLUTION_FAILED,
    STEP_PLAN_FAILED,
    STEP_VALIDATION_FAILED,
    FINAL_VALIDATION_FAILED,
    STALE_BASE_REVISION,
    PLAN_CHANGED,
    COMMAND_ID_CONFLICT,
    AUTHORIZATION_DENIED,
    AUTHORIZATION_CONTEXT_MISMATCH,
    REF_NOT_SUPPORTED,
    UNSUPPORTED_BATCH_OPERATION,
    PERSISTENCE_FAILURE
}
