package com.example.platform.identity.app;

import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.domain.Role;
import com.example.platform.identity.domain.Role.RoleScope;
import com.example.platform.identity.infrastructure.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BuiltinDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(BuiltinDataInitializer.class);

    private final RoleRepository roleRepository;

    public BuiltinDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public void init() {
        try {
            initPermissions();
            initRoles();
            log.info("Built-in RBAC data initialized");
        } catch (Exception e) {
            log.warn("Built-in RBAC data initialization skipped: {}", e.getMessage());
        }
    }

    private void initPermissions() {
        createPermIfNotExists("ADMIN", "Administrator access", "Full workspace administration", "PLATFORM");
        createPermIfNotExists("WRITE", "Write access", "Create and edit workspace resources", "PLATFORM");
        createPermIfNotExists("MEMBER_MANAGE", "Manage members", "Invite and manage workspace members", "PLATFORM");
        createPermIfNotExists("render.submit", "Submit render job", "Submit a render job", "RENDER");
        createPermIfNotExists("render.cancel", "Cancel render job", "Cancel a running render job", "RENDER");
        createPermIfNotExists("render.use_gpu", "Use GPU rendering", "Use GPU for rendering", "RENDER");
        createPermIfNotExists("render.use_remote_worker", "Use remote worker", "Use remote worker for rendering", "RENDER");
        createPermIfNotExists("entitlement.grant", "Grant entitlement", "Grant entitlements to subjects", "ENTITLEMENT");
        createPermIfNotExists("entitlement.revoke", "Revoke entitlement", "Revoke entitlements from subjects", "ENTITLEMENT");
        createPermIfNotExists("billing.manage", "Manage billing", "Manage billing and subscriptions", "BILLING");
        createPermIfNotExists("prompt.template.manage", "Manage prompt templates", "Create and edit prompt templates", "PROMPT");
        createPermIfNotExists("extension.install", "Install extension", "Install and configure extensions", "EXTENSION");
        createPermIfNotExists("audit.view", "View audit logs", "View audit trail and compliance logs", "AUDIT");
        createPermIfNotExists("navigation.manage", "Manage navigation", "Manage workspace navigation items", "NAVIGATION");
        createPermIfNotExists("notification.manage", "Manage notifications", "Manage notification subscriptions and settings", "NOTIFICATION");
        createPermIfNotExists("social.publish", "Social publish", "Publish to social media platforms", "SOCIAL");
    }

    private void initRoles() {
        createRoleIfNotExists("ADMIN", "Administrator", "Full system access");
        createRoleIfNotExists("EDITOR", "Editor", "Can create and edit projects");
        createRoleIfNotExists("VIEWER", "Viewer", "Read-only access");
        linkRolePermissionIfNotExists("ADMIN", "ADMIN");
        linkRolePermissionIfNotExists("ADMIN", "MEMBER_MANAGE");
        linkRolePermissionIfNotExists("ADMIN", "WRITE");
        linkRolePermissionIfNotExists("EDITOR", "WRITE");
        linkRolePermissionIfNotExists("VIEWER", "render.submit");
    }

    private void linkRolePermissionIfNotExists(String roleKey, String permissionKey) {
        try {
            var role = roleRepository.findByKey(roleKey);
            var perm = roleRepository.findPermissionByKey(permissionKey);
            if (role.isEmpty() || perm.isEmpty()) {
                return;
            }
            boolean exists = roleRepository.findPermissionsByRoleId(role.get().id()).stream()
                    .anyMatch(p -> permissionKey.equals(p.permissionKey()));
            if (!exists) {
                roleRepository.saveRolePermission(new com.example.platform.identity.domain.RolePermission(
                        java.util.UUID.randomUUID().toString(),
                        role.get().id(),
                        perm.get().id(),
                        java.time.Instant.now()));
            }
        } catch (Exception e) {
            log.debug("Role permission {} -> {} not linked: {}", roleKey, permissionKey, e.getMessage());
        }
    }

    private void createPermIfNotExists(String key, String name, String description, String category) {
        try {
            if (roleRepository.findPermissionByKey(key).isEmpty()) {
                roleRepository.savePermission(new Permission(java.util.UUID.randomUUID().toString(), key, name, description, category, java.time.Instant.now()));
            }
        } catch (Exception e) {
            log.debug("Permission {} not created: {}", key, e.getMessage());
        }
    }

    private void createRoleIfNotExists(String key, String name, String description) {
        try {
            if (roleRepository.findByKey(key).isEmpty()) {
                roleRepository.save(new Role(java.util.UUID.randomUUID().toString(), key, name, description, Role.RoleScope.GLOBAL, java.time.Instant.now()));
            }
        } catch (Exception e) {
            log.debug("Role {} not created: {}", key, e.getMessage());
        }
    }
}
