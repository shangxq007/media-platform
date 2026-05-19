package com.example.platform.identity.app;

import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.infrastructure.RoleRepository;
import com.example.platform.shared.Ids;
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
        String id = Ids.newId("perm");
        Instant now = Instant.now();
        Permission permission = new Permission(id, permissionKey, name, description, resourceType, now);
        return roleRepository.savePermission(permission);
    }

    public List<Permission> listAllPermissions() {
        return roleRepository.findAllPermissions();
    }

    public boolean hasPermission(String userId, String workspaceId, String permissionKey) {
        List<com.example.platform.identity.domain.UserRoleAssignment> assignments =
                roleRepository.findUserRoleAssignmentsByWorkspaceId(workspaceId).stream()
                        .filter(a -> a.userId().equals(userId))
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

    public Set<String> resolvePermissions(String userId, String workspaceId) {
        return roleRepository.findUserRoleAssignmentsByWorkspaceId(workspaceId).stream()
                .filter(a -> a.userId().equals(userId))
                .flatMap(a -> roleRepository.findPermissionsByRoleId(a.roleId()).stream())
                .map(Permission::permissionKey)
                .collect(Collectors.toSet());
    }
}
