package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.WorkspaceGroup;
import java.time.Instant;

public record WorkspaceGroupResponse(
        String id,
        String workspaceId,
        String name,
        String description,
        Instant createdAt) {

    public static WorkspaceGroupResponse from(WorkspaceGroup group) {
        return new WorkspaceGroupResponse(
                group.id(), group.workspaceId(), group.name(),
                group.description(), group.createdAt());
    }
}
