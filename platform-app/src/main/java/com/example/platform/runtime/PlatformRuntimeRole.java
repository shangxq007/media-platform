package com.example.platform.runtime;

/** Explicit process role for deployments that separate API admission from Temporal workers. */
public enum PlatformRuntimeRole {
    API,
    WORKER;

    public static PlatformRuntimeRole parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("platform.runtime.role is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("platform.runtime.role must be API or WORKER", ex);
        }
    }
}
