package com.example.platform.workerfabric.admission;

/** Durable dispatch reference; no media bytes, storage paths, secrets or provider configuration. */
public record CapabilityExecutionRequested(String executionId, String tenantId, String projectId, long generation) {
    public CapabilityExecutionRequested {
        if (executionId == null || executionId.isBlank() || tenantId == null || tenantId.isBlank()
                || projectId == null || projectId.isBlank() || generation < 1) {
            throw new IllegalArgumentException("scoped execution dispatch reference required");
        }
    }
}
