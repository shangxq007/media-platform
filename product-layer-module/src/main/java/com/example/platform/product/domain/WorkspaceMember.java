package com.example.platform.product.domain;

import java.time.Instant;

/**
 * Association between a user and a workspace with role.
 */
public record WorkspaceMember(
        String id,
        String workspaceId,
        String userId,
        UserRole role,
        Instant joinedAt,
        Instant lastActiveAt
) {
    /**
     * Create a new workspace member.
     */
    public static WorkspaceMember create(String id, String workspaceId, String userId, UserRole role) {
        Instant now = Instant.now();
        return new WorkspaceMember(id, workspaceId, userId, role, now, now);
    }

    /**
     * Check if member has a specific role.
     */
    public boolean hasRole(UserRole role) {
        return this.role == role;
    }

    /**
     * Check if member can perform an action.
     */
    public boolean canPerform(UserAction action) {
        return role.canPerform(action);
    }

    /**
     * Update the role.
     */
    public WorkspaceMember withRole(UserRole newRole) {
        return new WorkspaceMember(id, workspaceId, userId, newRole, joinedAt, Instant.now());
    }

    /**
     * Update last active timestamp.
     */
    public WorkspaceMember touch() {
        return new WorkspaceMember(id, workspaceId, userId, role, joinedAt, Instant.now());
    }
}
