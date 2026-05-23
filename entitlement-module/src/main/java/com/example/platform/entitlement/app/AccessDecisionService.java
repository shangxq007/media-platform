package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccessDecisionService {

    private static final Logger log = LoggerFactory.getLogger(AccessDecisionService.class);

    private final EntitlementDecisionService entitlementDecisionService;
    private final QuotaDecisionService quotaDecisionService;
    private final AccessDecisionFeatureFlagService featureFlagService;

    public AccessDecisionService(EntitlementDecisionService entitlementDecisionService,
            QuotaDecisionService quotaDecisionService) {
        this(entitlementDecisionService, quotaDecisionService, null);
    }

    @Autowired
    public AccessDecisionService(EntitlementDecisionService entitlementDecisionService,
            QuotaDecisionService quotaDecisionService,
            AccessDecisionFeatureFlagService featureFlagService) {
        this.entitlementDecisionService = entitlementDecisionService;
        this.quotaDecisionService = quotaDecisionService;
        this.featureFlagService = featureFlagService;
    }

    public AccessDecision check(AccessCheckRequest request) {
        log.debug("Access check: subject={} feature={} action={}", request.subjectId(), request.featureKey(), request.action());

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult ffResult =
                featureFlagService != null
                        ? featureFlagService.evaluateForAccessDecision(request)
                        : new AccessDecisionFeatureFlagService.FeatureFlagAccessResult(
                                List.of(), false, List.of());

        EntitlementDecision entitlementDecision = entitlementDecisionService.evaluate(request);

        if (!entitlementDecision.allowed()) {
            return new AccessDecision(
                    false, "DENY", entitlementDecision.reasonCode(),
                    entitlementDecision.userFriendlyMessage(),
                    entitlementDecision.currentTier(),
                    entitlementDecision.matchedPolicies(),
                    entitlementDecision.matchedGrantId(),
                    entitlementDecision.matchedOverrideId(),
                    entitlementDecision.matchedWorkspacePoolId(),
                    null,
                    entitlementDecision.recommendedAlternative(),
                    entitlementDecision.upgradeOptions(),
                    entitlementDecision.expiresAt(),
                    entitlementDecision.requiresReview(),
                    ffResult.decisions(),
                    ffResult.disabledByFlag(),
                    ffResult.reasons()
            );
        }

        if (request.requestedQuota() != null && request.requestedQuota() > 0) {
            String quotaSubject = request.subjectId() != null ? request.subjectId() : request.tenantId();
            QuotaDecision quotaDecision = quotaDecisionService.evaluate(
                    quotaSubject, request.featureKey(), request.requestedQuota());
            if (!quotaDecision.allowed()) {
                return new AccessDecision(
                        false, "QUOTA_EXCEEDED", "QUOTA_POLICY",
                        "Quota exceeded for feature: " + request.featureKey(),
                        entitlementDecision.currentTier(),
                        entitlementDecision.matchedPolicies(),
                        entitlementDecision.matchedGrantId(),
                        entitlementDecision.matchedOverrideId(),
                        entitlementDecision.matchedWorkspacePoolId(),
                        (long) (quotaDecision.limitValue() - quotaDecision.usedValue()),
                        null,
                        List.of("Request a quota increase or reduce usage"),
                        entitlementDecision.expiresAt(),
                        false,
                        ffResult.decisions(),
                        ffResult.disabledByFlag(),
                        ffResult.reasons()
                );
            }
            return new AccessDecision(
                    true, "ALLOW", entitlementDecision.reasonCode(),
                    "Access granted",
                    entitlementDecision.currentTier(),
                    entitlementDecision.matchedPolicies(),
                    entitlementDecision.matchedGrantId(),
                    entitlementDecision.matchedOverrideId(),
                    entitlementDecision.matchedWorkspacePoolId(),
                    (long) (quotaDecision.limitValue() - quotaDecision.usedValue()),
                    null,
                    List.of(),
                    entitlementDecision.expiresAt(),
                    false,
                    ffResult.decisions(),
                    ffResult.disabledByFlag(),
                    ffResult.reasons()
            );
        }

        return new AccessDecision(
                true, "ALLOW", entitlementDecision.reasonCode(),
                "Access granted",
                entitlementDecision.currentTier(),
                entitlementDecision.matchedPolicies(),
                entitlementDecision.matchedGrantId(),
                entitlementDecision.matchedOverrideId(),
                entitlementDecision.matchedWorkspacePoolId(),
                null,
                null,
                List.of(),
                entitlementDecision.expiresAt(),
                false,
                ffResult.decisions(),
                ffResult.disabledByFlag(),
                ffResult.reasons()
        );
    }

    public EntitlementDecision evaluateEntitlement(AccessCheckRequest request) {
        return entitlementDecisionService.evaluate(request);
    }
}
