package com.example.platform.operation.batch;

/**
 * Identity of one versioned aggregate that a batch targets.
 *
 * <p>v1 permits exactly one aggregate per batch; the batch target is the CAS
 * authority for the whole commit. {@code refId} is the movable head reference
 * inside that aggregate (only {@code main} is enabled in v1).
 */
@org.springframework.modulith.NamedInterface("batch")
public record VersionedAggregateRef(
        String aggregateKind,
        String tenantId,
        String projectId,
        String aggregateId,
        String refId) {

    public VersionedAggregateRef {
        requireToken(aggregateKind, "aggregateKind", 32);
        requireToken(tenantId, "tenantId", 64);
        requireToken(projectId, "projectId", 64);
        requireToken(aggregateId, "aggregateId", 64);
        requireToken(refId, "refId", 64);
    }

    private static void requireToken(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(field + " must be a bounded identifier token");
        }
    }
}
