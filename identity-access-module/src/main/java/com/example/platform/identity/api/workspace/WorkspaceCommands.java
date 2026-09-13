package com.example.platform.identity.api.workspace;


/** Identity-owned mutations. Actor and tenant authority come from authenticated server context. */
public interface WorkspaceCommands {
    WorkspaceResponse createWorkspace(String tenantId, CreateWorkspaceRequest request);
    WorkspaceMemberResponse addMember(String workspaceId, AddWorkspaceMemberRequest request);
    void removeMember(String workspaceId, String userId);
}
