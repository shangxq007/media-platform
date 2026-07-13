package com.example.platform.ingest.preflight.persistence.contract;

public record SafePreflightPersistenceRetentionPolicy(
    int retentionDays,
    int maxRetentionDays,
    boolean deleteOnExpiry,
    boolean redactBeforeDelete
) {
    public static final int MAX_RETENTION_DAYS = 7;

    public SafePreflightPersistenceRetentionPolicy {
        if (retentionDays < 1 || retentionDays > MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException("retentionDays must be 1-7");
        }
        if (maxRetentionDays != MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException("maxRetentionDays must be 7");
        }
    }

    public static SafePreflightPersistenceRetentionPolicy defaultPolicy() {
        return new SafePreflightPersistenceRetentionPolicy(7, 7, true, false);
    }
}
