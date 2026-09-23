package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagContext;
import com.example.platform.policy.featureflag.domain.FeatureFlagEvaluationRequest;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.Map;

/** OpenFeature SDK bridge backed by the PostgreSQL-owned platform provider. */
final class PlatformOpenFeatureProvider implements FeatureProvider {
    private final LocalFeatureFlagProvider provider;

    PlatformOpenFeatureProvider(LocalFeatureFlagProvider provider) {
        this.provider = provider;
    }

    @Override public Metadata getMetadata() { return () -> "platform-postgresql"; }

    @Override public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean fallback, EvaluationContext context) {
        var d = provider.evaluate(new FeatureFlagEvaluationRequest(key, toContext(context), fallback));
        return ProviderEvaluation.<Boolean>builder().value(d.enabled()).variant(d.variant()).reason(d.reasonCode()).build();
    }

    @Override public ProviderEvaluation<String> getStringEvaluation(String key, String fallback, EvaluationContext context) {
        var d = provider.evaluate(new FeatureFlagEvaluationRequest(key, toContext(context), fallback));
        return ProviderEvaluation.<String>builder().value(d.variant() == null ? fallback : d.variant()).variant(d.variant()).reason(d.reasonCode()).build();
    }

    @Override public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer fallback, EvaluationContext context) {
        var d = provider.evaluate(new FeatureFlagEvaluationRequest(key, toContext(context), fallback));
        return ProviderEvaluation.<Integer>builder().value(d.enabled() ? 1 : 0).variant(d.variant()).reason(d.reasonCode()).build();
    }

    @Override public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double fallback, EvaluationContext context) {
        var d = provider.evaluate(new FeatureFlagEvaluationRequest(key, toContext(context), fallback));
        return ProviderEvaluation.<Double>builder().value(d.enabled() ? 1d : 0d).variant(d.variant()).reason(d.reasonCode()).build();
    }

    @Override public ProviderEvaluation<Value> getObjectEvaluation(String key, Value fallback, EvaluationContext context) {
        var d = provider.evaluate(new FeatureFlagEvaluationRequest(key, toContext(context), fallback));
        return ProviderEvaluation.<Value>builder().value(d.enabled() ? new Value(true) : fallback).variant(d.variant()).reason(d.reasonCode()).build();
    }

    private static FeatureFlagContext toContext(EvaluationContext context) {
        if (context == null) return null;
        String targeting = context.getTargetingKey();
        Map<String, Value> attrs = context.asUnmodifiableMap();
        return new FeatureFlagContext(
                text(attrs, "tenantId"), text(attrs, "workspaceId"), text(attrs, "userId", targeting),
                java.util.List.of(), java.util.List.of(), text(attrs, "tier"), text(attrs, "requestSource"),
                text(attrs, "environment"), text(attrs, "region"), text(attrs, "riskLevel"), Map.of());
    }

    private static String text(Map<String, Value> attrs, String key) { return text(attrs, key, null); }
    private static String text(Map<String, Value> attrs, String key, String fallback) {
        Value value = attrs == null ? null : attrs.get(key);
        return value == null ? fallback : value.asString();
    }
}
