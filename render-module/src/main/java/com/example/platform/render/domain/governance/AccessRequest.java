package com.example.platform.render.domain.governance;

import java.util.Map;

/**
 * Access request — evaluated by AccessGovernanceService.
 */
public record AccessRequest(
        Subject subject,
        Resource resource,
        String action,
        Map<String, Object> context) {

    public record Subject(String subjectId, String subjectType, String tenantId) {}
    public record Resource(String resourceId, String resourceType, String ownerId, String trustLevel) {}

    public static AccessRequest of(String subjectId, String resourceId, String resourceType, String action) {
        return new AccessRequest(new Subject(subjectId, "USER", "system"),
                new Resource(resourceId, resourceType, null, "FULLY_TRUSTED"),
                action, Map.of());
    }
}
