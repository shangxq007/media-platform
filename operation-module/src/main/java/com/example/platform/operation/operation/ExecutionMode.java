package com.example.platform.operation.operation;

/**
 * Provider-neutral execution mode of an operation
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, DOM-OPERATION-001 layer 4).
 */
public enum ExecutionMode {

    /** Durable, user-facing asynchronous execution (bounded provider invocation inside the worker). */
    ASYNCHRONOUS_DURABLE,

    /** Single bounded synchronous invocation. */
    SYNCHRONOUS_BOUNDED
}
