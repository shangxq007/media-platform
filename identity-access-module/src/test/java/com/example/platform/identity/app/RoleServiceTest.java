package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.identity.domain.Role;
import com.example.platform.identity.infrastructure.RoleRepository;
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
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository);
    }

    @Test
    void createRoleSavesAndReturns() {
        Instant now = Instant.now();
        Role saved = new Role("rol_123", "CUSTOM", "Custom Role", "A custom role", Role.RoleScope.WORKSPACE, now);
        when(roleRepository.save(any(Role.class))).thenReturn(saved);

        Role result = roleService.createRole("CUSTOM", "Custom Role", "A custom role", Role.RoleScope.WORKSPACE);

        assertNotNull(result);
        assertEquals("CUSTOM", result.roleKey());
        assertEquals("Custom Role", result.name());

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertEquals("CUSTOM", captor.getValue().roleKey());
    }

    @Test
    void getRoleByKeyReturnsRole() {
        Role role = new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, Instant.now());
        when(roleRepository.findByKey("ADMIN")).thenReturn(Optional.of(role));

        Role result = roleService.getRoleByKey("ADMIN");

        assertEquals("ADMIN", result.roleKey());
    }

    @Test
    void getRoleByKeyThrowsForUnknown() {
        when(roleRepository.findByKey("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> roleService.getRoleByKey("UNKNOWN"));
    }

    @Test
    void listAllRolesReturnsAll() {
        List<Role> roles = List.of(
                new Role("rol_1", "OWNER", "Owner", null, Role.RoleScope.WORKSPACE, Instant.now()),
                new Role("rol_2", "VIEWER", "Viewer", null, Role.RoleScope.WORKSPACE, Instant.now()));
        when(roleRepository.findAll()).thenReturn(roles);

        List<Role> result = roleService.listAllRoles();

        assertEquals(2, result.size());
    }

    @Test
    void listRolesByScopeFilters() {
        List<Role> roles = List.of(
                new Role("rol_1", "OWNER", "Owner", null, Role.RoleScope.WORKSPACE, Instant.now()));
        when(roleRepository.findByScope(Role.RoleScope.WORKSPACE)).thenReturn(roles);

        List<Role> result = roleService.listRolesByScope(Role.RoleScope.WORKSPACE);

        assertEquals(1, result.size());
        assertEquals("OWNER", result.get(0).roleKey());
    }
}
