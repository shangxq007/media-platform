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
        Map<String, Object> context
) {}
