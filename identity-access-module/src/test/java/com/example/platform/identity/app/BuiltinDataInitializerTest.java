package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.domain.Role;
import com.example.platform.identity.infrastructure.RoleRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

        assertEquals(16, distinctPermissionKeys().size());
    }

    @Test
    void initCreatesAllBuiltinRoles() {
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        assertEquals(3, distinctRoleKeys().size());
    }

    @Test
    void initSkipsExistingPermissions() {
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findPermissionByKey("render.submit"))
                .thenReturn(Optional.of(new Permission("perm_1", "render.submit", "Submit", null, "RENDER", Instant.now())));

        initializer.init();

        assertEquals(15, distinctPermissionKeys().size());
    }

    @Test
    void initSkipsExistingRoles() {
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByKey("ADMIN"))
                .thenReturn(Optional.of(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.GLOBAL, Instant.now())));

        initializer.init();

        assertEquals(2, distinctRoleKeys().size());
    }

    @Test
    void initCreatesExpectedRoleKeys() {
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        Set<String> roleKeys = distinctRoleKeys();
        assertTrue(roleKeys.containsAll(Set.of("ADMIN", "EDITOR", "VIEWER")));
    }

    @Test
    void initCreatesExpectedPermissionKeys() {
        when(roleRepository.findPermissionByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.savePermission(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.init();

        Set<String> permKeys = distinctPermissionKeys();
        assertTrue(permKeys.containsAll(Set.of(
                "ADMIN", "WRITE", "MEMBER_MANAGE",
                "render.submit", "render.cancel", "render.use_gpu", "render.use_remote_worker",
                "entitlement.grant", "entitlement.revoke", "billing.manage",
                "prompt.template.manage", "extension.install", "audit.view",
                "navigation.manage", "notification.manage", "social.publish")));
    }

    private Set<String> distinctPermissionKeys() {
        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(roleRepository, atLeastOnce()).savePermission(captor.capture());
        return captor.getAllValues().stream().map(Permission::permissionKey).collect(Collectors.toSet());
    }

    private Set<String> distinctRoleKeys() {
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(Role::roleKey).collect(Collectors.toSet());
    }
}
