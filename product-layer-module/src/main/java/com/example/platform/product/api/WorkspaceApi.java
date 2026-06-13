package com.example.platform.product.api;

import com.example.platform.product.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for workspace management.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceApi {

    private final WorkspaceService workspaceService;

    public WorkspaceApi(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * Create a new workspace.
     */
    @PostMapping
    public Workspace createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        return workspaceService.createWorkspace(request.name(), request.description(), request.ownerId());
    }

    /**
     * Get workspace by ID.
     */
    @GetMapping("/{workspaceId}")
    public Workspace getWorkspace(@PathVariable String workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }

    /**
     * List workspaces for a user.
     */
    @GetMapping
    public List<Workspace> listWorkspaces(@RequestParam String userId) {
        return workspaceService.listWorkspacesForUser(userId);
    }

    /**
     * Update workspace details.
     */
    @PutMapping("/{workspaceId}")
    public Workspace updateWorkspace(@PathVariable String workspaceId,
                                      @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.updateWorkspace(workspaceId, request.name(), request.description());
    }

    /**
     * Archive workspace.
     */
    @PostMapping("/{workspaceId}/archive")
    public Workspace archiveWorkspace(@PathVariable String workspaceId) {
        return workspaceService.archiveWorkspace(workspaceId);
    }

    /**
     * Add member to workspace.
     */
    @PostMapping("/{workspaceId}/members")
    public WorkspaceMember addMember(@PathVariable String workspaceId,
                                      @RequestBody AddMemberRequest request) {
        return workspaceService.addMember(workspaceId, request.userId(), request.role());
    }

    /**
     * List workspace members.
     */
    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMember> listMembers(@PathVariable String workspaceId) {
        return workspaceService.listMembers(workspaceId);
    }

    /**
     * Update member role.
     */
    @PutMapping("/{workspaceId}/members/{userId}")
    public WorkspaceMember updateMemberRole(@PathVariable String workspaceId,
                                             @PathVariable String userId,
                                             @RequestBody UpdateMemberRoleRequest request) {
        return workspaceService.updateMemberRole(workspaceId, userId, request.role());
    }

    /**
     * Remove member from workspace.
     */
    @DeleteMapping("/{workspaceId}/members/{userId}")
    public void removeMember(@PathVariable String workspaceId, @PathVariable String userId) {
        workspaceService.removeMember(workspaceId, userId);
    }

    // Request records
    public record CreateWorkspaceRequest(String name, String description, String ownerId) {}
    public record UpdateWorkspaceRequest(String name, String description) {}
    public record AddMemberRequest(String userId, UserRole role) {}
    public record UpdateMemberRoleRequest(UserRole role) {}
}
