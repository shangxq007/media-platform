package com.example.platform.federation.graphql.dto;

import java.util.List;

public record NavigationRoute(
        String routeKey,
        String path,
        String title,
        String icon,
        boolean visible,
        boolean enabled,
        String reasonCode,
        String badge,
        List<NavigationRoute> children
) {}
