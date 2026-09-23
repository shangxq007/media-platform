package com.example.platform.execution.result;

import java.util.Objects;

/** Durable completion boundary; domain adapters register materialized outputs after commit. */
public record PlatformCompletionReference(String executionId, String attemptId, String resultId,
        String storageReceipt, String artifactCommitId, Status status) {
    public PlatformCompletionReference {
        Objects.requireNonNull(executionId); Objects.requireNonNull(attemptId); Objects.requireNonNull(resultId);
        Objects.requireNonNull(status);
        if (storageReceipt == null || storageReceipt.isBlank()) throw new IllegalArgumentException("storageReceipt required");
        if (artifactCommitId == null || artifactCommitId.isBlank()) throw new IllegalArgumentException("artifactCommitId required");
    }
    public enum Status { COMPLETED, CANCELLED, FAILED, COMPENSATING }
}
