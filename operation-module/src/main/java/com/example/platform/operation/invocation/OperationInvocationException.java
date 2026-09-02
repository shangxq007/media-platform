package com.example.platform.operation.invocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sole public failure carrier for invocation failures.
 *
 * <p>Diagnostics are bounded, immutable, and must contain only values safe to
 * return across the invocation boundary. Lower-layer exception causes and
 * mechanics are deliberately not accepted or exposed.</p>
 */
@org.springframework.modulith.NamedInterface("invocation")
public final class OperationInvocationException extends RuntimeException {

    private static final int MAX_DIAGNOSTICS = 16;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_VALUE_LENGTH = 512;

    private final OperationInvocationFailureCode code;
    private final Map<String, String> diagnostics;

    public OperationInvocationException(
            OperationInvocationFailureCode code,
            Map<String, String> diagnostics) {
        super(requireCode(code).name(), null, false, false);
        this.code = code;
        this.diagnostics = safeDiagnostics(diagnostics);
    }

    public OperationInvocationFailureCode code() {
        return code;
    }

    public Map<String, String> diagnostics() {
        return diagnostics;
    }

    private static OperationInvocationFailureCode requireCode(OperationInvocationFailureCode code) {
        return Objects.requireNonNull(code, "code");
    }

    private static Map<String, String> safeDiagnostics(Map<String, String> source) {
        Objects.requireNonNull(source, "diagnostics");
        if (source.size() > MAX_DIAGNOSTICS) {
            throw new IllegalArgumentException("diagnostics exceeds " + MAX_DIAGNOSTICS + " entries");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            requireDiagnosticText(key, "diagnostic key", MAX_KEY_LENGTH);
            requireDiagnosticText(value, "diagnostic value", MAX_VALUE_LENGTH);
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }

    private static void requireDiagnosticText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds " + maximumLength + " characters");
        }
    }
}
