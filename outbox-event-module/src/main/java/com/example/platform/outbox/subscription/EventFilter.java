package com.example.platform.outbox.subscription;

import java.util.Map;

/**
 * Event filter criteria. No arbitrary expression language. Deterministic.
 */
public record EventFilter(
    String tenantId,
    String projectId,
    String aggregateType,
    String aggregateId,
    Map<String, Object> payloadEquals
) {
    public static EventFilter none() {
        return new EventFilter(null, null, null, null, null);
    }

    public static EventFilter tenantProject(String tenantId, String projectId) {
        return new EventFilter(tenantId, projectId, null, null, null);
    }
}
