package com.example.platform.render.infrastructure.productization.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Workspace represents a collaborative environment for multiple users.
 * Supports multi-user timeline editing with conflict resolution.
 */
public record Workspace(
        String workspaceId,
        String name,
        String description,
        String ownerId,
        List<WorkspaceMember> members,
        List<SharedProject> sharedProjects,
        List<ActiveSession> activeSessions,
        WorkspaceSettings settings,
        WorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a new workspace.
     */
    public static Workspace create(String workspaceId, String name, String description, String ownerId) {
        Instant now = Instant.now();
        return new Workspace(
                workspaceId, name, description, ownerId,
                List.of(new WorkspaceMember(ownerId, WorkspaceRole.OWNER, now)),
                List.of(), List.of(),
                WorkspaceSettings.defaults(),
                WorkspaceStatus.ACTIVE, now, now
        );
    }

    /**
     * Add a member to the workspace.
     */
    public Workspace addMember(String userId, WorkspaceRole role) {
        List<WorkspaceMember> newMembers = new java.util.ArrayList<>(members);
        newMembers.add(new WorkspaceMember(userId, role, Instant.now()));
        return new Workspace(
                workspaceId, name, description, ownerId,
                List.copyOf(newMembers), sharedProjects, activeSessions,
                settings, status, createdAt, Instant.now()
        );
    }

    /**
     * Remove a member from the workspace.
     */
    public Workspace removeMember(String userId) {
        List<WorkspaceMember> newMembers = members.stream()
                .filter(m -> !m.userId().equals(userId))
                .toList();
        return new Workspace(
                workspaceId, name, description, ownerId,
                newMembers, sharedProjects, activeSessions,
                settings, status, createdAt, Instant.now()
        );
    }

    /**
     * Add a shared project.
     */
    public Workspace addProject(SharedProject project) {
        List<SharedProject> newProjects = new java.util.ArrayList<>(sharedProjects);
        newProjects.add(project);
        return new Workspace(
                workspaceId, name, description, ownerId,
                members, List.copyOf(newProjects), activeSessions,
                settings, status, createdAt, Instant.now()
        );
    }

    /**
     * Start a collaboration session.
     */
    public Workspace startSession(ActiveSession session) {
        List<ActiveSession> newSessions = new java.util.ArrayList<>(activeSessions);
        newSessions.add(session);
        return new Workspace(
                workspaceId, name, description, ownerId,
                members, sharedProjects, List.copyOf(newSessions),
                settings, status, createdAt, Instant.now()
        );
    }

    /**
     * End a collaboration session.
     */
    public Workspace endSession(String sessionId) {
        List<ActiveSession> newSessions = activeSessions.stream()
                .filter(s -> !s.sessionId().equals(sessionId))
                .toList();
        return new Workspace(
                workspaceId, name, description, ownerId,
                members, sharedProjects, newSessions,
                settings, status, createdAt, Instant.now()
        );
    }

    /**
     * Get member count.
     */
    public int memberCount() {
        return members.size();
    }

    /**
     * Check if user is a member.
     */
    public boolean hasMember(String userId) {
        return members.stream().anyMatch(m -> m.userId().equals(userId));
    }

    /**
     * Get user role.
     */
    public WorkspaceRole getUserRole(String userId) {
        return members.stream()
                .filter(m -> m.userId().equals(userId))
                .map(WorkspaceMember::role)
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record WorkspaceMember(
            String userId,
            WorkspaceRole role,
            Instant joinedAt
    ) {}

    public enum WorkspaceRole {
        OWNER, ADMIN, EDITOR, VIEWER
    }

    public enum WorkspaceStatus {
        ACTIVE, ARCHIVED, SUSPENDED
    }

    public record WorkspaceSettings(
            boolean allowGuestAccess,
            boolean enableRealTimeSync,
            int maxConcurrentSessions,
            ConflictResolutionStrategy conflictStrategy
    ) {
        public static WorkspaceSettings defaults() {
            return new WorkspaceSettings(false, true, 5, ConflictResolutionStrategy.LAST_WRITE_WINS);
        }
    }

    public enum ConflictResolutionStrategy {
        LAST_WRITE_WINS,
        FIRST_WRITE_WINS,
        MANUAL_MERGE,
        OPERATIONAL_TRANSFORM
    }

    public record SharedProject(
            String projectId,
            String projectName,
            String ownerId,
            List<String> editorIds,
            Instant sharedAt
    ) {}

    public record ActiveSession(
            String sessionId,
            String projectId,
            List<String> participantIds,
            Instant startedAt
    ) {}
}
