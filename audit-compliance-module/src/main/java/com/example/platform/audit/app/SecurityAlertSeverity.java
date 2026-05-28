package com.example.platform.audit.app;

/**
 * Severity levels for security alerts.
 */
public enum SecurityAlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Parse severity from string, defaulting to MEDIUM.
     */
    public static SecurityAlertSeverity fromString(String severity) {
        if (severity == null || severity.isBlank()) return MEDIUM;
        try {
            return valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
