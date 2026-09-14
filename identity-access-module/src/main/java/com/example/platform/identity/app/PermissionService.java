package com.example.platform.identity.app;

import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.infrastructure.RoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final RoleRepository roleRepository;

    public PermissionService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Permission createPermission(String permissionKey, String name,
            String description, String resourceType) {
        String id = ("perm_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        Instant now = Instant.now();
        Permission permission = new Permission(id, permissionKey, name, description, resourceType, now);
        return roleRepository.savePermission(permission);
    }

    public List<Permission> listAllPermissions() {
        return roleRepository.findAllPermissions();
    }

    public boolean hasPermission(String userId, String tenantId, String workspaceId, String permissionKey) {
        List<com.example.platform.identity.domain.UserRoleAssignment> assignments =
                roleRepository.findUserRoleAssignmentsByWorkspaceId(workspaceId).stream()
                        .filter(a -> a.userId().equals(userId) && tenantId.equals(a.tenantId()))
                        .toList();
        for (com.example.platform.identity.domain.UserRoleAssignment assignment : assignments) {
            List<Permission> permissions = roleRepository.findPermissionsByRoleId(assignment.roleId());
            for (Permission p : permissions) {
                if (p.permissionKey().equals(permissionKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasProjectPermission(String userId,String tenantId,String projectId,String permissionKey) {
        return roleRepository.findProjectRoleAssignments(userId,tenantId,projectId).stream()
                .flatMap(a->roleRepository.findPermissionsByRoleId(a.roleId()).stream())
                .anyMatch(p->p.permissionKey().equals(permissionKey));
    }

    public boolean hasTenantPermission(String userId, String tenantId, String permissionKey) {
        return resolveTenantPermissions(userId, tenantId).contains(permissionKey);
    }

    public Set<String> resolveTenantPermissions(String userId, String tenantId) {
        return roleRepository.findTenantRoleAssignments(userId,tenantId).stream()
                .flatMap(a -> roleRepository.findPermissionsByRoleId(a.roleId()).stream())
                .map(Permission::permissionKey).collect(Collectors.toSet());
    }

    public Set<String> resolvePermissions(String userId, String workspaceId) {
        return roleRepository.findUserRoleAssignmentsByWorkspaceId(workspaceId).stream()
                .filter(a -> a.userId().equals(userId))
                .flatMap(a -> roleRepository.findPermissionsByRoleId(a.roleId()).stream())
                .map(Permission::permissionKey)
                .collect(Collectors.toSet());
    }
}
