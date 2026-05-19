package com.example.platform.web.navigation;

import java.util.List;

public record FrontendRouteDefinition(
        String routeKey,
        String path,
        String componentKey,
        String title,
        String description,
        String menuGroup,
        String icon,
        Integer order,
        String parentRouteKey,
        List<String> requiredPermissions,
        List<String> requiredRoles,
        List<String> requiredEntitlements,
        String requiredTier,
        List<String> requiredFeatures,
        List<String> supportedSources,
        Boolean visible,
        Boolean enabled,
        String hiddenReason,
        String disabledReason,
        List<String> upgradeOptions,
        List<String> requiredFeatureFlags,
        String betaFlagKey,
        String rolloutFlagKey
) {
    public FrontendRouteDefinition {
        if (routeKey == null || routeKey.isBlank()) throw new IllegalArgumentException("routeKey is required");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
        if (componentKey == null || componentKey.isBlank()) throw new IllegalArgumentException("componentKey is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
    }

    public FrontendRouteDefinition(String routeKey, String path, String componentKey, String title,
                                    String description, String menuGroup, String icon, Integer order,
                                    String parentRouteKey, List<String> requiredPermissions,
                                    List<String> requiredRoles, List<String> requiredEntitlements,
                                    String requiredTier, List<String> requiredFeatures,
                                    List<String> supportedSources, Boolean visible, Boolean enabled,
                                    String hiddenReason, String disabledReason,
                                    List<String> upgradeOptions) {
        this(routeKey, path, componentKey, title, description, menuGroup, icon, order,
                parentRouteKey, requiredPermissions, requiredRoles, requiredEntitlements,
                requiredTier, requiredFeatures, supportedSources, visible, enabled,
                hiddenReason, disabledReason, upgradeOptions, List.of(), null, null);
    }
}
