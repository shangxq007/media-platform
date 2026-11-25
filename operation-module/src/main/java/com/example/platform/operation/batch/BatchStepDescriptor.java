package com.example.platform.operation.batch;

/**
 * Ordered provenance of one planned batch step. Distinct from any revision id:
 * it is plan/provenance material, never a commit identity.
 */
@org.springframework.modulith.NamedInterface("batch")
public record BatchStepDescriptor(
        int index,
        String definitionId,
        String version,
        String parameterDigest,
        String targetIdentity,
        String stepPlanDigest,
        String candidateHash,
        java.util.List<Change> changes,
        com.example.platform.operation.operation.OperationParameters.SourceStreamInput input) {
    public record Change(String identity, boolean primary) {}


    public BatchStepDescriptor {
        changes = java.util.List.copyOf(changes);
        if (index < 0) {
            throw new IllegalArgumentException("step index must be non-negative");
        }
        requireText(definitionId, "definitionId");
        requireText(version, "version");
        requireText(parameterDigest, "parameterDigest");
        requireText(targetIdentity, "targetIdentity");
        requireText(stepPlanDigest, "stepPlanDigest");
        requireText(candidateHash, "candidateHash");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
    }
}
