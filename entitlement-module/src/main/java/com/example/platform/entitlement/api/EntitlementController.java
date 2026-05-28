package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.AccessDecisionService;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.QuotaDecisionService;
import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.domain.ClientExportRoutingPolicy;
import com.example.platform.shared.entitlement.EntitlementPort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class EntitlementController {
    private final EntitlementService entitlementService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final AccessDecisionService accessDecisionService;
    private final QuotaDecisionService quotaDecisionService;

    public EntitlementController(EntitlementService entitlementService,
            EntitlementPolicyService entitlementPolicyService,
            AccessDecisionService accessDecisionService,
            QuotaDecisionService quotaDecisionService) {
        this.entitlementService = entitlementService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.accessDecisionService = accessDecisionService;
        this.quotaDecisionService = quotaDecisionService;
    }

    @GetMapping("/tenants/{tenantId}/entitlements")
    public Map<String, Object> getEntitlements(@PathVariable String tenantId) {
        EntitlementSnapshot snapshot = entitlementService.getSnapshot(tenantId);
        return Map.of(
                "tenantId", tenantId,
                "entitlements", snapshot
        );
    }

    @GetMapping("/entitlements/me/capabilities")
    public Map<String, Object> getMyCapabilities(
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        String effectiveUser = userId != null ? userId : "user-1";
        String tier = entitlementPolicyService.getTier(effectiveTenant);
        EntitlementPolicy policy = entitlementPolicyService.getPolicy(effectiveTenant);
        ExportCapabilityPolicy exportCaps = entitlementPolicyService.getExportCapabilities(effectiveTenant);
        ProviderAccessPolicy providerAccess = entitlementPolicyService.getProviderAccess(effectiveTenant);
        return Map.of(
                "tenantId", effectiveTenant,
                "userId", effectiveUser,
                "tier", tier,
                "entitlementPolicy", policy,
                "exportCapabilities", exportCaps,
                "providerAccess", providerAccess,
                "featureFlags", entitlementPolicyService.getFeatureFlags(tier)
        );
    }

    @PostMapping("/render/export/validate")
    public Map<String, Object> validateExport(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestBody ExportValidationRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        String effectiveUser = userId != null ? userId : "user-1";
        long duration = request.estimatedDurationSeconds() != null ? request.estimatedDurationSeconds() : 60L;

        AccessCheckRequest accessRequest = new AccessCheckRequest(
                effectiveTenant, null, effectiveUser,
                "TENANT", effectiveTenant,
                "export", "export", null,
                request.preset(), request.preset(), null,
                "api", null, null);

        AccessDecision decision = accessDecisionService.check(accessRequest);

        List<String> effectKeys = request.effectKeys() != null ? request.effectKeys() : List.of();
        if (effectKeys.isEmpty() && request.timelineJson() != null && !request.timelineJson().isBlank()) {
            effectKeys = com.example.platform.entitlement.domain.ClientExportRoutingPolicy
                    .parseEffectKeysFromTimelineJson(request.timelineJson());
        }

        EntitlementPort.ExportValidationResult legacyResult = entitlementPolicyService.validateExport(
                effectiveTenant, effectiveUser,
                request.preset(), request.outputFormat(), duration, effectKeys);

        Map<String, Object> result = new HashMap<>();
        result.put("allowed", decision.allowed());
        result.put("decision", decision.decision());
        result.put("reasonCode", decision.reasonCode());
        result.put("userFriendlyMessage", decision.userFriendlyMessage());
        result.put("currentTier", decision.currentTier());
        result.put("matchedPolicies", decision.matchedPolicies());
        result.put("matchedGrantId", decision.matchedGrantId());
        result.put("matchedOverrideId", decision.matchedOverrideId());
        result.put("matchedWorkspacePoolId", decision.matchedWorkspacePoolId());
        result.put("quotaRemaining", decision.quotaRemaining());
        result.put("recommendedAlternative", decision.recommendedAlternative());
        result.put("upgradeOptions", decision.upgradeOptions());
        result.put("expiresAt", decision.expiresAt());
        result.put("requiresReview", decision.requiresReview());
        result.put("legacyValidation", legacyResult);
        result.put("recommendedRenderLocation", legacyResult.recommendedRenderLocation());
        result.put("clientExportSupported", legacyResult.clientExportSupported());
        result.put("clientExportUnsupportedReasons", legacyResult.clientExportUnsupportedReasons());
        return result;
    }

    @PostMapping("/entitlements/policies/refresh")
    public Map<String, Object> refreshPolicies() {
        entitlementPolicyService.refreshPolicies();
        return Map.of("status", "refreshed");
    }

    @PutMapping("/tenants/{tenantId}/tier")
    public Map<String, Object> setTenantTier(@PathVariable String tenantId, @RequestBody TierUpdateRequest body) {
        entitlementPolicyService.setTier(tenantId, body.tier());
        return Map.of(
                "tenantId", tenantId,
                "tier", entitlementPolicyService.getTier(tenantId),
                "decisionSource", entitlementPolicyService.getDecisionSource(tenantId));
    }

    @PostMapping("/entitlements/access-check")
    public Map<String, Object> accessCheck(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestBody AccessCheckBody body) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        String effectiveUser = userId != null ? userId : body.userId();
        AccessCheckRequest request = new AccessCheckRequest(
                effectiveTenant,
                body.workspaceId(),
                effectiveUser,
                body.subjectType() != null ? body.subjectType() : "USER",
                body.subjectId() != null ? body.subjectId() : effectiveUser,
                body.action() != null ? body.action() : "read",
                body.resourceType(),
                body.resourceId(),
                body.featureKey(),
                body.requestedPreset(),
                body.providerKey(),
                "api",
                body.requestedQuota(),
                body.context());
        AccessDecision decision = accessDecisionService.check(request);
        return Map.of(
                "allowed", decision.allowed(),
                "decision", decision.decision(),
                "reasonCode", decision.reasonCode(),
                "message", decision.userFriendlyMessage(),
                "currentTier", decision.currentTier(),
                "matchedPolicies", decision.matchedPolicies());
    }

    @GetMapping("/entitlements/subjects/{subjectId}")
    public EntitlementSnapshot snapshot(@PathVariable String subjectId) {
        return entitlementService.getSnapshot(subjectId);
    }

    public record ExportValidationRequest(
            String preset,
            String outputFormat,
            Long estimatedDurationSeconds,
            List<String> effectKeys,
            String timelineJson) {}

    public record TierUpdateRequest(String tier) {}

    public record AccessCheckBody(
            String tenantId,
            String userId,
            String workspaceId,
            String subjectType,
            String subjectId,
            String action,
            String resourceType,
            String resourceId,
            String featureKey,
            String requestedPreset,
            String providerKey,
            Long requestedQuota,
            java.util.Map<String, Object> context) {}
}
