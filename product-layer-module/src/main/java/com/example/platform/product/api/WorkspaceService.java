package com.example.platform.product.api;

import com.example.platform.product.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for workspace management.
 */
@Service
@Transactional
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    // In-memory storage (replace with repository in production)
    private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();
    private final Map<String, List<WorkspaceMember>> membersByWorkspace = new ConcurrentHashMap<>();

    /**
     * Create a new workspace.
     */
    public Workspace createWorkspace(String name, String description, String ownerId) {
        String id = Ids.newId("ws");
        Workspace workspace = Workspace.create(id, name, description, ownerId);
        workspaces.put(id, workspace);

        // Add owner as admin
        String memberId = Ids.newId("wm");
        WorkspaceMember owner = WorkspaceMember.create(memberId, id, ownerId, UserRole.ADMIN);
        membersByWorkspace.put(id, new java.util.ArrayList<>(List.of(owner)));

        log.info("Created workspace {} for owner {}", id, ownerId);
        return workspace;
    }

    /**
     * Get workspace by ID.
     */
    public Workspace getWorkspace(String workspaceId) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }
        return workspace;
    }

    /**
     * List workspaces for a user.
     */
    public List<Workspace> listWorkspacesForUser(String userId) {
        return workspaces.values().stream()
                .filter(w -> isMember(w.id(), userId))
                .toList();
    }

    /**
     * Update workspace details.
     */
    public Workspace updateWorkspace(String workspaceId, String name, String description) {
        Workspace workspace = getWorkspace(workspaceId);
        Workspace updated = workspace.withDetails(name, description);
        workspaces.put(workspaceId, updated);
        return updated;
    }

    /**
     * Archive workspace.
     */
    public Workspace archiveWorkspace(String workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);
        Workspace archived = workspace.archive();
        workspaces.put(workspaceId, archived);
        return archived;
    }

    /**
     * Add member to workspace.
     */
    public WorkspaceMember addMember(String workspaceId, String userId, UserRole role) {
        getWorkspace(workspaceId); // Validate workspace exists

        String memberId = Ids.newId("wm");
        WorkspaceMember member = WorkspaceMember.create(memberId, workspaceId, userId, role);

        membersByWorkspace.computeIfAbsent(workspaceId, k -> new java.util.ArrayList<>())
                .add(member);

        log.info("Added member {} to workspace {} with role {}", userId, workspaceId, role);
        return member;
    }

    /**
     * List workspace members.
     */
    public List<WorkspaceMember> listMembers(String workspaceId) {
        return membersByWorkspace.getOrDefault(workspaceId, List.of());
    }

    /**
     * Update member role.
     */
    public WorkspaceMember updateMemberRole(String workspaceId, String userId, UserRole newRole) {
        List<WorkspaceMember> members = membersByWorkspace.get(workspaceId);
        if (members == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }

        for (int i = 0; i < members.size(); i++) {
            WorkspaceMember member = members.get(i);
            if (member.userId().equals(userId)) {
                WorkspaceMember updated = member.withRole(newRole);
                members.set(i, updated);
                return updated;
            }
        }

        throw new IllegalArgumentException("Member not found: " + userId);
    }

    /**
     * Remove member from workspace.
     */
    public void removeMember(String workspaceId, String userId) {
        List<WorkspaceMember> members = membersByWorkspace.get(workspaceId);
        if (members != null) {
            members.removeIf(m -> m.userId().equals(userId));
        }
    }

    /**
     * Check if user is a member of workspace.
     */
    public boolean isMember(String workspaceId, String userId) {
        List<WorkspaceMember> members = membersByWorkspace.get(workspaceId);
        return members != null && members.stream().anyMatch(m -> m.userId().equals(userId));
    }

    /**
     * Get user role in workspace.
     */
    public UserRole getUserRole(String workspaceId, String userId) {
        List<WorkspaceMember> members = membersByWorkspace.get(workspaceId);
        if (members == null) {
            return null;
        }
        return members.stream()
                .filter(m -> m.userId().equals(userId))
                .map(WorkspaceMember::role)
                .findFirst()
                .orElse(null);
    }
}
