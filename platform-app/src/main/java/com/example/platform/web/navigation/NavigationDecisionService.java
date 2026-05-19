package com.example.platform.web.navigation;

import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.app.RoleService;
import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.shared.entitlement.EntitlementPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NavigationDecisionService {

    private static final Logger log = LoggerFactory.getLogger(NavigationDecisionService.class);

    private final NavigationRegistryService registryService;
    private final PermissionService permissionService;
    private final RoleService roleService;
    private final EntitlementPort entitlementPort;
    private final FeatureFlagEvaluator featureFlagEvaluator;

    public NavigationDecisionService(NavigationRegistryService registryService,
                                      PermissionService permissionService,
                                      RoleService roleService,
                                      EntitlementPort entitlementPort) {
        this(registryService, permissionService, roleService, entitlementPort, null);
    }

    public NavigationDecisionService(NavigationRegistryService registryService,
                                      PermissionService permissionService,
                                      RoleService roleService,
                                      EntitlementPort entitlementPort,
                                      FeatureFlagEvaluator featureFlagEvaluator) {
        this.registryService = registryService;
        this.permissionService = permissionService;
        this.roleService = roleService;
        this.entitlementPort = entitlementPort;
        this.featureFlagEvaluator = featureFlagEvaluator;
    }

    public List<RouteVisibilityDecision> evaluateRoutes(String userId, String tenantId,
                                                         String source, String userTier,
                                                         Set<String> userRoles,
                                                         Set<String> userPermissions,
                                                         Set<String> userFeatures) {
        List<FrontendRouteDefinition> definitions = registryService.loadAllRouteDefinitions();
        Map<String, NavigationPolicy> policies = registryService.loadAllPolicies();

        return definitions.stream()
                .map(def -> evaluateRoute(def, userId, tenantId, source, userTier,
                        userRoles, userPermissions, userFeatures, policies))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(RouteVisibilityDecision::order))
                .collect(Collectors.toList());
    }

    public NavigationProfile buildProfile(String userId, String tenantId,
                                           String source, String userTier,
                                           Set<String> userRoles,
                                           Set<String> userPermissions,
                                           Set<String> userFeatures) {
        List<RouteVisibilityDecision> decisions = evaluateRoutes(
                userId, tenantId, source, userTier, userRoles, userPermissions, userFeatures);

        Map<String, List<RouteVisibilityDecision>> menuGroups = new LinkedHashMap<>();
        for (RouteVisibilityDecision decision : decisions) {
            if (!decision.visible()) continue;
            String group = decision.menuGroup();
            if (group == null || group.isBlank()) group = "default";
            menuGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(decision);
        }

        return new NavigationProfile(decisions, menuGroups);
    }

    private RouteVisibilityDecision evaluateRoute(FrontendRouteDefinition def,
                                                   String userId, String tenantId,
                                                   String source, String userTier,
                                                   Set<String> userRoles,
                                                   Set<String> userPermissions,
                                                   Set<String> userFeatures,
                                                   Map<String, NavigationPolicy> policies) {
        List<String> reasons = new ArrayList<>();
        boolean visible = def.visible() != null ? def.visible() : true;
        boolean enabled = def.enabled() != null ? def.enabled() : true;

        Map<String, Boolean> matchedFeatureFlags = new LinkedHashMap<>();
        boolean beta = false;
        boolean rollout = false;
        boolean disabledByFeatureFlag = false;

        if (def.supportedSources() != null && !def.supportedSources().isEmpty()) {
            boolean sourceMatch = def.supportedSources().stream()
                    .anyMatch(s -> s.equalsIgnoreCase(source));
            if (!sourceMatch) {
                visible = false;
                reasons.add("NAV-403-SOURCE: Route '" + def.routeKey() + "' not available for source " + source);
            }
        }

        if (def.requiredRoles() != null && !def.requiredRoles().isEmpty()) {
            boolean hasRole = def.requiredRoles().stream()
                    .anyMatch(r -> userRoles != null && userRoles.contains(r));
            if (!hasRole) {
                visible = false;
                reasons.add("NAV-403-ROLE: Route '" + def.routeKey() + "' requires one of roles " + def.requiredRoles());
            }
        }

        if (def.requiredPermissions() != null && !def.requiredPermissions().isEmpty()) {
            boolean hasPermission = def.requiredPermissions().stream()
                    .anyMatch(p -> userPermissions != null && userPermissions.contains(p));
            if (!hasPermission) {
                visible = false;
                reasons.add("NAV-403-PERM: Route '" + def.routeKey() + "' requires one of permissions " + def.requiredPermissions());
            }
        }

        if (def.requiredTier() != null && !def.requiredTier().isBlank()) {
            if (userTier == null || !meetsTierRequirement(userTier, def.requiredTier())) {
                enabled = false;
                reasons.add("NAV-403-TIER: Route '" + def.routeKey() + "' requires tier " + def.requiredTier());
            }
        }

        if (def.requiredFeatures() != null && !def.requiredFeatures().isEmpty()) {
            boolean hasAllFeatures = def.requiredFeatures().stream()
                    .allMatch(f -> userFeatures != null && userFeatures.contains(f));
            if (!hasAllFeatures) {
                enabled = false;
                reasons.add("NAV-403-FEAT: Route '" + def.routeKey() + "' requires features " + def.requiredFeatures());
            }
        }

        if (def.requiredEntitlements() != null && !def.requiredEntitlements().isEmpty()) {
            for (String entitlement : def.requiredEntitlements()) {
                if (userPermissions != null && !userPermissions.contains(entitlement)) {
                    enabled = false;
                    reasons.add("NAV-403-ENT: Route '" + def.routeKey() + "' requires entitlement " + entitlement);
                    break;
                }
            }
        }

        if (featureFlagEvaluator != null && def.requiredFeatureFlags() != null && !def.requiredFeatureFlags().isEmpty()) {
            for (String flagKey : def.requiredFeatureFlags()) {
                boolean flagEnabled = featureFlagEvaluator.isEnabled(
                        flagKey, userId, Map.of("tenantId", tenantId != null ? tenantId : ""), false);
                matchedFeatureFlags.put(flagKey, flagEnabled);
                if (!flagEnabled) {
                    enabled = false;
                    disabledByFeatureFlag = true;
                    reasons.add("NAV-403-FF: Route '" + def.routeKey() + "' requires feature flag '" + flagKey + "'");
                }
            }
        }

        if (featureFlagEvaluator != null && def.betaFlagKey() != null && !def.betaFlagKey().isBlank()) {
            boolean betaEnabled = featureFlagEvaluator.isEnabled(
                    def.betaFlagKey(), userId, Map.of("tenantId", tenantId != null ? tenantId : ""), false);
            matchedFeatureFlags.put(def.betaFlagKey(), betaEnabled);
            beta = betaEnabled;
            if (!betaEnabled) {
                visible = false;
                reasons.add("NAV-403-BETA: Route '" + def.routeKey() + "' is in beta and flag '" + def.betaFlagKey() + "' is disabled");
            }
        }

        if (featureFlagEvaluator != null && def.rolloutFlagKey() != null && !def.rolloutFlagKey().isBlank()) {
            boolean rolloutEnabled = featureFlagEvaluator.isEnabled(
                    def.rolloutFlagKey(), userId, Map.of("tenantId", tenantId != null ? tenantId : ""), false);
            matchedFeatureFlags.put(def.rolloutFlagKey(), rolloutEnabled);
            rollout = rolloutEnabled;
            if (!rolloutEnabled) {
                enabled = false;
                disabledByFeatureFlag = true;
                reasons.add("NAV-403-ROLLOUT: Route '" + def.routeKey() + "' rollout flag '" + def.rolloutFlagKey() + "' is disabled");
            }
        }

        List<NavigationPolicy> routePolicies = policies.values().stream()
                .filter(p -> p.routeKey().equals(def.routeKey()))
                .sorted(Comparator.comparingInt(NavigationPolicy::priority).reversed())
                .collect(Collectors.toList());

        for (NavigationPolicy policy : routePolicies) {
            if (!policy.enabled()) continue;
            boolean conditionMatches = evaluatePolicyCondition(policy, userId, tenantId,
                    source, userTier, userRoles, userPermissions, userFeatures);
            if (conditionMatches) {
                switch (policy.effect()) {
                    case "HIDE" -> {
                        visible = false;
                        reasons.add(policy.reasonCode() + ": " + policy.reasonMessage());
                    }
                    case "DISABLE" -> {
                        enabled = false;
                        reasons.add(policy.reasonCode() + ": " + policy.reasonMessage());
                    }
                }
            }
        }

        if (!visible) {
            reasons.add(0, "NAV-404-HIDDEN: Route '" + def.routeKey() + "' is hidden");
        } else if (!enabled) {
            reasons.add(0, "NAV-403-DISABLED: Route '" + def.routeKey() + "' is disabled");
        }

        String primaryReason = reasons.isEmpty() ? null : reasons.get(0);
        String reasonCode = primaryReason != null && primaryReason.contains(":")
                ? primaryReason.substring(0, primaryReason.indexOf(':')) : null;
        String userFriendlyMessage = primaryReason != null && primaryReason.contains(":")
                ? primaryReason.substring(primaryReason.indexOf(':') + 1).trim() : null;

        List<String> upgradeOpts = new ArrayList<>();
        if (!enabled && def.upgradeOptions() != null) {
            upgradeOpts.addAll(def.upgradeOptions());
        }
        for (NavigationPolicy policy : routePolicies) {
            if (policy.upgradeOptions() != null) {
                upgradeOpts.addAll(policy.upgradeOptions());
            }
        }

        return new RouteVisibilityDecision(
                def.routeKey(),
                def.path(),
                def.title(),
                def.menuGroup(),
                def.order() != null ? def.order() : 0,
                visible,
                enabled,
                reasonCode,
                userFriendlyMessage,
                def.requiredTier(),
                def.requiredPermissions() != null && !def.requiredPermissions().isEmpty()
                        ? def.requiredPermissions().get(0) : null,
                def.requiredEntitlements() != null && !def.requiredEntitlements().isEmpty()
                        ? def.requiredEntitlements().get(0) : null,
                upgradeOpts.isEmpty() ? null : upgradeOpts,
                null,
                matchedFeatureFlags,
                beta,
                rollout,
                disabledByFeatureFlag
        );
    }

    private boolean evaluatePolicyCondition(NavigationPolicy policy, String userId,
                                             String tenantId, String source,
                                             String userTier, Set<String> userRoles,
                                             Set<String> userPermissions,
                                             Set<String> userFeatures) {
        String condition = policy.condition();
        if (condition == null || condition.isBlank()) return true;

        try {
            String[] parts = condition.split("=", 2);
            if (parts.length != 2) return true;
            String attr = parts[0].trim();
            String expected = parts[1].trim();

            return switch (attr) {
                case "source" -> source != null && source.equalsIgnoreCase(expected);
                case "tier" -> userTier != null && userTier.equalsIgnoreCase(expected);
                case "role" -> userRoles != null && userRoles.contains(expected);
                case "permission" -> userPermissions != null && userPermissions.contains(expected);
                case "feature" -> userFeatures != null && userFeatures.contains(expected);
                default -> true;
            };
        } catch (Exception e) {
            log.warn("Failed to evaluate policy condition '{}': {}", condition, e.getMessage());
            return true;
        }
    }

    private boolean meetsTierRequirement(String userTier, String requiredTier) {
        List<String> tierOrder = List.of("FREE", "BASIC", "STANDARD", "PROFESSIONAL", "ENTERPRISE");
        int userIdx = tierOrder.indexOf(userTier.toUpperCase());
        int requiredIdx = tierOrder.indexOf(requiredTier.toUpperCase());
        if (userIdx < 0 || requiredIdx < 0) return false;
        return userIdx >= requiredIdx;
    }

    public record RouteVisibilityDecision(
            String routeKey,
            String path,
            String title,
            String menuGroup,
            int order,
            boolean visible,
            boolean enabled,
            String reasonCode,
            String userFriendlyMessage,
            String requiredTier,
            String requiredPermission,
            String requiredEntitlement,
            List<String> upgradeOptions,
            List<RouteVisibilityDecision> children,
            Map<String, Boolean> matchedFeatureFlags,
            boolean beta,
            boolean rollout,
            boolean disabledByFeatureFlag
    ) {}

    public record NavigationProfile(
            List<RouteVisibilityDecision> routes,
            Map<String, List<RouteVisibilityDecision>> menuGroups
    ) {}
}
