package com.example.platform.sandbox.execution;

import java.util.function.BooleanSupplier;

/** A backend that observes cancellation throughout its bounded process lifetime. */
public interface CancellableExecutionBackend extends ExecutionBackend {
    ExecutionResult execute(ExecutionRequest request, BooleanSupplier cancelled);

    /** Ordinary execution uses the same backend mechanics without an external cancellation signal. */
    @Override
    default ExecutionResult execute(ExecutionRequest request) {
        return execute(request, () -> false);
    }
}
