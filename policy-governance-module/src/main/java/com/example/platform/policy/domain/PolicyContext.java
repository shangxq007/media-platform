package com.example.platform.policy.domain;

import java.util.Map;

public record PolicyContext(
        String userId,
        String role,
        String tenantId,
        String workspaceId,
        String resourceType,
        String requestSource,
        Map<String, Object> attributes
) {}
