package com.example.platform.ingest.preflight.persistence.retention;

public record SafePreflightReportRetentionSafetyCheck(
    String code,
    boolean passed,
    String message
) {}
