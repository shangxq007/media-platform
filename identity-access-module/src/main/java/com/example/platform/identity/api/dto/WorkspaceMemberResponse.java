package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.WorkspaceMember;
import java.time.Instant;

public record WorkspaceMemberResponse(
        String id,
        String workspaceId,
        String userId,
        String role,
        String status,
        Instant joinedAt,
        Instant updatedAt) {

    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.id(), member.workspaceId(), member.userId(),
                member.role(), member.status().name(),
                member.joinedAt(), member.updatedAt());
    }
}
