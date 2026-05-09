package com.example.platform.scheduler.domain;

import java.time.Instant;
import java.util.Objects;

public record ScheduledJobRun(
        String id,
        String jobDefinitionId,
        JobStatus status,
        Instant startedAt,
        Instant finishedAt,
        int retryCount,
        String errorMessage
) {
    public ScheduledJobRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(jobDefinitionId, "jobDefinitionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }

    public ScheduledJobRun withStatus(JobStatus newStatus) {
        return new ScheduledJobRun(id, jobDefinitionId, newStatus, startedAt, finishedAt, retryCount, errorMessage);
    }

    public ScheduledJobRun withFinishedAt(Instant finishedAt) {
        return new ScheduledJobRun(id, jobDefinitionId, status, startedAt, finishedAt, retryCount, errorMessage);
    }

    public ScheduledJobRun withRetryCount(int retryCount) {
        return new ScheduledJobRun(id, jobDefinitionId, status, startedAt, finishedAt, retryCount, errorMessage);
    }

    public ScheduledJobRun withErrorMessage(String errorMessage) {
        return new ScheduledJobRun(id, jobDefinitionId, status, startedAt, finishedAt, retryCount, errorMessage);
    }

    public boolean isPending() {
        return status == JobStatus.PENDING;
    }

    public boolean isFailed() {
        return status == JobStatus.FAILED;
    }

    public boolean isDeadLetter() {
        return status == JobStatus.DEAD_LETTER;
    }

    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || status == JobStatus.DEAD_LETTER;
    }
}
