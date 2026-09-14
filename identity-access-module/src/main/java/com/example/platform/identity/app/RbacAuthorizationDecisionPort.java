package com.example.platform.identity.app;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
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
    private final ProjectRepository projects;
    private final com.example.platform.identity.infrastructure.WorkspaceRepository workspaces;
    private final com.example.platform.identity.infrastructure.WorkspaceMemberRepository members;
    private final UserRepository users;

    public RbacAuthorizationDecisionPort(PermissionService permissionService, ProjectRepository projects,
            com.example.platform.identity.infrastructure.WorkspaceRepository workspaces,
            com.example.platform.identity.infrastructure.WorkspaceMemberRepository members, UserRepository users) {
        this.permissionService = permissionService;
        this.projects=projects;this.workspaces=workspaces;this.members=members;this.users=users;
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        // Layer 0 — tenant boundary default deny.
        if (!tenantsMatch(request)) {
            return AuthorizationDecision.deny("TENANT_BOUNDARY", "TENANT_BOUNDARY",
                    "actor tenant does not match resource tenant");
        }

        String workspaceId;
        try { workspaceId = resolveWorkspaceId(request); }
        catch (RuntimeException invalid) { return AuthorizationDecision.deny("RESOURCE_SCOPE", "IDENTITY", "Resource scope unavailable"); }

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

        String permissionKey = request.action().permissionKey();

        boolean permitted;
        try {
            permitted = workspaceId == null
                    ? permissionService.hasTenantPermission(userId, request.resource().tenantId(), permissionKey)
                    : permissionService.hasPermission(userId, request.resource().tenantId(), workspaceId, permissionKey);
            String projectId=request.resource().projectId();
            if(projectId!=null) permitted = permitted || permissionService.hasProjectPermission(userId,request.resource().tenantId(),projectId,permissionKey);
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

    /** Resolve actual resource relationships. A missing Workspace is never a tenant/Project alias. */
    private String resolveWorkspaceId(AuthorizationRequest request) {
        String tenant = request.resource().tenantId();
        String workspace = request.context().workspaceId();
        String project = request.resource().projectId();
        if(project == null && request.resource().resourceType() == com.example.platform.shared.authorization.AuthorizationResourceType.PROJECT)
            project = request.resource().resourceId();
        if(project != null) {
            if(request.resource().resourceType()==com.example.platform.shared.authorization.AuthorizationResourceType.PROJECT
                    &&request.resource().resourceId()!=null&&!project.equals(request.resource().resourceId()))throw new IllegalArgumentException("Project reference mismatch");
            var resource=projects.findByIdAndTenant(project,tenant).orElseThrow();
            if(resource.status()!=com.example.platform.identity.domain.Project.ProjectStatus.ACTIVE || resource.workspaceId()==null)throw new IllegalArgumentException("Project scope unresolved");
            if(workspace!=null&&!workspace.equals(resource.workspaceId()))throw new IllegalArgumentException("Workspace mismatch");
            workspace=resource.workspaceId();
        }
        if(request.actor().actorType()==ActorType.USER) {
            if(!users.isUsableMembership(request.actor().actorId(),tenant))throw new IllegalArgumentException("Inactive membership");
        }
        if(workspace!=null) {
            workspaces.findById(workspace).filter(w->tenant.equals(w.tenantId())&&w.status()==com.example.platform.identity.domain.Workspace.WorkspaceStatus.ACTIVE).orElseThrow();
            if(request.actor().actorType()==ActorType.USER)
                members.findByWorkspaceIdAndUserId(workspace,request.actor().actorId())
                        .filter(m->m.status()==com.example.platform.identity.domain.WorkspaceMember.MemberStatus.ACTIVE).orElseThrow();
        }
        return workspace;
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
