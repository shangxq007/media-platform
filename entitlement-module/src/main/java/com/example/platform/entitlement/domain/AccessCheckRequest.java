package com.example.platform.entitlement.domain;

import java.util.Map;
import java.math.BigDecimal;

public record AccessCheckRequest(
        String tenantId,
        String workspaceId,
        String userId,
        String subjectType,
        String subjectId,
        String action,
        String resourceType,
        String resourceId,
        String featureKey,
        String requestedPreset,
        String providerKey,
        String requestSource,
        BigDecimal requestedQuota,
        Map<String, Object> context,
        VersionedRequirement requirement
) {
    public record VersionedRequirement(String identity, String version, String quotaKey,
            java.time.Instant periodStart, java.time.Instant periodEnd) {
        public VersionedRequirement {
            if (identity == null || identity.isBlank() || version == null || !version.matches("[1-9][0-9]*")
                    || quotaKey == null || quotaKey.isBlank() || periodStart == null || periodEnd == null
                    || !periodEnd.isAfter(periodStart)) throw new IllegalArgumentException("INVALID_ENTITLEMENT_REQUIREMENT");
            Long.parseLong(version);
        }
    }
    public AccessCheckRequest(String tenantId, String workspaceId, String userId, String subjectType,
            String subjectId, String action, String resourceType, String resourceId, String featureKey,
            String requestedPreset, String providerKey, String requestSource, BigDecimal requestedQuota,
            Map<String,Object> context) {
        this(tenantId, workspaceId, userId, subjectType, subjectId, action, resourceType, resourceId,
                featureKey, requestedPreset, providerKey, requestSource, requestedQuota, context, null);
    }
}
