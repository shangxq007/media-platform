package com.example.platform.policy.domain;

public record PolicyFeatureFlagCondition(
        String flagKey,
        String operator,
        Object expectedValue
) {}
