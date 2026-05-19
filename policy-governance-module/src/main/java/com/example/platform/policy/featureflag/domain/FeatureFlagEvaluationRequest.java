package com.example.platform.policy.featureflag.domain;

public record FeatureFlagEvaluationRequest(
        String flagKey,
        FeatureFlagContext context,
        Object defaultValue
) {}
