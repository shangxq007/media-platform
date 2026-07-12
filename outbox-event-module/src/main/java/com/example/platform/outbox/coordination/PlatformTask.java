package com.example.platform.outbox.coordination;

import java.time.Instant;

/**
 * Domain model for a platform coordination task — individual work unit within a job.
 */
public record PlatformTask(
        String id,
        String jobId,
        String taskType,
        TaskCapability capability,
        String provider,
        TaskStatus status,
        int attemptCount,
        int maxAttempts,
        String resultRef,
        String resultJson,
        String errorMessage,
        int bitPosition,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean canRetry() {
        return attemptCount < maxAttempts && status != TaskStatus.COMPLETED;
    }

    public boolean isLeasable() {
        return status == TaskStatus.PENDING;
    }

    public PlatformTask markLeased() {
        return new PlatformTask(id, jobId, taskType, capability, provider, TaskStatus.LEASED,
                attemptCount + 1, maxAttempts, resultRef, resultJson, errorMessage, bitPosition,
                Instant.now(), completedAt, createdAt, Instant.now());
    }

    public PlatformTask markCompleted(String resultRef) {
        return new PlatformTask(id, jobId, taskType, capability, provider, TaskStatus.COMPLETED,
                attemptCount, maxAttempts, resultRef, resultJson, errorMessage, bitPosition,
                startedAt, Instant.now(), createdAt, Instant.now());
    }

    public PlatformTask markFailed(String error) {
        return new PlatformTask(id, jobId, taskType, capability, provider, TaskStatus.FAILED,
                attemptCount, maxAttempts, resultRef, resultJson, error, bitPosition,
                startedAt, completedAt, createdAt, Instant.now());
    }
}
