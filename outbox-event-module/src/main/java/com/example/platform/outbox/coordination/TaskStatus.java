package com.example.platform.outbox.coordination;

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
