package com.example.platform.shared.audit;

import java.util.Map;

/**
 * Port interface for audit recording, implemented by audit-compliance module.
 */
public interface AuditPort {
    void record(String actorType, String action, String category,
            String resourceType, String resourceId, Map<String, Object> payload);
}
