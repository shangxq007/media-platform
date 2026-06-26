package com.example.platform.render.domain.execution;

/**
 * An input to an execution step — product + storage reference.
 * No local path. Materialization policy determined by Storage Runtime.
 */
public record ExecutionInput(
        String productId,
        String storageReferenceId,
        String representationKind,
        String materializationPolicy) {

    public static ExecutionInput of(String productId, String storageRefId) {
        return new ExecutionInput(productId, storageRefId, "MEDIA_FILE", "LOCAL_CACHE");
    }
}
