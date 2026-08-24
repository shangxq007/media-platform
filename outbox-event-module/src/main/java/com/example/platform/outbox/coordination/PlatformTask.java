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

}
