package com.example.platform.outbox.domain;

/**
 * Status lifecycle for platform coordination tasks.
 */
public enum TaskStatus {
    PENDING,
    LEASED,
    RUNNING,
    COMPLETED,
    FAILED
}
