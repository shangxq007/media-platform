package com.example.platform.security;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.identity.app.BuiltinDataInitializer;
import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.app.UserRepository;
import com.example.platform.identity.domain.Tenant;
import com.example.platform.identity.domain.User;
import com.example.platform.identity.domain.UserRoleAssignment;
import com.example.platform.identity.infrastructure.RoleRepository;
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
    private final com.example.platform.identity.app.AccountMembershipService accounts;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EntitlementPolicyService entitlementPolicyService;
    private final BuiltinDataInitializer builtinDataInitializer;

    public DevWorkspaceBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EntitlementPolicyService entitlementPolicyService,
            BuiltinDataInitializer builtinDataInitializer, com.example.platform.identity.app.AccountMembershipService accounts) {
        this.tenantRepository = tenantRepository;
        this.accounts=accounts;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.entitlementPolicyService = entitlementPolicyService;
        this.builtinDataInitializer = builtinDataInitializer;
    }

    public void ensureDefaultTenant(String tenantId, String entitlementTier) {
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
        String tenant=tenantId==null||tenantId.isBlank()?LEGACY_DEV_TENANT_ID:tenantId;
        var operator=com.example.platform.shared.authorization.CanonicalActor.system("system:identity-provisioning",tenant);
        String account=accounts.provisionVerifiedAccount(operator,"urn:media-platform:local-hmac",LEGACY_DEV_USER_ID);
        var old=userRepository.findById(LEGACY_DEV_USER_ID);
        String member;
        if(old.isPresent()&&tenant.equals(old.get().tenantId())) {
            member=accounts.linkMembership(operator,account,tenant,LEGACY_DEV_USER_ID).membershipId();
        } else {
            member=accounts.createMembership(operator,account,tenant,"dev-user","dev-user@local","ADMIN").membershipId();
        }
        roleRepository.findByKey("ADMIN").ifPresent(role->{
            if(roleRepository.findTenantRoleAssignments(member,tenant).stream().noneMatch(a->a.roleId().equals(role.id())))
                roleRepository.saveUserRoleAssignment(new UserRoleAssignment("ura_"+java.util.UUID.randomUUID(),tenant,null,member,role.id(),"dev-bootstrap",Instant.now()));
        });
    }
}
