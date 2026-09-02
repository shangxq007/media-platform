package com.example.platform.operation.invocation;

/** Stable public failure categories for the Operation invocation boundary. */
@org.springframework.modulith.NamedInterface("invocation")
public enum OperationInvocationFailureCode {
    UNSUPPORTED_OPERATION,
    INVALID_REQUEST,
    INVALID_SCOPE,
    INVALID_PARAMETER,
    BASE_REVISION_NOT_FOUND,
    STALE_BASE_REVISION,
    SOURCE_REFERENCE_INVALID,
    CANDIDATE_INVALID,
    PLAN_CHANGED,
    AUTHORIZATION_DENIED,
    AUTHORIZATION_CONTEXT_MISMATCH,
    IDEMPOTENCY_CONFLICT,
    TARGET_MISSING,
    PLACEMENT_CONFLICT,
    CANONICAL_INVARIANT_VIOLATION,
    PERSISTENCE_FAILURE,
    APPLY_FAILURE
}
