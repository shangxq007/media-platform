package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.domain.Role;
import com.example.platform.identity.infrastructure.RoleRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuiltinDataInitializerTest {

    @Mock
    private RoleRepository roleRepository;

    private BuiltinDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new BuiltinDataInitializer(roleRepository);
    }

    @Test
    void initCreatesAllBuiltinPermissions() {
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        verify(roleRepository, times(11)).savePermission(any(Permission.class));
    }

    @Test
    void initCreatesAllBuiltinRoles() {
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        verify(roleRepository, times(9)).save(any(Role.class));
    }

    @Test
    void initSkipsExistingPermissions() {
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(roleRepository.findPermissionByKey("render.submit"))
                .thenReturn(Optional.of(new Permission("perm_1", "render.submit", "Submit", null, "RENDER", Instant.now())));

        initializer.init();

        verify(roleRepository, times(10)).savePermission(any(Permission.class));
    }

    @Test
    void initSkipsExistingRoles() {
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(roleRepository.findByKey("OWNER"))
                .thenReturn(Optional.of(new Role("rol_1", "OWNER", "Owner", null, Role.RoleScope.WORKSPACE, Instant.now())));

        initializer.init();

        verify(roleRepository, times(8)).save(any(Role.class));
    }

    @Test
    void initCreatesExpectedRoleKeys() {
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(9)).save(captor.capture());
        var roleKeys = captor.getAllValues().stream().map(Role::roleKey).toList();
        assertTrue(roleKeys.contains("OWNER"));
        assertTrue(roleKeys.contains("ADMIN"));
        assertTrue(roleKeys.contains("BILLING_ADMIN"));
        assertTrue(roleKeys.contains("PROJECT_MANAGER"));
        assertTrue(roleKeys.contains("EDITOR"));
        assertTrue(roleKeys.contains("VIEWER"));
        assertTrue(roleKeys.contains("PROMPT_ADMIN"));
        assertTrue(roleKeys.contains("EXTENSION_ADMIN"));
        assertTrue(roleKeys.contains("RENDER_OPERATOR"));
    }

    @Test
    void initCreatesExpectedPermissionKeys() {
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(roleRepository, times(11)).savePermission(captor.capture());
        var permKeys = captor.getAllValues().stream().map(Permission::permissionKey).toList();
        assertTrue(permKeys.contains("render.submit"));
        assertTrue(permKeys.contains("render.cancel"));
        assertTrue(permKeys.contains("render.use_gpu"));
        assertTrue(permKeys.contains("render.use_remote_worker"));
        assertTrue(permKeys.contains("entitlement.grant"));
        assertTrue(permKeys.contains("entitlement.revoke"));
        assertTrue(permKeys.contains("billing.manage"));
        assertTrue(permKeys.contains("prompt.template.manage"));
        assertTrue(permKeys.contains("extension.install"));
        assertTrue(permKeys.contains("audit.view"));
        assertTrue(permKeys.contains("navigation.manage"));
    }
}
