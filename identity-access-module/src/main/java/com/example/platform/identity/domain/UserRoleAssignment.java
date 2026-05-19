package com.example.platform.identity.domain;

import java.time.Instant;

public record UserRoleAssignment(
        String id,
        String tenantId,
        String workspaceId,
        String userId,
        String roleId,
        String assignedBy,
        Instant createdAt) {}
