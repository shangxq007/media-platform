package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.EntitlementOverrideRepository;
import com.example.platform.entitlement.infrastructure.WorkspaceEntitlementPoolRepository;
import com.example.platform.shared.collaboration.CollaborationAccessPort;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EntitlementDecisionService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementDecisionService.class);

    private final EntitlementPolicyService policyService;
    private final EntitlementService entitlementService;
    private final EntitlementOverrideRepository overrideRepository;
    private final WorkspaceEntitlementPoolRepository poolRepository;
    private final CollaborationAccessPort collaborationAccessPort;

    public EntitlementDecisionService(
            EntitlementPolicyService policyService,
            EntitlementService entitlementService,
            Optional<EntitlementOverrideRepository> overrideRepository,
            Optional<WorkspaceEntitlementPoolRepository> poolRepository,
            Optional<CollaborationAccessPort> collaborationAccessPort) {
        this.policyService = policyService;
        this.entitlementService = entitlementService;
        this.overrideRepository = overrideRepository.orElse(null);
        this.poolRepository = poolRepository.orElse(null);
        this.collaborationAccessPort = collaborationAccessPort.orElse(null);
    }

    public EntitlementDecision evaluate(AccessCheckRequest request) {
        List<String> matchedPolicies = new ArrayList<>();
        Instant now = Instant.now();

        String tier = policyService.getTier(request.tenantId());

        if (collaborationAccessPort != null && isSharedResourceCheck(request)) {
            String userId = request.userId() != null ? request.userId() : request.subjectId();
            if (userId != null && collaborationAccessPort.hasSharedAccess(
                    request.tenantId(), userId, request.resourceType(), request.resourceId(), request.action())) {
                matchedPolicies.add("shared-resource:" + request.resourceType() + ":" + request.resourceId());
                return new EntitlementDecision(
                        true, "ALLOW", EntitlementDecisionReason.SHARED_RESOURCE_GRANT.name(),
                        "Access granted by shared resource grant", tier,
                        matchedPolicies, null, null, null, null,
                        null, List.of(), null, false);
            }
        }

        if (overrideRepository != null && request.subjectId() != null) {
            try {
                List<EntitlementOverride> overrides = overrideRepository.findActiveBySubjectId(request.subjectId());
                for (EntitlementOverride o : overrides) {
                    if (isOverrideExpired(o, now)) continue;
                    matchedPolicies.add("override:" + o.id());
                    return new EntitlementDecision(
                            true, "ALLOW", EntitlementDecisionReason.TENANT_OVERRIDE.name(),
                            "Access granted by override", tier,
                            matchedPolicies, null, o.id(), null, null,
                            null, List.of(), o.expiresAt(), false);
                }
            } catch (Exception e) {
                log.warn("Override check failed: {}", e.getMessage());
            }
        }

        if (request.subjectId() != null) {
            try {
                PrincipalRef principal = new PrincipalRef(request.tenantId(),
                        principalType(request.subjectType()), request.subjectId(), request.workspaceId(), null);
                for (EntitlementGrantView g : entitlementService.listGrants(principal)) {
                    if (g.bundleCode().equals(request.featureKey())) {
                        matchedPolicies.add((g.workspaceGrant() ? "workspace-member-grant:" : "grant:")
                                + g.grantId());
                        return new EntitlementDecision(
                                true, "ALLOW", (g.workspaceGrant()
                                        ? EntitlementDecisionReason.WORKSPACE_MEMBER_GRANT
                                        : EntitlementDecisionReason.USER_GRANT).name(),
                                "Access granted by entitlement grant", tier,
                                matchedPolicies, g.grantId(), null, null, null,
                                null, List.of(), g.expiresAt(), false);
                    }
                }
            } catch (Exception e) {
                log.warn("Member grant check failed: {}", e.getMessage());
                return persistenceDenied(tier, matchedPolicies, "grant persistence unavailable");
            }
        }

        if (poolRepository != null && request.workspaceId() != null) {
            try {
                poolRepository.findByWorkspaceAndFeature(request.workspaceId(), request.featureKey())
                        .ifPresent(pool -> {
                            long remaining = pool.totalQuota() - pool.usedQuota();
                            if (remaining > 0) {
                                matchedPolicies.add("workspace-pool:" + pool.id());
                            }
                        });
            } catch (Exception e) {
                log.warn("Pool check failed: {}", e.getMessage());
            }
        }

        matchedPolicies.add("default-deny");
        return new EntitlementDecision(
                false, "DENY", EntitlementDecisionReason.DEFAULT_DENY.name(),
                "Access denied: no active entitlement grant", tier,
                matchedPolicies, null, null, null, null,
                null, buildUpgradeOptions(tier), null, false);
    }

    private boolean isOverrideExpired(EntitlementOverride o, Instant now) {
        return o.expiresAt() != null && o.expiresAt().isBefore(now);
    }

    private static PrincipalType principalType(String subjectType) {
        if (subjectType == null || subjectType.isBlank() || "TENANT".equalsIgnoreCase(subjectType)) {
            return PrincipalType.ORGANIZATION;
        }
        return PrincipalType.valueOf(subjectType.toUpperCase());
    }

    private static boolean isSharedResourceCheck(AccessCheckRequest request) {
        if (request.resourceType() == null || request.resourceId() == null) {
            return false;
        }
        String type = request.resourceType().toLowerCase();
        return "project".equals(type) || "export".equals(type);
    }

    private List<String> buildUpgradeOptions(String currentTier) {
        return List.of("Review available commercial offerings");
    }

    private EntitlementDecision persistenceDenied(
            String tier, List<String> matchedPolicies, String detail) {
        matchedPolicies.add("persistence-deny");
        return new EntitlementDecision(false, "DENY", EntitlementDecisionReason.DEFAULT_DENY.name(),
                "Access denied: " + detail, tier, matchedPolicies, null, null, null, null,
                null, List.of(), null, false);
    }
}
