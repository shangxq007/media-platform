package com.example.platform.policy.features;

import com.example.platform.policy.api.FeatureFlagEvaluator;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Boolean flags via OpenFeature (Unleash when enabled). Use from Temporal <strong>activities</strong>, not workflows.
 */
@Service
@DependsOn("openFeatureLifecycle")
public class FeatureFlagService implements FeatureFlagEvaluator {

    private final Client client = OpenFeatureAPI.getInstance().getClient();

    @Override
    public boolean isEnabled(
            String flagKey, String targetingKey, Map<String, String> attributes, boolean defaultValue) {
        Map<String, Value> attrs = new HashMap<>();
        if (attributes != null) {
            attributes.forEach((k, v) -> attrs.put(k, new Value(v != null ? v : "")));
        }
        var ctx = new ImmutableContext(targetingKey != null ? targetingKey : "", attrs);
        return client.getBooleanValue(flagKey, defaultValue, ctx);
    }
}
