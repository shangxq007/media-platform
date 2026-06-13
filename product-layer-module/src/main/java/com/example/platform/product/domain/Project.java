package com.example.platform.product.domain;

import java.time.Instant;

/**
 * Project represents a video editing project within a workspace.
 * Contains timeline, render history, and asset references.
 */
public record Project(
        String id,
        String workspaceId,
        String name,
        String description,
        String ownerId,
        ProjectStatus status,
        String timelineSnapshotId,
        String templateId,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a new project.
     */
    public static Project create(String id, String workspaceId, String name, String description, String ownerId) {
        Instant now = Instant.now();
        return new Project(id, workspaceId, name, description, ownerId, ProjectStatus.DRAFT,
                null, null, now, now);
    }

    /**
     * Create a project from a template.
     */
    public static Project fromTemplate(String id, String workspaceId, String name, String description,
                                        String ownerId, String templateId) {
        Instant now = Instant.now();
        return new Project(id, workspaceId, name, description, ownerId, ProjectStatus.DRAFT,
                null, templateId, now, now);
    }

    /**
     * Check if project is editable.
     */
    public boolean isEditable() {
        return status == ProjectStatus.DRAFT || status == ProjectStatus.IN_PROGRESS;
    }

    /**
     * Update project details.
     */
    public Project withDetails(String name, String description) {
        return new Project(id, workspaceId, name, description, ownerId, status,
                timelineSnapshotId, templateId, createdAt, Instant.now());
    }

    /**
     * Update timeline snapshot.
     */
    public Project withTimeline(String timelineSnapshotId) {
        return new Project(id, workspaceId, name, description, ownerId, status,
                timelineSnapshotId, templateId, createdAt, Instant.now());
    }

    /**
     * Mark project as in progress.
     */
    public Project markInProgress() {
        return new Project(id, workspaceId, name, description, ownerId, ProjectStatus.IN_PROGRESS,
                timelineSnapshotId, templateId, createdAt, Instant.now());
    }

    /**
     * Mark project as completed.
     */
    public Project markCompleted() {
        return new Project(id, workspaceId, name, description, ownerId, ProjectStatus.COMPLETED,
                timelineSnapshotId, templateId, createdAt, Instant.now());
    }

    /**
     * Archive the project.
     */
    public Project archive() {
        return new Project(id, workspaceId, name, description, ownerId, ProjectStatus.ARCHIVED,
                timelineSnapshotId, templateId, createdAt, Instant.now());
    }
}
