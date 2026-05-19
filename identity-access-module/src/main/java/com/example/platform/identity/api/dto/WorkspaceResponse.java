package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.Workspace;
import java.time.Instant;

public record WorkspaceResponse(
        String id,
        String tenantId,
        String name,
        String description,
        String planTier,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.id(), workspace.tenantId(), workspace.name(),
                workspace.description(), workspace.planTier(),
                workspace.status().name(), workspace.createdAt(), workspace.updatedAt());
    }
}
