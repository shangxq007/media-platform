package com.example.platform.ingest.preflight.persistence.retention;

public enum SafePreflightReportRetentionDryRunStrategy {
    READ_TIME_EXPIRATION_ONLY,
    MARK_EXPIRED_CANDIDATE,
    PHYSICAL_DELETE_CANDIDATE
}
