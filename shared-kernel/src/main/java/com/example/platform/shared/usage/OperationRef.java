package com.example.platform.shared.usage;

/** Logical operation and optional execution-attempt identity. */
public record OperationRef(String operationId, String attemptId) {

    public OperationRef {
        operationId = requireNonBlank(operationId, "operationId");
        if (attemptId != null && attemptId.isBlank()) {
            throw new IllegalArgumentException("attemptId must not be blank when present");
        }
    }

    public static OperationRef of(String operationId) {
        return new OperationRef(operationId, null);
    }

    public static OperationRef of(String operationId, String attemptId) {
        return new OperationRef(operationId, attemptId);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
