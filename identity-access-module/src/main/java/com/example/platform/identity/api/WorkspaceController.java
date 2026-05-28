package com.example.platform.identity.api;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementChangedEvent;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.entitlement.domain.EntitlementGrant;
import com.example.platform.entitlement.domain.EntitlementGrantStatus;
import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceEntitlementPoolService poolService;
    private final EntitlementService entitlementService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final AdminAuditPublisher auditPublisher;

    public WorkspaceController(WorkspaceService workspaceService,
            WorkspaceEntitlementPoolService poolService,
            EntitlementService entitlementService,
            EntitlementPolicyService entitlementPolicyService,
            AdminAuditPublisher auditPublisher) {
        this.workspaceService = workspaceService;
        this.poolService = poolService;
        this.entitlementService = entitlementService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.auditPublisher = auditPublisher;
    }

    @PostMapping
    public WorkspaceResponse createWorkspace(@RequestParam(required = false) String tenantId,
            @Valid @RequestBody CreateWorkspaceRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Resolve tenant: use caller-supplied tenantId only if admin;
        // otherwise derive from TenantContext (current authenticated tenant).
        String effectiveTenant = resolveTenantId(tenantId, httpRequest);
        return workspaceService.createWorkspace(effectiveTenant, request);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable String workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    public WorkspaceMemberResponse addMember(@PathVariable String workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request) {
        return workspaceService.addMember(workspaceId, request);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> listMembers(@PathVariable String workspaceId) {
        return workspaceService.listMembers(workspaceId);
    }

    @PostMapping("/{workspaceId}/members/{memberId}/roles")
    public void assignRole(@PathVariable String workspaceId,
            @PathVariable String memberId,
            @Valid @RequestBody AssignRoleRequest request) {
        workspaceService.assignRoleToMember(workspaceId, memberId, request);
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}/roles/{roleKey}")
    public void revokeRole(@PathVariable String workspaceId,
            @PathVariable String memberId,
            @PathVariable String roleKey) {
        workspaceService.revokeRoleFromMember(workspaceId, memberId, roleKey);
    }

    @PostMapping("/{workspaceId}/groups")
    public WorkspaceGroupResponse createGroup(@PathVariable String workspaceId,
            @Valid @RequestBody CreateWorkspaceGroupRequest request) {
        return workspaceService.createGroup(workspaceId, request);
    }

    @GetMapping("/{workspaceId}/groups")
    public List<WorkspaceGroupResponse> listGroups(@PathVariable String workspaceId) {
        return workspaceService.listGroups(workspaceId);
    }

    @PostMapping("/{workspaceId}/entitlements/grants")
    public WorkspaceMemberEntitlementGrant createWorkspaceGrant(
            @PathVariable String workspaceId,
            @RequestBody CreateWorkspaceGrantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        Instant now = Instant.now();
        String grantId = com.example.platform.shared.Ids.newId("ws_grant");
        WorkspaceMemberEntitlementGrant grant = new WorkspaceMemberEntitlementGrant(
                grantId, workspaceId, request.memberId(), request.featureKey(),
                request.quotaAmount(),
                request.startsAt() != null ? request.startsAt() : now,
                request.expiresAt(), "ACTIVE", effectiveActor, now, now);
        return poolService.allocateToMember(
                workspaceId, request.featureKey(), request.memberId(),
                request.quotaAmount(),
                request.startsAt() != null ? request.startsAt() : now,
                request.expiresAt(), effectiveActor);
    }

    @GetMapping("/{workspaceId}/entitlements/grants")
    public Map<String, Object> listWorkspaceGrants(@PathVariable String workspaceId) {
        return Map.of("grants", poolService.getMemberGrants(workspaceId));
    }

    @PostMapping("/{workspaceId}/entitlements/grants/{grantId}/revoke")
    public Map<String, Object> revokeWorkspaceGrant(
            @PathVariable String workspaceId,
            @PathVariable String grantId,
            @RequestBody(required = false) RevokeGrantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        String reason = request != null ? request.reason() : "revoked";
        EntitlementChangedEvent event = entitlementService.revokeEntitlement(grantId, effectiveActor, reason);
        return Map.of("status", "revoked", "event", event);
    }

    @PostMapping("/{workspaceId}/entitlements/preview")
    public EntitlementDecision previewEntitlements(
            @PathVariable String workspaceId,
            @RequestBody PreviewRequest request) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        return entitlementPolicyService.validateExportDecision(
                tenantId,
                request.userId(), request.preset(), request.outputFormat(),
                request.estimatedDurationSeconds() != null ? request.estimatedDurationSeconds() : 60L);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }

    /**
     * Resolve tenant ID for workspace creation.
     * If caller supplies a tenantId different from TenantContext, require admin role.
     * Otherwise, use TenantContext (current authenticated tenant).
     */
    private String resolveTenantId(String requestedTenantId, jakarta.servlet.http.HttpServletRequest request) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            // Cross-tenant workspace creation requires admin role
            if (!isAdmin(request)) {
                auditPublisher.publish(
                        extractActor(request), extractRoles(request),
                        "ADMIN_CREATE_WORKSPACE_CROSS_TENANT", "workspace", null, requestedTenantId, "DENIED");
                throw new SecurityException("Admin role required to create workspace in another tenant");
            }
            return requestedTenantId;
        }
        return contextTenant;
    }

    private boolean isAdmin(jakarta.servlet.http.HttpServletRequest request) {
        // OAuth2 / Spring Security path
        if (request.isUserInRole("ADMIN")) return true;
        // Legacy HMAC JWT path: check jwt.roles request attribute
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && "ADMIN".equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if ("ADMIN".equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }

    private static String extractActor(jakarta.servlet.http.HttpServletRequest request) {
        Object subject = request.getAttribute("jwt.subject");
        return subject != null && !subject.toString().isBlank() ? subject.toString() : "anonymous";
    }

    private static String extractRoles(jakarta.servlet.http.HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return String.join(",", roles.stream().map(Object::toString).toList());
        } else if (rolesAttr instanceof String rolesStr) {
            return rolesStr;
        }
        return "none";
    }

    public record CreateWorkspaceGrantRequest(
            String memberId, String featureKey, long quotaAmount,
            Instant startsAt, Instant expiresAt) {}

    public record RevokeGrantRequest(String reason) {}

    public record PreviewRequest(
            String userId, String preset, String outputFormat, Long estimatedDurationSeconds) {}
}
