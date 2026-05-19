package com.example.platform.identity.app;

import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.domain.Role;
import com.example.platform.identity.infrastructure.RoleRepository;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void init() {
        initPermissions();
        initRoles();
        log.info("Built-in RBAC data initialized");
    }

    private void initPermissions() {
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
    }

    private void initRoles() {
        createRoleIfNotExists("OWNER", "Owner", "Full control over workspace", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("ADMIN", "Admin", "Administrative access", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("BILLING_ADMIN", "Billing Admin", "Manage billing only", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("PROJECT_MANAGER", "Project Manager", "Manage projects", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("EDITOR", "Editor", "Edit content", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("VIEWER", "Viewer", "Read-only access", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("PROMPT_ADMIN", "Prompt Admin", "Manage prompts", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("EXTENSION_ADMIN", "Extension Admin", "Manage extensions", Role.RoleScope.WORKSPACE);
        createRoleIfNotExists("RENDER_OPERATOR", "Render Operator", "Operate render jobs", Role.RoleScope.WORKSPACE);
    }

    private void createPermIfNotExists(String key, String name, String desc, String resourceType) {
        roleRepository.findPermissionByKey(key).orElseGet(() -> {
            log.info("Creating built-in permission: {}", key);
            return roleRepository.savePermission(new Permission(
                    null, key, name, desc, resourceType, null));
        });
    }

    private void createRoleIfNotExists(String key, String name, String desc, Role.RoleScope scope) {
        roleRepository.findByKey(key).orElseGet(() -> {
            log.info("Creating built-in role: {}", key);
            return roleRepository.save(new Role(null, key, name, desc, scope, null));
        });
    }
}
