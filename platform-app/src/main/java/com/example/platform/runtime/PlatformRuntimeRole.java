package com.example.platform.runtime;

/** Explicit process role for deployments that separate API admission from Temporal workers. */
public enum PlatformRuntimeRole {
    API,
    WORKER,
    /**
     * Combined process role: HTTP (API) and Temporal workers coexist in one process. This is an
     * explicit third role, not a relaxation of API or WORKER.
     */
    COMBINED;

    public static PlatformRuntimeRole parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("platform.runtime.role is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("platform.runtime.role must be API, WORKER or COMBINED", ex);
        }
    }
}
