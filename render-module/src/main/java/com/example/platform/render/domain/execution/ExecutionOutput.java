package com.example.platform.render.domain.execution;

/**
 * Expected output of an execution step.
 * No storage allocation — only type and representation.
 */
public record ExecutionOutput(
        String expectedProductType,
        String representationKind,
        boolean temporary) {

    public static ExecutionOutput of(String productType, String repKind) {
        return new ExecutionOutput(productType, repKind, false);
    }
}
