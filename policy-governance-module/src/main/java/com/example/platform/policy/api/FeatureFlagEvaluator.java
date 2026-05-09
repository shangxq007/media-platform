package com.example.platform.policy.api;

import java.util.Map;

/**
 * Application API for feature flags (OpenFeature / Unleash). Exposed for other modules (e.g. workflow activities).
 */
public interface FeatureFlagEvaluator {

    boolean isEnabled(
            String flagKey, String targetingKey, Map<String, String> attributes, boolean defaultValue);
}
