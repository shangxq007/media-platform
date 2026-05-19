package com.example.platform.compatibility.domain;

/**
 * Status of a migration run.
 */
public enum MigrationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    PARTIAL,
    ROLLED_BACK
}
