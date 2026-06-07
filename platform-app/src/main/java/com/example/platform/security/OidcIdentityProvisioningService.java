package com.example.platform.security;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Just-in-time user + RBAC assignment for OIDC logins (Authentik {@code sub} / groups).
 */
@Service
@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "true")
public class OidcIdentityProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OidcIdentityProvisioningService.class);

    private final OAuth2SecurityProperties oauth2Properties;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public OidcIdentityProvisioningService(
            OAuth2SecurityProperties oauth2Properties,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository) {
        this.oauth2Properties = oauth2Properties;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public void provisionFromJwt(Jwt jwt) {
        if (!oauth2Properties.enabled() || !oauth2Properties.jitProvisioningEnabled()) {
            return;
        }
        String userId = JwtClaimSupport.userId(jwt, oauth2Properties.userIdClaim());
        if (userId == null || userId.isBlank()) {
            userId = jwt.getSubject();
        }
        if (userId == null || userId.isBlank()) {
            return;
        }
        final String resolvedUserId = userId;

        String resolvedTenantId = JwtClaimSupport.tenantId(jwt, oauth2Properties.tenantClaim());
        if (resolvedTenantId == null || resolvedTenantId.isBlank()) {
            resolvedTenantId = oauth2Properties.defaultTenantId();
        }
        if (resolvedTenantId == null || resolvedTenantId.isBlank()) {
            log.warn("OIDC JIT skipped: missing tenant claim for subject={}", resolvedUserId);
            return;
        }
        final String tenantId = resolvedTenantId;

        if (tenantRepository.findById(tenantId).isEmpty()) {
            log.warn("OIDC JIT skipped: tenant {} does not exist for subject={}", tenantId, resolvedUserId);
            return;
        }

        String email = firstNonBlank(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"));
        String username = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                email != null ? email.split("@")[0] : userId);

        List<String> platformRoles = OidcRoleMapping.toPlatformRoleKeys(
                JwtClaimSupport.roles(jwt, oauth2Properties.rolesClaim()));
        User.UserRole userRole = OidcRoleMapping.toUserRole(platformRoles);

        userRepository.findById(resolvedUserId).ifPresentOrElse(
                existing -> updateExisting(existing, tenantId, email, username, userRole, platformRoles),
                () -> createNew(resolvedUserId, tenantId, email, username, userRole, platformRoles));
    }

    private void updateExisting(
            User existing,
            String tenantId,
            String email,
            String username,
            User.UserRole userRole,
            List<String> platformRoles) {
        if (!tenantId.equals(existing.tenantId())) {
            log.warn(
                    "OIDC subject {} already bound to tenant {}, JWT tenant {} ignored",
                    existing.id(),
                    existing.tenantId(),
                    tenantId);
            syncRoleAssignments(existing.id(), existing.tenantId(), platformRoles);
            return;
        }
        if (existing.role() != userRole) {
            userRepository.updateRole(existing.id(), userRole);
        }
        syncRoleAssignments(existing.id(), tenantId, platformRoles);
    }

    private void createNew(
            String userId,
            String tenantId,
            String email,
            String username,
            User.UserRole userRole,
            List<String> platformRoles) {
        String resolvedEmail = email != null && !email.isBlank() ? email : userId + "@oidc.local";
        String resolvedUsername = username != null && !username.isBlank() ? username : userId;
        User user = new User(
                userId,
                tenantId,
                resolvedUsername,
                resolvedEmail,
                userRole,
                User.UserStatus.ACTIVE,
                Instant.now());
        userRepository.save(user);
        syncRoleAssignments(userId, tenantId, platformRoles);
        log.info("OIDC JIT created user id={} tenant={} roles={}", userId, tenantId, platformRoles);
    }

    private void syncRoleAssignments(String userId, String workspaceId, List<String> platformRoleKeys) {
        for (String roleKey : List.of("ADMIN", "EDITOR", "VIEWER")) {
            try {
                roleRepository.deleteUserRoleAssignmentByWorkspace(userId, roleKey, workspaceId);
            } catch (Exception ex) {
                log.debug("No assignment to delete for user={} role={} workspace={}", userId, roleKey, workspaceId);
            }
        }
        for (String roleKey : platformRoleKeys) {
            roleRepository.findByKey(roleKey).ifPresent(role -> {
                UserRoleAssignment assignment = new UserRoleAssignment(
                        Ids.newId("ura"),
                        null,
                        workspaceId,
                        userId,
                        role.id(),
                        "oidc-jit",
                        Instant.now());
                roleRepository.saveUserRoleAssignment(assignment);
            });
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
