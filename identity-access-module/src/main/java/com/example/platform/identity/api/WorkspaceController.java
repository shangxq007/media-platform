package com.example.platform.identity.api;

import com.example.platform.entitlement.app.EntitlementDecisionService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
    private final EntitlementDecisionService entitlementDecisionService;
    private final AdminAuditPublisher auditPublisher;

    public WorkspaceController(WorkspaceService workspaceService,
            WorkspaceEntitlementPoolService poolService,
            EntitlementDecisionService entitlementDecisionService,
            AdminAuditPublisher auditPublisher) {
        this.workspaceService = workspaceService;
        this.poolService = poolService;
        this.entitlementDecisionService = entitlementDecisionService;
        this.auditPublisher = auditPublisher;
    }

    @PostMapping
    public WorkspaceResponse createWorkspace(@RequestParam(required = false) String tenantId,
            @Valid @RequestBody CreateWorkspaceRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        throw FailClosedAuthorization.unavailable("workspace creation");
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable String workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    public WorkspaceMemberResponse addMember(@PathVariable String workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request) {
        throw FailClosedAuthorization.unavailable("workspace member addition");
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> listMembers(@PathVariable String workspaceId) {
        return workspaceService.listMembers(workspaceId);
    }

    @PostMapping("/{workspaceId}/members/{memberId}/roles")
    public void assignRole(@PathVariable String workspaceId,
            @PathVariable String memberId,
            @Valid @RequestBody AssignRoleRequest request) {
        throw FailClosedAuthorization.unavailable("workspace role assignment");
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}/roles/{roleKey}")
    public void revokeRole(@PathVariable String workspaceId,
            @PathVariable String memberId,
            @PathVariable String roleKey) {
        throw FailClosedAuthorization.unavailable("workspace role revocation");
    }

    @PostMapping("/{workspaceId}/groups")
    public WorkspaceGroupResponse createGroup(@PathVariable String workspaceId,
            @Valid @RequestBody CreateWorkspaceGroupRequest request) {
        throw FailClosedAuthorization.unavailable("workspace group creation");
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
        throw FailClosedAuthorization.unavailable("workspace entitlement grant creation");
    }

    @GetMapping("/{workspaceId}/entitlements/grants")
    public Map<String, Object> listWorkspaceGrants(@PathVariable String workspaceId) {
        return Map.of("grants", poolService.getMemberGrants(workspaceId));
    }

    @PostMapping("/{workspaceId}/entitlements/grants/{grantId}/revoke")
    public Map<String, Object> revokeWorkspaceGrant(
            @PathVariable String workspaceId,
            @PathVariable String grantId,
            @RequestBody RevokeGrantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        throw FailClosedAuthorization.unavailable("workspace entitlement grant revocation");
    }

    @PostMapping("/{workspaceId}/entitlements/preview")
    public EntitlementDecision previewEntitlements(
            @PathVariable String workspaceId,
            @RequestBody PreviewRequest request) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        return entitlementDecisionService.evaluate(new AccessCheckRequest(
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
