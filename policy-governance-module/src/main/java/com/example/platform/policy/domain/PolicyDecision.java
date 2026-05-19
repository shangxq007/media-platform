package com.example.platform.policy.domain;

import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;

import java.util.List;
import java.util.Map;

public record PolicyDecision(
        PolicyEffect effect,
        String reason,
        String matchedRuleId,
        Map<String, Boolean> matchedFeatureFlags
) {
    public PolicyDecision(PolicyEffect effect, String reason, String matchedRuleId) {
        this(effect, reason, matchedRuleId, Map.of());
    }
}
