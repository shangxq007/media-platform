package com.example.platform.identity.api.workspace;

import com.example.platform.identity.api.dto.*;
import java.util.List;

/** Authorized owner reads. Workspace identity is distinct from tenant and Project identity. */
public interface WorkspaceQueries {
    WorkspaceResponse getWorkspace(String workspaceId);
    List<WorkspaceResponse> listWorkspacesForUser(String userId);
    List<WorkspaceMemberResponse> listMembers(String workspaceId);
}
