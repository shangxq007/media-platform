package com.example.platform.ingest.preflight.persistence.writer;

public enum SafePreflightPersistenceWriteOutcome {
    SKIPPED_DISABLED,
    SKIPPED_UNSUPPORTED_MODE,
    SKIPPED_INVALID_INPUT,
    RECORDED,
    FAILED_OPEN
}
