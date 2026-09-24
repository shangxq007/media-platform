package com.example.platform.composition.app;

import com.example.platform.execution.result.PlatformCompletionReference;
import java.util.Optional;

public interface CompositionResultRepository {
    PlatformCompletionReference record(PlatformCompletionReference completion, long expectedGeneration);
    Optional<PlatformCompletionReference> find(String executionId, String attemptId);
    Optional<PlatformCompletionReference> findByIdempotency(String tenantId, String key, String requestHash);
    Optional<MaterializedResult> findMaterialized(String tenantId, String key, String requestHash);
    void recordMaterialized(String tenantId, String key, String requestHash, MaterializedResult result, long generation);
    record MaterializedResult(String executionId, String attemptId, String placementId, String digest, long length,
            String artifactId) {}
}
