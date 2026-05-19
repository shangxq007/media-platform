package com.example.platform.policy.featureflag.domain;

import java.util.List;
import java.util.Map;

public record FeatureFlagContext(
        String tenantId,
        String workspaceId,
        String userId,
        List<String> roles,
        List<String> groups,
        String tier,
        String requestSource,
        String environment,
        String region,
        String riskLevel,
        Map<String, Object> attributes
) {}
