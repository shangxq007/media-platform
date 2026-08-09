package com.example.platform.extension.runtime;

import java.util.Objects;

/**
 * Canonical runtime error (frozen PRV2-ADR-016).
 *
 * <p>Provider SDK exceptions are mapped into {@link PluginRuntimeErrorCategory}
 * and wrapped here; raw SDK/native/HTTP exceptions never cross the public
 * runtime API (AR-PRV2-08, PRV2-RED-012).</p>
 *
 * @param category            canonical error category
 * @param code                stable error code (e.g. "PRV2-404")
 * @param message             human-readable message (no secret values)
 * @param providerOperationId optional provider-side operation identifier
 */
public record PluginRuntimeError(
        PluginRuntimeErrorCategory category,
        String code,
        String message,
        String providerOperationId) {

    public PluginRuntimeError {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    public static PluginRuntimeError of(PluginRuntimeErrorCategory category, String code, String message) {
        return new PluginRuntimeError(category, code, message, null);
    }
}
