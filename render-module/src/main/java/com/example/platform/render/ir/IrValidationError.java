package com.example.platform.render.ir;

import java.util.Objects;

/**
 * A structured validation error with stable machine-readable code, safe message,
 * semantic path, and retryability classification.
 */
public record IrValidationError(
    IrErrorCode code,
    String message,
    String path,
    boolean retryable
) {
    public IrValidationError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * Convenience constructor that derives retryability and default message from code.
     */
    public static IrValidationError of(IrErrorCode code, String path, String detail) {
        String msg = code.message();
        if (detail != null && !detail.isBlank()) {
            msg = msg + ": " + detail;
        }
        return new IrValidationError(code, msg, path != null ? path : "", code.retryable());
    }

    @Override
    public String toString() {
        return "[" + code.code() + "] " + message + (path.isEmpty() ? "" : " at " + path);
    }
}
