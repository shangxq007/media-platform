package com.example.platform.ingest.preflight.policy;

public record UserSafePolicyMessage(
    String code,
    String message,
    PreflightPolicySeverity severity,
    String suggestion
) {
    public UserSafePolicyMessage(String code, String message, PreflightPolicySeverity severity) {
        this(code, message, severity, null);
    }
}
