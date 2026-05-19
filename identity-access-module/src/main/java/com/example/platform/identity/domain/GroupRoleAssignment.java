package com.example.platform.identity.domain;

import java.time.Instant;

public record GroupRoleAssignment(
        String id,
        String workspaceId,
        String groupId,
        String roleId,
        Instant assignedAt) {}
