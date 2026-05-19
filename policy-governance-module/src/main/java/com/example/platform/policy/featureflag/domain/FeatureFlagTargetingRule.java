package com.example.platform.policy.featureflag.domain;

import java.time.Instant;

public record FeatureFlagTargetingRule(
        String ruleId,
        String flagKey,
        Integer priority,
        boolean enabled,
        String tenantId,
        String workspaceId,
        String userId,
        String role,
        String group,
        String tier,
        Double percentage,
        String region,
        String requestSource,
        String environment,
        Instant startAt,
        Instant endAt
) {}
