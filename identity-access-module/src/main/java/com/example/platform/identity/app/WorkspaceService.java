package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceGroupRepository workspaceGroupRepository;
    private final RoleRepository roleRepository;
    private final AuditPort auditPort;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceGroupRepository workspaceGroupRepository,
            RoleRepository roleRepository,
            AuditPort auditPort) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceGroupRepository = workspaceGroupRepository;
        this.roleRepository = roleRepository;
        this.auditPort = auditPort;
    }

    public WorkspaceResponse createWorkspace(String tenantId, CreateWorkspaceRequest request) {
        String id = Ids.newId("ws");
        Instant now = Instant.now();
        String planTier = request.planTier() != null ? request.planTier() : "FREE";
        Workspace workspace = new Workspace(id, tenantId, request.name(),
                request.description(), planTier, Workspace.WorkspaceStatus.ACTIVE, now, now);
        workspaceRepository.save(workspace);
        auditPort.record("SYSTEM", "WORKSPACE_CREATE", "CONFIG",
                "WORKSPACE", id, Map.of("tenantId", tenantId, "name", request.name()));
        return WorkspaceResponse.from(workspace);
    }

    public WorkspaceResponse getWorkspace(String workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        return WorkspaceResponse.from(workspace);
    }

    public WorkspaceMemberResponse addMember(String workspaceId, AddWorkspaceMemberRequest request) {
        String id = Ids.newId("wsm");
        Instant now = Instant.now();
        WorkspaceMember member = new WorkspaceMember(id, workspaceId, request.userId(),
                request.role(), WorkspaceMember.MemberStatus.ACTIVE, now, now);
        workspaceMemberRepository.save(member);
        auditPort.record("SYSTEM", "MEMBER_ADD", "PERMISSION",
                "WORKSPACE_MEMBER", id, Map.of("workspaceId", workspaceId, "userId", request.userId()));
        return WorkspaceMemberResponse.from(member);
    }

    public List<WorkspaceMemberResponse> listMembers(String workspaceId) {
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    public void assignRoleToMember(String workspaceId, String memberId, AssignRoleRequest request) {
        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        Role role = roleRepository.findByKey(request.roleKey())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.roleKey()));
        String assignmentId = Ids.newId("ura");
        UserRoleAssignment assignment = new UserRoleAssignment(
                assignmentId, null, workspaceId, member.userId(),
                role.id(), request.assignedBy(), Instant.now());
        roleRepository.saveUserRoleAssignment(assignment);
        auditPort.record("SYSTEM", "ROLE_ASSIGN", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", assignmentId,
                Map.of("workspaceId", workspaceId, "userId", member.userId(), "roleKey", request.roleKey()));
    }

    public void revokeRoleFromMember(String workspaceId, String memberId, String roleKey) {
        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        // Use workspace-scoped deletion to avoid removing the user's role in OTHER workspaces.
        roleRepository.deleteUserRoleAssignmentByWorkspace(member.userId(), roleKey, workspaceId);
        auditPort.record("SYSTEM", "ROLE_REVOKE", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", memberId,
                Map.of("workspaceId", workspaceId, "userId", member.userId(), "roleKey", roleKey));
    }

    public WorkspaceGroupResponse createGroup(String workspaceId, CreateWorkspaceGroupRequest request) {
        String id = Ids.newId("wsg");
        Instant now = Instant.now();
        WorkspaceGroup group = new WorkspaceGroup(id, workspaceId, request.name(),
                request.description(), now);
        workspaceGroupRepository.save(group);
        auditPort.record("SYSTEM", "GROUP_CREATE", "CONFIG",
                "WORKSPACE_GROUP", id, Map.of("workspaceId", workspaceId, "name", request.name()));
        return WorkspaceGroupResponse.from(group);
    }

    public List<WorkspaceGroupResponse> listGroups(String workspaceId) {
        return workspaceGroupRepository.findByWorkspaceId(workspaceId).stream()
                .map(WorkspaceGroupResponse::from)
                .toList();
    }
}
