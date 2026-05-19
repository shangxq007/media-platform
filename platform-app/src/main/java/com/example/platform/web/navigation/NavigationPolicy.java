package com.example.platform.web.navigation;

import java.util.List;

public record NavigationPolicy(
        String policyKey,
        String routeKey,
        String policyType,
        String condition,
        String effect,
        String reasonCode,
        String reasonMessage,
        List<String> upgradeOptions,
        int priority,
        boolean enabled
) {
    public NavigationPolicy {
        if (policyKey == null || policyKey.isBlank()) throw new IllegalArgumentException("policyKey is required");
        if (routeKey == null || routeKey.isBlank()) throw new IllegalArgumentException("routeKey is required");
        if (policyType == null || policyType.isBlank()) throw new IllegalArgumentException("policyType is required");
        if (effect == null || effect.isBlank()) throw new IllegalArgumentException("effect is required");
        if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode is required");
    }
}
