package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.AccessDecisionService;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.QuotaDecisionService;
import com.example.platform.entitlement.domain.*;
import com.example.platform.shared.entitlement.EntitlementPort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
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
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestBody ExportValidationRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        String effectiveUser = userId != null ? userId : "user-1";
        long duration = request.estimatedDurationSeconds() != null ? request.estimatedDurationSeconds() : 60L;

        AccessCheckRequest accessRequest = new AccessCheckRequest(
                effectiveTenant, null, effectiveUser,
                "TENANT", effectiveTenant,
                "export", "export", null,
                request.preset(), request.preset(), null,
                "api", null, null);

        AccessDecision decision = accessDecisionService.check(accessRequest);

        EntitlementPort.ExportValidationResult legacyResult = entitlementPolicyService.validateExport(
                effectiveTenant, effectiveUser,
                request.preset(), request.outputFormat(), duration);

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
        return result;
    }

    @PostMapping("/entitlements/policies/refresh")
    public Map<String, Object> refreshPolicies() {
        entitlementPolicyService.refreshPolicies();
        return Map.of("status", "refreshed");
    }

    @GetMapping("/entitlements/subjects/{subjectId}")
    public EntitlementSnapshot snapshot(@PathVariable String subjectId) {
        return entitlementService.getSnapshot(subjectId);
    }

    public record ExportValidationRequest(
            String preset,
            String outputFormat,
            Long estimatedDurationSeconds) {}
}
