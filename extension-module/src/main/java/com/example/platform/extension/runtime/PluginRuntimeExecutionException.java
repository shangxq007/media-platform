package com.example.platform.extension.runtime;

import java.util.Objects;

/**
 * Pre-execution rejection raised by the runtime (frozen PRV2-ADR-016).
 *
 * <p>Wraps provider SDK/native/HTTP exceptions into the canonical taxonomy —
 * raw SDK exceptions never cross the public runtime API (AR-PRV2-08).
 * The internal cause may be retained locally but is never part of the public
 * contract surface.</p>
 */
public class PluginRuntimeExecutionException extends RuntimeException {

    private final PluginRuntimeErrorCategory category;
    private final String code;

    public PluginRuntimeExecutionException(PluginRuntimeErrorCategory category, String code, String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public PluginRuntimeExecutionException(PluginRuntimeErrorCategory category, String code, String message, Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public PluginRuntimeErrorCategory category() {
        return category;
    }

    public String code() {
        return code;
    }
}
