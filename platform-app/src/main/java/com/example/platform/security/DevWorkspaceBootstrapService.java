package com.example.platform.security;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.identity.app.BuiltinDataInitializer;
import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.app.UserRepository;
import com.example.platform.identity.domain.Tenant;
import com.example.platform.identity.domain.User;
import com.example.platform.identity.domain.UserRoleAssignment;
import com.example.platform.identity.infrastructure.RoleRepository;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Seeds default tenant / legacy dev user ({@code user-1}) for local OIDC联调与 H2 开发库。
 */
@Service
public class DevWorkspaceBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(DevWorkspaceBootstrapService.class);

    public static final String LEGACY_DEV_USER_ID = "user-1";
    public static final String LEGACY_DEV_TENANT_ID = "tenant-1";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EntitlementPolicyService entitlementPolicyService;
    private final BuiltinDataInitializer builtinDataInitializer;

    public DevWorkspaceBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EntitlementPolicyService entitlementPolicyService,
            BuiltinDataInitializer builtinDataInitializer) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.entitlementPolicyService = entitlementPolicyService;
        this.builtinDataInitializer = builtinDataInitializer;
    }

    public void ensureDefaultWorkspace(String tenantId, String entitlementTier) {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = LEGACY_DEV_TENANT_ID;
        }
        final String resolvedTenantId = tenantId;
        builtinDataInitializer.init();
        tenantRepository.findById(resolvedTenantId).ifPresentOrElse(
                t -> log.debug("Dev workspace: tenant {} exists", t.id()),
                () -> {
                    tenantRepository.save(new Tenant(
                            resolvedTenantId,
                            "Default Workspace",
                            Tenant.TenantStatus.ACTIVE,
                            Instant.now()));
                    log.info("Dev workspace: created tenant {}", resolvedTenantId);
                });
        if (entitlementTier != null && !entitlementTier.isBlank()) {
            try {
                entitlementPolicyService.setTier(resolvedTenantId, entitlementTier);
            } catch (Exception ex) {
                log.debug("Dev workspace: could not set tier {} for {}: {}", entitlementTier, resolvedTenantId, ex.getMessage());
            }
        }
    }

    /**
     * Ensures legacy {@code user-1} exists with ADMIN RBAC — matches dev JWT / docs examples.
     */
    public void ensureLegacyDevUser(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = LEGACY_DEV_TENANT_ID;
        }
        final String resolvedTenantId = tenantId;
        userRepository.findById(LEGACY_DEV_USER_ID).ifPresentOrElse(
                u -> syncRoleAssignments(LEGACY_DEV_USER_ID, resolvedTenantId, List.of("ADMIN")),
                () -> {
                    User user = new User(
                            LEGACY_DEV_USER_ID,
                            resolvedTenantId,
                            "dev-user",
                            "dev-user@local",
                            User.UserRole.ADMIN,
                            User.UserStatus.ACTIVE,
                            Instant.now());
                    userRepository.save(user);
                    syncRoleAssignments(LEGACY_DEV_USER_ID, resolvedTenantId, List.of("ADMIN"));
                    log.info("Dev workspace: created legacy user {} in tenant {}", LEGACY_DEV_USER_ID, resolvedTenantId);
                });
    }

    private void syncRoleAssignments(String userId, String workspaceId, List<String> platformRoleKeys) {
        for (String roleKey : List.of("ADMIN", "EDITOR", "VIEWER")) {
            try {
                roleRepository.deleteUserRoleAssignment(userId, roleKey);
            } catch (Exception ignored) {
                // no-op
            }
        }
        for (String roleKey : platformRoleKeys) {
            roleRepository.findByKey(roleKey).ifPresent(role -> roleRepository.saveUserRoleAssignment(
                    new UserRoleAssignment(
                            Ids.newId("ura"),
                            null,
                            workspaceId,
                            userId,
                            role.id(),
                            "dev-bootstrap",
                            Instant.now())));
        }
    }
}
