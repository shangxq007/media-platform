package com.example.platform.execution.result;

import java.util.Optional;

/** Durable result port. Implementations must persist before domain materialization and support replay. */
public interface PlatformResultRepository {
    PlatformCompletionReference record(PlatformCompletionReference completion);
    Optional<PlatformCompletionReference> find(String executionId, String attemptId);
    Optional<PlatformCompletionReference> findByIdempotency(String idempotencyKey, String requestHash);
}
