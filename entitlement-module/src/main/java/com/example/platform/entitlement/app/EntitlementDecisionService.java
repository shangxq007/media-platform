package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.EntitlementGrantRepository;
import com.example.platform.entitlement.infrastructure.EntitlementOverrideRepository;
import com.example.platform.entitlement.infrastructure.WorkspaceEntitlementPoolRepository;
import com.example.platform.entitlement.infrastructure.WorkspaceMemberEntitlementGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EntitlementDecisionService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementDecisionService.class);

    private final EntitlementPolicyService policyService;
    private final EntitlementGrantRepository grantRepository;
    private final EntitlementOverrideRepository overrideRepository;
    private final WorkspaceEntitlementPoolRepository poolRepository;
    private final WorkspaceMemberEntitlementGrantRepository memberGrantRepository;

    public EntitlementDecisionService(EntitlementPolicyService policyService,
            @Autowired(required = false) EntitlementGrantRepository grantRepository,
            @Autowired(required = false) EntitlementOverrideRepository overrideRepository,
            @Autowired(required = false) WorkspaceEntitlementPoolRepository poolRepository,
            @Autowired(required = false) WorkspaceMemberEntitlementGrantRepository memberGrantRepository) {
        this.policyService = policyService;
        this.grantRepository = grantRepository;
        this.overrideRepository = overrideRepository;
        this.poolRepository = poolRepository;
        this.memberGrantRepository = memberGrantRepository;
    }

    public EntitlementDecision evaluate(AccessCheckRequest request) {
        List<String> matchedPolicies = new ArrayList<>();
        Instant now = Instant.now();

        String tier = policyService.getTier(request.tenantId());
        EntitlementPolicy tierPolicy = policyService.getPolicy(request.tenantId());

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

        if (memberGrantRepository != null && request.workspaceId() != null && request.userId() != null) {
            try {
                List<WorkspaceMemberEntitlementGrant> memberGrants =
                        memberGrantRepository.findActiveByMemberId(request.workspaceId(), request.userId());
                for (WorkspaceMemberEntitlementGrant g : memberGrants) {
                    if (g.featureKey().equals(request.featureKey()) && !isGrantExpired(g, now)) {
                        matchedPolicies.add("workspace-member-grant:" + g.id());
                        return new EntitlementDecision(
                                true, "ALLOW", EntitlementDecisionReason.WORKSPACE_MEMBER_GRANT.name(),
                                "Access granted by workspace member grant", tier,
                                matchedPolicies, g.id(), null, null, null,
                                null, List.of(), g.expiresAt(), false);
                    }
                }
            } catch (Exception e) {
                log.warn("Member grant check failed: {}", e.getMessage());
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

        if (grantRepository != null && request.subjectId() != null) {
            try {
                List<EntitlementGrantRepository.EntitlementGrantRecord> grants =
                        grantRepository.findActiveBySubjectId(request.subjectId());
                for (EntitlementGrantRepository.EntitlementGrantRecord g : grants) {
                    if (g.bundleCode().equals(request.featureKey()) && !isRecordExpired(g, now)) {
                        matchedPolicies.add("grant:" + g.id());
                        return new EntitlementDecision(
                                true, "ALLOW", EntitlementDecisionReason.USER_GRANT.name(),
                                "Access granted by entitlement grant", tier,
                                matchedPolicies, g.id(), null, null, null,
                                null, List.of(), g.expiresAt(), false);
                    }
                }
            } catch (Exception e) {
                log.warn("Grant check failed: {}", e.getMessage());
            }
        }

        boolean tierAllowed = checkTierPolicy(tierPolicy, request);
        if (tierAllowed) {
            matchedPolicies.add("tier:" + tier);
            return new EntitlementDecision(
                    true, "ALLOW", EntitlementDecisionReason.TIER.name(),
                    "Access granted by tier policy", tier,
                    matchedPolicies, null, null, null, null,
                    null, List.of(), null, false);
        }

        matchedPolicies.add("default-deny");
        return new EntitlementDecision(
                false, "DENY", EntitlementDecisionReason.DEFAULT_DENY.name(),
                "Access denied: feature not available for current tier", tier,
                matchedPolicies, null, null, null, null,
                null, buildUpgradeOptions(tier), null, false);
    }

    private boolean isOverrideExpired(EntitlementOverride o, Instant now) {
        return o.expiresAt() != null && o.expiresAt().isBefore(now);
    }

    private boolean isGrantExpired(WorkspaceMemberEntitlementGrant g, Instant now) {
        return g.expiresAt() != null && g.expiresAt().isBefore(now);
    }

    private boolean isRecordExpired(EntitlementGrantRepository.EntitlementGrantRecord g, Instant now) {
        return g.expiresAt() != null && g.expiresAt().isBefore(now);
    }

    private boolean checkTierPolicy(EntitlementPolicy policy, AccessCheckRequest request) {
        if (request.featureKey() == null) return false;
        if (request.providerKey() != null && !policy.isProviderAllowed(request.providerKey())) return false;
        if (request.requestedPreset() != null) {
            if (request.requestedPreset().startsWith("gpu_") && !policy.gpuAllowed()) return false;
            boolean is4k = request.requestedPreset().contains("4k") || request.requestedPreset().contains("2160p");
            if (is4k && policy.maxResolutionHeight() < 2160) return false;
        }
        return true;
    }

    private List<String> buildUpgradeOptions(String currentTier) {
        return switch (currentTier.toUpperCase()) {
            case "FREE" -> List.of("Upgrade to PRO for more features", "Upgrade to TEAM for GPU rendering");
            case "PRO" -> List.of("Upgrade to TEAM for GPU rendering", "Upgrade to ENTERPRISE for priority queue");
            case "TEAM" -> List.of("Upgrade to ENTERPRISE for priority rendering and more capacity");
            default -> List.of();
        };
    }
}
