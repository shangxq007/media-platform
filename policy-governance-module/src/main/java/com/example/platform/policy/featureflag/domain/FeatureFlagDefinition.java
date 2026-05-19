package com.example.platform.policy.featureflag.domain;

import java.time.Instant;
import java.util.List;

public record FeatureFlagDefinition(
        String flagKey,
        String name,
        String description,
        FeatureFlagType flagType,
        Object defaultValue,
        List<FeatureFlagVariant> variants,
        List<FeatureFlagTargetingRule> targetingRules,
        boolean enabled,
        String owner,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt,
        boolean archived
) {}
