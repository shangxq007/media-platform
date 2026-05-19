package com.example.platform.entitlement.domain;

public record EntitlementSubject(
        String subjectType,
        String subjectId,
        String tenantId,
        String workspaceId
) {}
