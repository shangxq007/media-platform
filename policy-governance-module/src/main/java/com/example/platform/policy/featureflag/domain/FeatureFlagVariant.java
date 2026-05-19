package com.example.platform.policy.featureflag.domain;

public record FeatureFlagVariant(
        String key,
        Object value,
        String description
) {}
