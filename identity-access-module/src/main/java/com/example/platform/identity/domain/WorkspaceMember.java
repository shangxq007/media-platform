package com.example.platform.identity.domain;

import java.time.Instant;

public record WorkspaceMember(
        String id,
        String workspaceId,
        String userId,
        String role,
        MemberStatus status,
        Instant joinedAt,
        Instant updatedAt) {

    public enum MemberStatus {
        ACTIVE, INACTIVE, REMOVED
    }
}
