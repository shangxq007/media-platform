package com.example.platform.web.navigation;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MenuCompositionService {

    private final NavigationDecisionService decisionService;

    public MenuCompositionService(NavigationDecisionService decisionService) {
        this.decisionService = decisionService;
    }

    public NavigationDecisionService.NavigationProfile composeMenu(
            String userId, String tenantId, String source,
            String userTier, Set<String> userRoles,
            Set<String> userPermissions, Set<String> userFeatures) {

        return decisionService.buildProfile(userId, tenantId, source, userTier,
                userRoles, userPermissions, userFeatures);
    }

    public Map<String, List<NavigationDecisionService.RouteVisibilityDecision>> composeMenuGroups(
            String userId, String tenantId, String source,
            String userTier, Set<String> userRoles,
            Set<String> userPermissions, Set<String> userFeatures) {

        NavigationDecisionService.NavigationProfile profile = composeMenu(
                userId, tenantId, source, userTier, userRoles, userPermissions, userFeatures);
        return profile.menuGroups();
    }

    public List<NavigationDecisionService.RouteVisibilityDecision> resolveVisibleRoutes(
            String userId, String tenantId, String source,
            String userTier, Set<String> userRoles,
            Set<String> userPermissions, Set<String> userFeatures) {

        NavigationDecisionService.NavigationProfile profile = composeMenu(
                userId, tenantId, source, userTier, userRoles, userPermissions, userFeatures);
        return profile.routes().stream()
                .filter(NavigationDecisionService.RouteVisibilityDecision::visible)
                .collect(Collectors.toList());
    }

    public List<NavigationDecisionService.RouteVisibilityDecision> getUpgradeSuggestions(
            String userId, String tenantId, String source,
            String userTier, Set<String> userRoles,
            Set<String> userPermissions, Set<String> userFeatures) {

        NavigationDecisionService.NavigationProfile profile = composeMenu(
                userId, tenantId, source, userTier, userRoles, userPermissions, userFeatures);
        return profile.routes().stream()
                .filter(r -> r.visible() && !r.enabled())
                .filter(r -> r.upgradeOptions() != null && !r.upgradeOptions().isEmpty())
                .collect(Collectors.toList());
    }
}
