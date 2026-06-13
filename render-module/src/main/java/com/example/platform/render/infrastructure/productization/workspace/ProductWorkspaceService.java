package com.example.platform.render.infrastructure.productization.workspace;

import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing collaborative workspaces.
 */
@Service
public class ProductWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(ProductWorkspaceService.class);

    private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();

    /**
     * Create a new workspace.
     */
    public Workspace createWorkspace(String name, String description, String ownerId) {
        String workspaceId = Ids.newId("ws");
        Workspace workspace = Workspace.create(workspaceId, name, description, ownerId);
        workspaces.put(workspaceId, workspace);
        log.info("Created workspace: {} ({}) by {}", name, workspaceId, ownerId);
        return workspace;
    }

    /**
     * Get workspace by ID.
     */
    public Workspace getWorkspace(String workspaceId) {
        return workspaces.get(workspaceId);
    }

    /**
     * List workspaces for a user.
     */
    public List<Workspace> listWorkspacesForUser(String userId) {
        return workspaces.values().stream()
                .filter(ws -> ws.hasMember(userId))
                .toList();
    }

    /**
     * Add member to workspace.
     */
    public Workspace addMember(String workspaceId, String userId, Workspace.WorkspaceRole role) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }
        Workspace updated = workspace.addMember(userId, role);
        workspaces.put(workspaceId, updated);
        log.info("Added member {} to workspace {} with role {}", userId, workspaceId, role);
        return updated;
    }

    /**
     * Remove member from workspace.
     */
    public Workspace removeMember(String workspaceId, String userId) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }
        Workspace updated = workspace.removeMember(userId);
        workspaces.put(workspaceId, updated);
        log.info("Removed member {} from workspace {}", userId, workspaceId);
        return updated;
    }

    /**
     * Add shared project to workspace.
     */
    public Workspace addSharedProject(String workspaceId, String projectId, String projectName, String ownerId) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }

        Workspace.SharedProject project = new Workspace.SharedProject(
                projectId, projectName, ownerId, List.of(), Instant.now()
        );
        Workspace updated = workspace.addProject(project);
        workspaces.put(workspaceId, updated);
        log.info("Added shared project {} to workspace {}", projectId, workspaceId);
        return updated;
    }

    /**
     * Start a collaboration session.
     */
    public Workspace startCollaborationSession(String workspaceId, String projectId, List<String> participantIds) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }

        String sessionId = Ids.newId("session");
        Workspace.ActiveSession session = new Workspace.ActiveSession(
                sessionId, projectId, participantIds, Instant.now()
        );
        Workspace updated = workspace.startSession(session);
        workspaces.put(workspaceId, updated);
        log.info("Started collaboration session {} in workspace {}", sessionId, workspaceId);
        return updated;
    }

    /**
     * End a collaboration session.
     */
    public Workspace endCollaborationSession(String workspaceId, String sessionId) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }

        Workspace updated = workspace.endSession(sessionId);
        workspaces.put(workspaceId, updated);
        log.info("Ended collaboration session {} in workspace {}", sessionId, workspaceId);
        return updated;
    }

    /**
     * Update workspace settings.
     */
    public Workspace updateSettings(String workspaceId, Workspace.WorkspaceSettings settings) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }

        Workspace updated = new Workspace(
                workspace.workspaceId(), workspace.name(), workspace.description(),
                workspace.ownerId(), workspace.members(), workspace.sharedProjects(),
                workspace.activeSessions(), settings, workspace.status(),
                workspace.createdAt(), Instant.now()
        );
        workspaces.put(workspaceId, updated);
        return updated;
    }
}
