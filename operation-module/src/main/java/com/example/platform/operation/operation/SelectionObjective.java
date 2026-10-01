package com.example.platform.operation.operation;

/**
 * Provider-neutral selection objective for implementation resolution
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, DOM-OPERATION-001 layer 3).
 */
public enum SelectionObjective {

    MINIMIZE_COST,

    MINIMIZE_LATENCY,

    PREFER_DETERMINISTIC
}
