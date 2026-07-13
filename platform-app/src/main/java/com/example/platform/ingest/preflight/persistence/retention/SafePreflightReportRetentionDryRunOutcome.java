package com.example.platform.ingest.preflight.persistence.retention;

public enum SafePreflightReportRetentionDryRunOutcome {
    DRY_RUN_COMPLETE,
    SKIPPED_DISABLED,
    SKIPPED_INVALID_CONFIG,
    SKIPPED_UNSUPPORTED_MODE,
    FAILED_SAFE
}
