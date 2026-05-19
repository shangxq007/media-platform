package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.RoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private RoleRepository roleRepository;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(roleRepository);
    }

    @Test
    void hasPermissionReturnsTrueWhenUserHasPermission() {
        Role role = new Role("rol_1", "EDITOR", "Editor", null, Role.RoleScope.WORKSPACE, Instant.now());
        Permission perm = new Permission("perm_1", "render.submit", "Submit", null, "RENDER", Instant.now());
        UserRoleAssignment assignment = new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, Instant.now());

        when(roleRepository.findUserRoleAssignmentsByWorkspaceId("ws_1")).thenReturn(List.of(assignment));
        when(roleRepository.findPermissionsByRoleId("rol_1")).thenReturn(List.of(perm));

        assertTrue(permissionService.hasPermission("usr_1", "ws_1", "render.submit"));
    }

    @Test
    void hasPermissionReturnsFalseWhenUserLacksPermission() {
        UserRoleAssignment assignment = new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, Instant.now());

        when(roleRepository.findUserRoleAssignmentsByWorkspaceId("ws_1")).thenReturn(List.of(assignment));
        lenient().when(roleRepository.findPermissionsByRoleId("rol_1")).thenReturn(List.of());

        assertFalse(permissionService.hasPermission("usr_2", "ws_1", "render.submit"));
    }

    @Test
    void hasPermissionReturnsFalseForUnknownPermission() {
        Role role = new Role("rol_1", "VIEWER", "Viewer", null, Role.RoleScope.WORKSPACE, Instant.now());
        Permission perm = new Permission("perm_1", "render.submit", "Submit", null, "RENDER", Instant.now());
        UserRoleAssignment assignment = new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, Instant.now());

        when(roleRepository.findUserRoleAssignmentsByWorkspaceId("ws_1")).thenReturn(List.of(assignment));
        when(roleRepository.findPermissionsByRoleId("rol_1")).thenReturn(List.of(perm));

        assertFalse(permissionService.hasPermission("usr_1", "ws_1", "billing.manage"));
    }

    @Test
    void resolvePermissionsReturnsAllPermissionKeys() {
        Permission p1 = new Permission("perm_1", "render.submit", "Submit", null, "RENDER", Instant.now());
        Permission p2 = new Permission("perm_2", "render.cancel", "Cancel", null, "RENDER", Instant.now());
        UserRoleAssignment assignment = new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, Instant.now());

        when(roleRepository.findUserRoleAssignmentsByWorkspaceId("ws_1")).thenReturn(List.of(assignment));
        when(roleRepository.findPermissionsByRoleId("rol_1")).thenReturn(List.of(p1, p2));

        var perms = permissionService.resolvePermissions("usr_1", "ws_1");

        assertEquals(2, perms.size());
        assertTrue(perms.contains("render.submit"));
        assertTrue(perms.contains("render.cancel"));
    }

    @Test
    void resolvePermissionsReturnsEmptyForNoAssignments() {
        when(roleRepository.findUserRoleAssignmentsByWorkspaceId("ws_1")).thenReturn(List.of());

        var perms = permissionService.resolvePermissions("usr_1", "ws_1");

        assertTrue(perms.isEmpty());
    }
}
