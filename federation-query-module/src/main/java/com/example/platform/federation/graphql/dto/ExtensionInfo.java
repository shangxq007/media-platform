package com.example.platform.federation.graphql.dto;

import java.util.List;

public record ExtensionInfo(
        String extensionKey,
        String runtimeType,
        String trustLevel,
        boolean enabled,
        String version,
        String healthStatus,
        String lastExecutionAt,
        List<RouteRule> routeRules,
        ResourceLimits resourceLimits
) {}
