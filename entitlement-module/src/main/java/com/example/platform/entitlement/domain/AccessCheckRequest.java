package com.example.platform.entitlement.domain;

import java.util.Map;

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
        Long requestedQuota,
        Map<String, Object> context
) {}
