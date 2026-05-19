package com.example.platform.federation.graphql.resolver;

import com.example.platform.billing.app.BillingDecisionService;
import com.example.platform.billing.app.BillingDecisionService.BillingContext;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.entitlement.app.EntitlementDecisionService;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.app.UserRepository;
import com.example.platform.identity.domain.Tenant;
import com.example.platform.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MeOverviewGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(MeOverviewGraphQLResolver.class);

    @Autowired private EntitlementDecisionService entitlementDecisionService;
    @Autowired private BillingDecisionService billingDecisionService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    @QueryMapping
    public MeOverview meOverview(GraphQLRequestContext context) {
        String userId = context.userId();
        String tenantId = context.tenantId();
        String workspaceId = context.workspaceId();
        List<String> roles = context.roles() != null ? context.roles() : List.of();
        List<String> permissions = context.permissions() != null ? context.permissions() : List.of();

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        String displayName = user != null ? user.username() : "Anonymous";
        String effectiveUserId = user != null ? user.id() : userId;

        TenantInfo tenantInfo = null;
        String tier = "FREE";
        if (tenantId != null) {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                tier = resolveTier(tenantId, effectiveUserId);
                tenantInfo = new TenantInfo(tenant.id(), tenant.name(), tier);
            }
        }

        WorkspaceInfo workspaceInfo = null;
        if (workspaceId != null) {
            workspaceInfo = new WorkspaceInfo(workspaceId, "Workspace", "MEMBER");
        }

        List<CapabilityDto> capabilities = resolveCapabilities(tenantId, workspaceId, effectiveUserId, tier);
        List<NavigationRoute> navigation = resolveNavigation(tier, roles, permissions);
        BillingSummary billingSummary = resolveBillingSummary(tenantId, effectiveUserId);

        return new MeOverview(
                effectiveUserId,
                displayName,
                tenantInfo,
                workspaceInfo,
                capabilities,
                navigation,
                billingSummary
        );
    }

    private String resolveTier(String tenantId, String userId) {
        try {
            AccessCheckRequest req = new AccessCheckRequest(
                    tenantId, null, userId, "USER", userId,
                    "check", "FEATURE", tenantId, "render",
                    null, null, "GRAPHQL", 0L, Map.of());
            EntitlementDecision decision = entitlementDecisionService.evaluate(req);
            return decision.currentTier() != null ? decision.currentTier() : "FREE";
        } catch (Exception e) {
            log.debug("Failed to resolve tier for tenant {}: {}", tenantId, e.getMessage());
            return "FREE";
        }
    }

    private List<CapabilityDto> resolveCapabilities(String tenantId, String workspaceId, String userId, String tier) {
        List<CapabilityDto> capabilities = new ArrayList<>();
        String[] featureKeys = {"render", "export", "ai_features", "team_collaboration", "api_access", "extensions"};
        for (String featureKey : featureKeys) {
            try {
                AccessCheckRequest req = new AccessCheckRequest(
                        tenantId, workspaceId, userId, "USER", userId,
                        "check", "FEATURE", featureKey, featureKey,
                        null, null, "GRAPHQL", 0L, Map.of());
                EntitlementDecision decision = entitlementDecisionService.evaluate(req);
                capabilities.add(new CapabilityDto(
                        featureKey,
                        decision.allowed(),
                        decision.reasonCode(),
                        decision.quotaRemaining() != null ? decision.quotaRemaining().doubleValue() : null,
                        decision.expiresAt() != null ? decision.expiresAt().toString() : null,
                        decision.matchedPolicies() != null && !decision.matchedPolicies().isEmpty()
                                ? decision.matchedPolicies().get(0) : "default"
                ));
            } catch (Exception e) {
                log.debug("Capability check failed for {}: {}", featureKey, e.getMessage());
                capabilities.add(new CapabilityDto(featureKey, false, "ERROR", null, null, "error"));
            }
        }
        return capabilities;
    }

    private List<NavigationRoute> resolveNavigation(String tier, List<String> roles, List<String> permissions) {
        List<NavigationRoute> routes = new ArrayList<>();
        routes.add(new NavigationRoute("editor", "/", "Editor", null, true, true, null, null, null));
        routes.add(new NavigationRoute("prompts", "/prompts", "Prompts", null, true, true, null, null, null));
        routes.add(new NavigationRoute("effect-packs", "/effect-packs", "Effect Packs", null, true, true, null, null, null));
        return routes;
    }

    private BillingSummary resolveBillingSummary(String tenantId, String userId) {
        try {
            BillingContext billingCtx = new BillingContext(
                    tenantId, userId, PricingModel.USAGE_BASED, 0, "USD", 0L, true);
            var decision = billingDecisionService.decideBilling("summary", billingCtx);
            MoneyDto creditBalance = new MoneyDto(0.0, decision.currencyCode());
            UsageSummary usage = new UsageSummary(0.0, 0.0, 0);
            return new BillingSummary("FREE", creditBalance, usage);
        } catch (Exception e) {
            log.debug("Billing summary resolution failed: {}", e.getMessage());
            return null;
        }
    }
}
