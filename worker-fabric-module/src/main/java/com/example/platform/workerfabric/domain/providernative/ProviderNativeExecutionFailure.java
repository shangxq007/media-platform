package com.example.platform.workerfabric.domain.providernative;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Fail-closed typed failure for provider-native lowering/adaptation.
 *
 * <p>Provider-native stderr/API bodies may be attached only as bounded diagnostics; the typed
 * failure code remains the architecture-level identity.
 */
public final class ProviderNativeExecutionFailure extends RuntimeException {

    private final ProviderNativeFailureCode code;
    private final Map<String, String> diagnostics;

    public ProviderNativeExecutionFailure(ProviderNativeFailureCode code, String message) {
        this(code, message, Map.of());
    }

    public ProviderNativeExecutionFailure(
            ProviderNativeFailureCode code,
            String message,
            Map<String, String> diagnostics) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        Objects.requireNonNull(diagnostics, "diagnostics");
        TreeMap<String, String> canonical = new TreeMap<>();
        diagnostics.forEach((key, value) -> canonical.put(
                Objects.requireNonNull(key, "diagnostics key"),
                Objects.requireNonNull(value, "diagnostics value")));
        this.diagnostics = Map.copyOf(canonical);
    }

    public ProviderNativeFailureCode code() {
        return code;
    }

    public Map<String, String> diagnostics() {
        return diagnostics;
    }
}
