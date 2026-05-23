package com.example.platform.ai.domain;

import java.util.List;

/**
 * Ordered provider attempts for a capability (primary + fallbacks).
 */
public record RoutePlan(List<RouteTarget> targets) {

    public RoutePlan {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
    }

    public RouteTarget primary() {
        return targets.getFirst();
    }
}
