package com.example.platform.extension.runtime;

/**
 * Bounded canonical runtime error taxonomy (frozen PRV2-ADR-016).
 *
 * <p>Provider SDK exceptions must be mapped into these categories and must never
 * cross the public runtime API (AR-PRV2-08).</p>
 */
public enum PluginRuntimeErrorCategory {
    VALIDATION,
    CAPABILITY_UNSUPPORTED,
    PROVIDER_UNAVAILABLE,
    RATE_LIMITED,
    TIMEOUT,
    CANCELLED,
    EXECUTION_FAILED,
    RESOURCE_UNAVAILABLE,
    SECURITY_DENIED
}
