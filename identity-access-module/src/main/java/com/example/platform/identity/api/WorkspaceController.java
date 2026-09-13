package com.example.platform.identity.api;

import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.identity.app.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceEntitlementPoolService poolService;
    private final EntitlementDecisionQuery entitlementDecisionQuery;

    public WorkspaceController(WorkspaceService workspaceService,
            WorkspaceEntitlementPoolService poolService,
            EntitlementDecisionQuery entitlementDecisionQuery) {
        this.workspaceService = workspaceService;
        this.poolService = poolService;
        this.entitlementDecisionQuery = entitlementDecisionQuery;
    }

    @PostMapping
    public WorkspaceResponse createWorkspace(@RequestParam(required = false) String tenantId,
            @Valid @RequestBody CreateWorkspaceRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // A request parameter cannot replace the authenticated tenant.
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

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String workspaceId, @PathVariable String userId) {
        workspaceService.removeMember(workspaceId, userId);
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

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{workspaceId}/entitlements/grants")
    public WorkspaceMemberEntitlementGrant createWorkspaceGrant(
            @PathVariable String workspaceId,
            @RequestBody CreateWorkspaceGrantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = workspaceService.requireManagementActor(workspaceId);
        String tenantId = requireTenantContext();
        workspaceService.requireWorkspaceUser(workspaceId, request.memberId(), true);
        Instant startsAt = request.startsAt() != null ? request.startsAt() : Instant.now();
        return poolService.allocateToMember(
                tenantId, workspaceId, request.featureKey(), request.memberId(),
                request.quotaAmount(), startsAt, request.expiresAt(), effectiveActor,
                request.sourceRef(), request.idempotencyKey(), request.reason(), request.traceId());
    }

    @GetMapping("/{workspaceId}/entitlements/grants")
    public Map<String, Object> listWorkspaceGrants(@PathVariable String workspaceId) {
        workspaceService.requireManagementActor(workspaceId);
        return Map.of("grants", poolService.getMemberGrants(requireTenantContext(), workspaceId));
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{workspaceId}/entitlements/grants/{grantId}/revoke")
    public Map<String, Object> revokeWorkspaceGrant(
            @PathVariable String workspaceId,
            @PathVariable String grantId,
            @RequestBody RevokeGrantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = workspaceService.requireManagementActor(workspaceId);
        workspaceService.requireWorkspaceUser(workspaceId, request.memberId(), false);
        EntitlementCommandResult result = poolService.revokeFromMember(
                requireTenantContext(), workspaceId, grantId, request.memberId(),
                request.expectedVersion(), effectiveActor, request.sourceRef(),
                request.idempotencyKey(), request.reason(), request.traceId());
        return Map.of("status", "revoked", "event", result);
    }

    @PostMapping("/{workspaceId}/entitlements/preview")
    public EntitlementDecision previewEntitlements(
            @PathVariable String workspaceId,
            @RequestBody PreviewRequest request) {
        workspaceService.requireMemberPreview(workspaceId, request.userId());
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        return entitlementDecisionQuery.evaluate(new AccessCheckRequest(
                tenantId, workspaceId, request.userId(), "USER", request.userId(),
                "export", "workspace", workspaceId,
                "export.preset." + request.preset(), request.preset(), null,
                "workspace-preview", null, Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }

    /**
     * Resolve tenant ID for workspace creation.
     * A different requested tenant is rejected, including for a JWT ADMIN claim.
     */
    private String resolveTenantId(String requestedTenantId, jakarta.servlet.http.HttpServletRequest request) {
        String contextTenant = requireTenantContext();
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            throw new SecurityException("Workspace creation must use the authenticated tenant");
        }
        return contextTenant;
    }

    private static String requireTenantContext() {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        return tenantId;
    }

    public record CreateWorkspaceGrantRequest(
            String memberId, String featureKey, long quotaAmount,
            Instant startsAt, Instant expiresAt, String sourceRef,
            String idempotencyKey, String reason, String traceId) {}

    public record RevokeGrantRequest(
            String memberId, long expectedVersion, String sourceRef,
            String idempotencyKey, String reason, String traceId) {}

    public record PreviewRequest(
            String userId, String preset, String outputFormat, Long estimatedDurationSeconds) {}
}
