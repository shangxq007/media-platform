package com.example.platform.ingest.preflight.policy;

public record PreflightPolicyFindingCode(String value) {
    public PreflightPolicyFindingCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Finding code must not be blank");
        }
    }
}
