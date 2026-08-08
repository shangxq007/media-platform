package com.example.platform.identity.app;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Canonical authorization decision port backed by the existing RBAC authority
 * ({@link PermissionService#hasPermission}).
 *
 * <p>Implements the bounded security closed loop (APPD-CHV1):</p>
 * <ol>
 *   <li><strong>Layer 0 — tenant default-deny:</strong> the actor's tenant must equal
 *       the resource's tenant. Cross-tenant is DENY regardless of role/entitlement/
 *       flag/capability (AR-AUTH-008). {@link ActorType#SYSTEM} is NOT a universal
 *       implicit allow — it is denied here unless an explicit system policy grants it.</li>
 *   <li><strong>Layer 1 — RBAC:</strong> the actor's permission key (from the typed
 *       {@link com.example.platform.shared.authorization.AuthorizationAction}) is
 *       evaluated via the existing {@code PermissionService}. This is the single RBAC
 *       primitive; no second RBAC authority is introduced.</li>
 * </ol>
 *
 * <p>This decision is INDEPENDENT of the separate Entitlement → FeatureFlag →
 * Capability → Quota composition — those never grant authorization here
 * (AR-AUTH-003/004/005).</p>
 */
@Service
public class RbacAuthorizationDecisionPort implements AuthorizationDecisionPort {

    private static final Logger log = LoggerFactory.getLogger(RbacAuthorizationDecisionPort.class);

    private final PermissionService permissionService;

    public RbacAuthorizationDecisionPort(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        // Layer 0 — tenant boundary default deny.
        if (!tenantsMatch(request)) {
            return AuthorizationDecision.deny("TENANT_BOUNDARY", "TENANT_BOUNDARY",
                    "actor tenant does not match resource tenant");
        }

        // SYSTEM is not a universal implicit allow; require explicit policy.
        if (request.actor().isSystem()) {
            if (!systemExplicitlyAuthorized(request)) {
                return AuthorizationDecision.deny("SYSTEM_NOT_AUTHORIZED", "RBAC",
                        "SYSTEM actor has no explicit authorization policy for this action");
            }
            return AuthorizationDecision.allow("SYSTEM_POLICY");
        }

        // Layer 1 — RBAC via the existing permission authority.
        String userId = request.actor().actorId();
        String workspaceId = resolveWorkspaceId(request);
        String permissionKey = request.action().permissionKey();

        boolean permitted;
        try {
            permitted = permissionService.hasPermission(userId, workspaceId, permissionKey);
        } catch (Exception e) {
            log.warn("RBAC evaluation failed for user={} permission={}: {}", userId, permissionKey, e.getMessage());
            // Fail closed.
            return AuthorizationDecision.deny("RBAC_ERROR", "RBAC", "authorization evaluation failed");
        }

        if (permitted) {
            return AuthorizationDecision.allow("RBAC");
        }
        return AuthorizationDecision.deny("RBAC_DENY", "RBAC",
                "actor lacks permission: " + permissionKey);
    }

    private boolean tenantsMatch(AuthorizationRequest request) {
        String actorTenant = request.actor().tenantId();
        String resourceTenant = request.resource().tenantId();
        if (actorTenant == null || resourceTenant == null) {
            return false;
        }
        return actorTenant.equals(resourceTenant);
    }

    /**
     * Resolve the workspace scope for the RBAC lookup. The existing
     * {@link PermissionService} is workspace-scoped; the platform convention (see
     * {@code MeController}, {@code NavigationController}) passes the tenant id as the
     * workspace id when no explicit workspace is in scope.
     */
    private String resolveWorkspaceId(AuthorizationRequest request) {
        String workspaceId = request.context().workspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            return workspaceId;
        }
        return request.resource().tenantId();
    }

    /**
     * Explicit system-action/resource policy. For this slice, SYSTEM actors are only
     * authorized for a bounded, registered set of internal actions. The absence of a
     * matching policy means DENY (never implicit allow).
     */
    private boolean systemExplicitlyAuthorized(AuthorizationRequest request) {
        String key = request.action().permissionKey();
        return key != null && key.startsWith("system.");
    }
}
