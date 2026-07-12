package com.example.platform.outbox.coordination;

/**
 * Status lifecycle for platform coordination jobs.
 */
public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
