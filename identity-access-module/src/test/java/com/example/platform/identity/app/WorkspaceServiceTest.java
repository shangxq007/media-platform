package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.*;
import com.example.platform.shared.audit.AuditPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceGroupRepository workspaceGroupRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditPort auditPort;

    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(workspaceRepository, workspaceMemberRepository,
                workspaceGroupRepository, roleRepository, auditPort);
    }

    @Test
    void createWorkspaceReturnsResponse() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("My WS", "Desc", "PRO");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse response = workspaceService.createWorkspace("ten_1", request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertTrue(response.id().startsWith("ws_"));
        assertEquals("My WS", response.name());
        assertEquals("PRO", response.planTier());
        verify(auditPort).record(eq("SYSTEM"), eq("WORKSPACE_CREATE"), eq("CONFIG"),
                eq("WORKSPACE"), anyString(), any());
    }

    @Test
    void createWorkspaceDefaultsPlanTierToFree() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("My WS", null, null);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse response = workspaceService.createWorkspace("ten_1", request);

        assertEquals("FREE", response.planTier());
    }

    @Test
    void getWorkspaceReturnsResponse() {
        Workspace ws = new Workspace("ws_123", "ten_1", "My WS", null, "FREE",
                Workspace.WorkspaceStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceRepository.findById("ws_123")).thenReturn(Optional.of(ws));

        WorkspaceResponse response = workspaceService.getWorkspace("ws_123");

        assertEquals("ws_123", response.id());
        assertEquals("My WS", response.name());
    }

    @Test
    void getWorkspaceThrowsForUnknown() {
        when(workspaceRepository.findById("ws_unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> workspaceService.getWorkspace("ws_unknown"));
    }

    @Test
    void addMemberReturnsResponse() {
        AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("usr_1", "EDITOR");
        when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceMemberResponse response = workspaceService.addMember("ws_1", request);

        assertNotNull(response);
        assertEquals("usr_1", response.userId());
        assertEquals("EDITOR", response.role());
        verify(auditPort).record(eq("SYSTEM"), eq("MEMBER_ADD"), eq("PERMISSION"),
                eq("WORKSPACE_MEMBER"), anyString(), any());
    }

    @Test
    void listMembersReturnsResponses() {
        WorkspaceMember m1 = new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        WorkspaceMember m2 = new WorkspaceMember("wsm_2", "ws_1", "usr_2", "VIEWER",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceMemberRepository.findByWorkspaceId("ws_1")).thenReturn(List.of(m1, m2));

        List<WorkspaceMemberResponse> members = workspaceService.listMembers("ws_1");

        assertEquals(2, members.size());
    }

    @Test
    void assignRoleToMemberSavesAssignment() {
        WorkspaceMember member = new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        Role role = new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, Instant.now());
        when(workspaceMemberRepository.findById("wsm_1")).thenReturn(Optional.of(member));
        when(roleRepository.findByKey("ADMIN")).thenReturn(Optional.of(role));
        when(roleRepository.saveUserRoleAssignment(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignRoleRequest request = new AssignRoleRequest("ADMIN", "usr_admin");
        workspaceService.assignRoleToMember("ws_1", "wsm_1", request);

        ArgumentCaptor<UserRoleAssignment> captor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(roleRepository).saveUserRoleAssignment(captor.capture());
        assertEquals("usr_1", captor.getValue().userId());
        assertEquals("rol_1", captor.getValue().roleId());
    }

    @Test
    void assignRoleThrowsForUnknownMember() {
        when(workspaceMemberRepository.findById("wsm_unknown")).thenReturn(Optional.empty());

        AssignRoleRequest request = new AssignRoleRequest("ADMIN", null);
        assertThrows(IllegalArgumentException.class,
                () -> workspaceService.assignRoleToMember("ws_1", "wsm_unknown", request));
    }

    @Test
    void revokeRoleDeletesAssignmentInWorkspaceScope() {
        WorkspaceMember member = new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceMemberRepository.findById("wsm_1")).thenReturn(Optional.of(member));

        workspaceService.revokeRoleFromMember("ws_1", "wsm_1", "ADMIN");

        // Must use workspace-scoped deletion, NOT the deprecated global deletion
        verify(roleRepository).deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");
        verify(roleRepository, never()).deleteUserRoleAssignment(anyString(), anyString());
        verify(auditPort).record(eq("SYSTEM"), eq("ROLE_REVOKE"), eq("PERMISSION"),
                eq("USER_ROLE_ASSIGNMENT"), eq("wsm_1"), any());
    }

    @Test
    void revokeRoleDoesNotDeleteOtherWorkspaceRoles() {
        // Setup: user usr_1 is in workspace ws_1 (ADMIN) and ws_2 (VIEWER)
        WorkspaceMember memberWs1 = new WorkspaceMember("wsm_1", "ws_1", "usr_1", "ADMIN",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceMemberRepository.findById("wsm_1")).thenReturn(Optional.of(memberWs1));

        workspaceService.revokeRoleFromMember("ws_1", "wsm_1", "ADMIN");

        // Verify only workspace-scoped deletion is called
        verify(roleRepository).deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");
        // Verify the deprecated global deletion is NOT called
        verify(roleRepository, never()).deleteUserRoleAssignment(eq("usr_1"), anyString());
    }

    @Test
    void revokeRoleThrowsWhenMemberNotFound() {
        when(workspaceMemberRepository.findById("wsm_unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> workspaceService.revokeRoleFromMember("ws_1", "wsm_unknown", "ADMIN"));
        verify(roleRepository, never()).deleteUserRoleAssignmentByWorkspace(anyString(), anyString(), anyString());
    }

    @Test
    void revokeRoleOnlyAffectsSpecifiedRole() {
        // Setup: user has both ADMIN and EDITOR in workspace ws_1
        WorkspaceMember member = new WorkspaceMember("wsm_1", "ws_1", "usr_1", "ADMIN",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceMemberRepository.findById("wsm_1")).thenReturn(Optional.of(member));

        workspaceService.revokeRoleFromMember("ws_1", "wsm_1", "ADMIN");

        // Only ADMIN should be deleted, EDITOR should remain
        verify(roleRepository).deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");
        verify(roleRepository, never()).deleteUserRoleAssignmentByWorkspace(
                eq("usr_1"), eq("EDITOR"), anyString());
    }

    @Test
    void revokeRoleDoesNotAffectOtherUsers() {
        // Setup: user usr_1 has ADMIN in ws_1
        WorkspaceMember member = new WorkspaceMember("wsm_1", "ws_1", "usr_1", "ADMIN",
                WorkspaceMember.MemberStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceMemberRepository.findById("wsm_1")).thenReturn(Optional.of(member));

        workspaceService.revokeRoleFromMember("ws_1", "wsm_1", "ADMIN");

        // Only usr_1's ADMIN in ws_1 should be deleted
        verify(roleRepository).deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");
        // No deletion for other users
        verify(roleRepository, never()).deleteUserRoleAssignmentByWorkspace(
                eq("usr_2"), anyString(), anyString());
    }

    @Test
    void createGroupReturnsResponse() {
        CreateWorkspaceGroupRequest request = new CreateWorkspaceGroupRequest("Dev Team", "Developers");
        when(workspaceGroupRepository.save(any(WorkspaceGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceGroupResponse response = workspaceService.createGroup("ws_1", request);

        assertNotNull(response);
        assertEquals("Dev Team", response.name());
        verify(auditPort).record(eq("SYSTEM"), eq("GROUP_CREATE"), eq("CONFIG"),
                eq("WORKSPACE_GROUP"), anyString(), any());
    }

    @Test
    void listGroupsReturnsResponses() {
        WorkspaceGroup g1 = new WorkspaceGroup("wsg_1", "ws_1", "Devs", null, Instant.now());
        WorkspaceGroup g2 = new WorkspaceGroup("wsg_2", "ws_1", "Ops", null, Instant.now());
        when(workspaceGroupRepository.findByWorkspaceId("ws_1")).thenReturn(List.of(g1, g2));

        List<WorkspaceGroupResponse> groups = workspaceService.listGroups("ws_1");

        assertEquals(2, groups.size());
    }
}
