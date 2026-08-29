package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/entitlements/pool")
public class WorkspaceEntitlementPoolController {

    private final WorkspaceEntitlementPoolService poolService;

    public WorkspaceEntitlementPoolController(WorkspaceEntitlementPoolService poolService) {
        this.poolService = poolService;
    }

    @GetMapping
    public Map<String, Object> getPool(@PathVariable String workspaceId) {
        return Map.of("pools", poolService.getPool(workspaceId));
    }

    @PostMapping("/allocate")
    public WorkspaceMemberEntitlementGrant allocate(
            @PathVariable String workspaceId,
            @RequestBody AllocateRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Tenant context is required");
        return poolService.allocateToMember(
                tenantId, workspaceId, request.featureKey(), request.memberId(),
                request.quotaAmount(), request.startsAt(), request.expiresAt(), effectiveActor,
                request.sourceRef(), request.idempotencyKey(), request.reason(), request.traceId());
    }

    @PostMapping("/reclaim")
    public Map<String, Object> reclaim(
            @PathVariable String workspaceId,
            @RequestBody ReclaimRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        poolService.reclaimFromMember(
                workspaceId, request.memberId(), request.featureKey(),
                request.quotaAmount(), effectiveActor);
        return Map.of("status", "reclaimed");
    }

    public record AllocateRequest(
            String featureKey,
            String memberId,
            long quotaAmount,
            Instant startsAt,
            Instant expiresAt,
            String sourceRef,
            String idempotencyKey,
            String reason,
            String traceId) {}

    public record ReclaimRequest(
            String memberId,
            String featureKey,
            long quotaAmount) {}
}
