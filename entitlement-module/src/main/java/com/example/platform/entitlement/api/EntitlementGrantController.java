package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.EntitlementChangedEvent;
import com.example.platform.entitlement.domain.EntitlementGrant;
import com.example.platform.entitlement.domain.EntitlementGrantStatus;
import com.example.platform.entitlement.infrastructure.EntitlementGrantRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/entitlements/grants")
public class EntitlementGrantController {

    private final EntitlementService entitlementService;

    public EntitlementGrantController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @PostMapping
    public EntitlementChangedEvent createGrant(
            @RequestBody CreateGrantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        EntitlementGrant grant = new EntitlementGrant(
                null, request.tenantId(), request.workspaceId(),
                request.subjectType(), request.subjectId(),
                request.featureKey(), request.bundleKey(),
                request.quotaProfileKey(), "admin", request.reason(),
                effectiveActor,
                request.startsAt() != null ? request.startsAt() : Instant.now(),
                request.expiresAt(), null, null, null,
                EntitlementGrantStatus.ACTIVE, null, null);
        return entitlementService.grantEntitlement(grant);
    }

    @GetMapping
    public Map<String, Object> listGrants(
            @RequestParam String subjectId) {
        return Map.of("grants", entitlementService.listGrants(subjectId));
    }

    @GetMapping("/{grantId}")
    public Map<String, Object> getGrant(@PathVariable String grantId) {
        return entitlementService.getGrant(grantId)
                .map(g -> Map.<String, Object>of("grant", g))
                .orElse(Map.of("error", "Grant not found: " + grantId));
    }

    @PostMapping("/{grantId}/revoke")
    public EntitlementChangedEvent revokeGrant(
            @PathVariable String grantId,
            @RequestBody RevokeRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return entitlementService.revokeEntitlement(grantId, effectiveActor, request.reason());
    }

    @PostMapping("/{grantId}/extend")
    public EntitlementChangedEvent extendGrant(
            @PathVariable String grantId,
            @RequestBody ExtendRequest request) {
        return entitlementService.extendGrant(grantId, request.newExpiresAt());
    }

    public record CreateGrantRequest(
            String tenantId, String workspaceId,
            String subjectType, String subjectId,
            String featureKey, String bundleKey,
            String quotaProfileKey, String reason,
            Instant startsAt, Instant expiresAt) {}

    public record RevokeRequest(String reason) {}

    public record ExtendRequest(Instant newExpiresAt) {}
}
