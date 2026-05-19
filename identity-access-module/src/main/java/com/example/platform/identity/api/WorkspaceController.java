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

    public WorkspaceController(WorkspaceService workspaceService,
            WorkspaceEntitlementPoolService poolService,
            EntitlementService entitlementService,
            EntitlementPolicyService entitlementPolicyService) {
        this.workspaceService = workspaceService;
        this.poolService = poolService;
        this.entitlementService = entitlementService;
        this.entitlementPolicyService = entitlementPolicyService;
    }

    @PostMapping
    public WorkspaceResponse createWorkspace(@RequestParam String tenantId,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.createWorkspace(tenantId, request);
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
            @RequestBody PreviewRequest request,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
        return entitlementPolicyService.validateExportDecision(
                tenantId != null ? tenantId : workspaceId,
                request.userId(), request.preset(), request.outputFormat(),
                request.estimatedDurationSeconds() != null ? request.estimatedDurationSeconds() : 60L);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }

    public record CreateWorkspaceGrantRequest(
            String memberId, String featureKey, long quotaAmount,
            Instant startsAt, Instant expiresAt) {}

    public record RevokeGrantRequest(String reason) {}

    public record PreviewRequest(
            String userId, String preset, String outputFormat, Long estimatedDurationSeconds) {}
}
