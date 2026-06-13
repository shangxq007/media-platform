package com.example.platform.product.domain;

import java.time.Instant;

/**
 * Workspace represents a multi-tenant container for projects and assets.
 * Each workspace has its own isolated resource namespace.
 */
public record Workspace(
        String id,
        String name,
        String description,
        String ownerId,
        WorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a new workspace.
     */
    public static Workspace create(String id, String name, String description, String ownerId) {
        Instant now = Instant.now();
        return new Workspace(id, name, description, ownerId, WorkspaceStatus.ACTIVE, now, now);
    }

    /**
     * Check if workspace is active.
     */
    public boolean isActive() {
        return status == WorkspaceStatus.ACTIVE;
    }

    /**
     * Update workspace details.
     */
    public Workspace withDetails(String name, String description) {
        return new Workspace(id, name, description, ownerId, status, createdAt, Instant.now());
    }

    /**
     * Archive the workspace.
     */
    public Workspace archive() {
        return new Workspace(id, name, description, ownerId, WorkspaceStatus.ARCHIVED, createdAt, Instant.now());
    }
}
