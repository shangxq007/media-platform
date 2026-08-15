package com.example.platform.render.domain.operation;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM17/OE): typed Operation semantic error
 * vocabulary. Capability/policy execution errors stay future (OperationPlan/
 * application layer); only categories needed by Operation Model are active now.
 */
public enum OperationErrorCode {
    INVALID_PARAMETER,
    INVALID_SCOPE,
    STALE_BASE_REVISION,
    UNSUPPORTED_OPERATION,
    CAPABILITY_UNAVAILABLE,
    POLICY_DENIED,
    CANONICAL_INVARIANT_VIOLATION
}
