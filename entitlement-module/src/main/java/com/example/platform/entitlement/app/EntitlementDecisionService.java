package com.example.platform.entitlement.app;

import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.EntitlementOverrideRepository;
import com.example.platform.entitlement.infrastructure.WorkspaceEntitlementPoolRepository;
import com.example.platform.entitlement.api.collaboration.CollaborationAccessPort;
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
class EntitlementDecisionService implements EntitlementDecisionQuery {

    private static final Logger log = LoggerFactory.getLogger(EntitlementDecisionService.class);

    private final EntitlementPolicyService policyService;
    private final EntitlementService entitlementService;
    private final EntitlementOverrideRepository overrideRepository;
    private final WorkspaceEntitlementPoolRepository poolRepository;
    private final CollaborationAccessPort collaborationAccessPort;
    private final QuotaPolicyService quotaPolicyService;
    private final QuotaUsageAuthority quotaUsageAuthority;

    @org.springframework.beans.factory.annotation.Autowired
    public EntitlementDecisionService(
            EntitlementPolicyService policyService,
            EntitlementService entitlementService,
            Optional<EntitlementOverrideRepository> overrideRepository,
            Optional<WorkspaceEntitlementPoolRepository> poolRepository,
            Optional<CollaborationAccessPort> collaborationAccessPort,
            Optional<QuotaPolicyService> quotaPolicyService, Optional<QuotaUsageAuthority> quotaUsageAuthority) {
        this.policyService = policyService;
        this.entitlementService = entitlementService;
        this.overrideRepository = overrideRepository.orElse(null);
        this.poolRepository = poolRepository.orElse(null);
        this.collaborationAccessPort = collaborationAccessPort.orElse(null);
        this.quotaPolicyService = quotaPolicyService.orElse(null);
        this.quotaUsageAuthority = quotaUsageAuthority.orElse(null);
    }

    public EntitlementDecisionService(EntitlementPolicyService policyService,
            EntitlementService entitlementService, Optional<EntitlementOverrideRepository> overrideRepository,
            Optional<WorkspaceEntitlementPoolRepository> poolRepository,
            Optional<CollaborationAccessPort> collaborationAccessPort) {
        this(policyService, entitlementService, overrideRepository, poolRepository, collaborationAccessPort,
                Optional.of(new QuotaPolicyService()), Optional.empty());
    }

    @Override
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
                        matchedPolicies, null, null, null, quotaRemaining(request),
                        null, List.of(), null, false);
            }
        }

        if (overrideRepository != null && request.subjectId() != null) {
            try {
                List<EntitlementOverride> overrides = overrideRepository.findActiveBySubjectId(request.subjectId());
                for (EntitlementOverride o : overrides) {
                    if (isOverrideExpired(o, now)) continue;
                    if (requiredVersion(request) != null) continue;
                    matchedPolicies.add("override:" + o.id());
                    return new EntitlementDecision(
                            true, "ALLOW", EntitlementDecisionReason.TENANT_OVERRIDE.name(),
                            "Access granted by override", tier,
                            matchedPolicies, null, o.id(), null, quotaRemaining(request),
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
                    if (g.bundleCode().equals(request.featureKey()) && versionMatches(g, requiredVersion(request))) {
                        matchedPolicies.add((g.workspaceGrant() ? "workspace-member-grant:" : "grant:")
                                + g.grantId());
                        return new EntitlementDecision(
                                true, "ALLOW", (g.workspaceGrant()
                                        ? EntitlementDecisionReason.WORKSPACE_MEMBER_GRANT
                                        : EntitlementDecisionReason.USER_GRANT).name(),
                                "Access granted by entitlement grant", tier,
                                matchedPolicies, g.grantId(), null, null, quotaRemaining(request),
                                null, List.of(), g.expiresAt(), false);
                    }
                }
            } catch (Exception e) {
                log.warn("Member grant check failed: {}", e.getMessage());
                return persistenceDenied(tier, matchedPolicies, "grant persistence unavailable");
            }
        }

        if (poolRepository != null && request.workspaceId() != null && requiredVersion(request) == null) {
            try {
                var pool = poolRepository.findByWorkspaceAndFeature(request.workspaceId(), request.featureKey()).orElse(null);
                if (pool != null) {
                    java.math.BigDecimal remaining = java.math.BigDecimal.valueOf(pool.totalQuota())
                            .subtract(java.math.BigDecimal.valueOf(pool.usedQuota())).max(java.math.BigDecimal.ZERO);
                    if (remaining.signum() > 0) {
                        matchedPolicies.add("workspace-pool:" + pool.id());
                        return new EntitlementDecision(true, "ALLOW", EntitlementDecisionReason.WORKSPACE_POOL.name(),
                                "Access granted by workspace entitlement pool", tier, matchedPolicies, null, null,
                                pool.id(), remaining, null, List.of(), null, false);
                    }
                }
            } catch (Exception e) {
                log.warn("Pool check failed: {}", e.getMessage());
                return persistenceDenied(tier, matchedPolicies, "pool persistence unavailable");
            }
        }

        matchedPolicies.add("default-deny");
        return new EntitlementDecision(
                false, "DENY", EntitlementDecisionReason.DEFAULT_DENY.name(),
                "Access denied: no active entitlement grant", tier,
                matchedPolicies, null, null, null, null,
                null, buildUpgradeOptions(tier), null, false);
    }

    private java.math.BigDecimal quotaRemaining(AccessCheckRequest request) {
        try {
            if (request.featureKey() == null || quotaPolicyService == null) return java.math.BigDecimal.ZERO;
            var policy = quotaPolicyService.getQuotaPolicy(request.featureKey());
            var now = java.time.Instant.now();
            var start = now.atZone(java.time.ZoneOffset.UTC).withDayOfMonth(1).toInstant();
            var end = start.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant();
            var principal = new PrincipalRef(request.tenantId(), principalType(request.subjectType()),
                    request.subjectId() == null ? request.userId() : request.subjectId(), request.workspaceId(), null);
            if (quotaUsageAuthority == null) return policy.limitValue();
            return policy.limitValue().subtract(quotaUsageAuthority.currentUsage(new com.example.platform.entitlement.domain.QuotaUsageQuery(
                    principal, request.featureKey(), start, end, java.math.BigDecimal.ZERO, policy.limitValue(),
                    "entitlement-resolution", now))).max(java.math.BigDecimal.ZERO);
        } catch (IllegalArgumentException unknownQuota) {
            return java.math.BigDecimal.ZERO;
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("quota availability unavailable", unavailable);
        }
    }

    private static String requiredVersion(AccessCheckRequest request) {
        Object v = request.context() == null ? null : request.context().get("requiredEntitlementVersion");
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    private static boolean versionMatches(EntitlementGrantView grant, String required) {
        return required == null || required.equals(Long.toString(grant.version()));
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
