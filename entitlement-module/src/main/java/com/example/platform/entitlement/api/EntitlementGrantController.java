package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/entitlements/grants")
public class EntitlementGrantController {
    private final EntitlementService entitlementService;

    public EntitlementGrantController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @PostMapping
    public EntitlementCommandResult createGrant(
            @RequestBody CreateGrantRequest request,
            @RequestHeader(value = "X-User-ID") String actor) {
        PrincipalRef principal = principal(request.subjectType(), request.subjectId(), request.workspaceId());
        return entitlementService.execute(new EntitlementGrantCommand(
                EntitlementCommandType.GRANT, principal, request.grantId(), request.bundleKey(),
                request.quotaProfileKey(), request.sourceType(), request.sourceRef(),
                request.idempotencyKey(), actor, request.reason(), request.traceId(),
                request.effectiveAt(), request.expiresAt(), 0));
    }

    @GetMapping
    public Map<String, Object> listGrants(
            @RequestParam String subjectType, @RequestParam String subjectId,
            @RequestParam(required = false) String workspaceId) {
        return Map.of("grants", entitlementService.listGrants(
                principal(subjectType, subjectId, workspaceId)));
    }

    @GetMapping("/{grantId}")
    public Map<String, Object> getGrant(
            @PathVariable String grantId, @RequestParam String subjectType,
            @RequestParam String subjectId, @RequestParam(required = false) String workspaceId) {
        return entitlementService.findGrant(principal(subjectType, subjectId, workspaceId), grantId)
                .map(grant -> Map.<String, Object>of("grant", grant))
                .orElse(Map.of("error", "Grant not found: " + grantId));
    }

    @PostMapping("/{grantId}/revoke")
    public EntitlementCommandResult revokeGrant(
            @PathVariable String grantId, @RequestBody TransitionRequest request,
            @RequestHeader(value = "X-User-ID") String actor) {
        return transition(grantId, request, actor,
                request.workspaceGrant() ? EntitlementCommandType.WORKSPACE_REVOKE
                        : EntitlementCommandType.REVOKE, null);
    }

    @PostMapping("/{grantId}/extend")
    public EntitlementCommandResult extendGrant(
            @PathVariable String grantId, @RequestBody TransitionRequest request,
            @RequestHeader(value = "X-User-ID") String actor) {
        return transition(grantId, request, actor,
                request.workspaceGrant() ? EntitlementCommandType.WORKSPACE_EXTEND
                        : EntitlementCommandType.EXTEND, request.newExpiresAt());
    }

    private EntitlementCommandResult transition(
            String grantId, TransitionRequest request, String actor,
            EntitlementCommandType type, Instant expiry) {
        return entitlementService.execute(new EntitlementGrantCommand(type,
                principal(request.subjectType(), request.subjectId(), request.workspaceId()),
                grantId, null, null, request.sourceType(), request.sourceRef(),
                request.idempotencyKey(), actor, request.reason(), request.traceId(),
                request.effectiveAt(), expiry, request.expectedVersion()));
    }

    private static PrincipalRef principal(String subjectType, String subjectId, String workspaceId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Tenant context is required");
        PrincipalType type = "TENANT".equalsIgnoreCase(subjectType)
                ? PrincipalType.ORGANIZATION : PrincipalType.valueOf(subjectType.toUpperCase());
        return new PrincipalRef(tenantId, type, subjectId, workspaceId, null);
    }

    public record CreateGrantRequest(
            String grantId, String workspaceId, String subjectType, String subjectId,
            String bundleKey, String quotaProfileKey, String sourceType, String sourceRef,
            String idempotencyKey, String reason, String traceId,
            Instant effectiveAt, Instant expiresAt) {}

    public record TransitionRequest(
            String workspaceId, String subjectType, String subjectId, boolean workspaceGrant,
            long expectedVersion, String sourceType, String sourceRef, String idempotencyKey,
            String reason, String traceId, Instant effectiveAt, Instant newExpiresAt) {}
}
