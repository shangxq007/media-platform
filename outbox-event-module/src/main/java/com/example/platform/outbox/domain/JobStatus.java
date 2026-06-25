package com.example.platform.outbox.domain;

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
