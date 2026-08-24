package com.example.platform.workerfabric.domain;

/** Typed outcome of completion preconditions and the atomic authority transition. */
public enum CompletionDecision {
    COMPLETED,
    DUPLICATE_NOOP,
    BACKEND_NOT_SUCCEEDED_REJECTED,
    EXPECTED_OUTPUT_INVALID_REJECTED,
    ARTIFACT_NOT_COMMITTED_REJECTED,
    STALE_ATTEMPT_REJECTED,
    STALE_GENERATION_REJECTED,
    EXPECTED_TASK_MISMATCH_REJECTED
}
