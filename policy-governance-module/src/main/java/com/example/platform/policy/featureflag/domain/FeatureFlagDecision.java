package com.example.platform.policy.featureflag.domain;

import java.time.Instant;
import java.util.Map;

public record FeatureFlagDecision(
        String flagKey,
        boolean enabled,
        String variant,
        String reasonCode,
        FeatureFlagProviderType providerType,
        String matchedRule,
        String tenantId,
        String workspaceId,
        String userId,
        Instant evaluatedAt,
        Map<String, Object> details
) {}
