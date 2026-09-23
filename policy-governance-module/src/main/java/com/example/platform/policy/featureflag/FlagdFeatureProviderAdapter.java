package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;
import com.example.platform.policy.featureflag.domain.FeatureFlagEvaluationRequest;

/**
 * Reserved internal seam for a future flagd/OpenFeature provider.
 * It is deliberately not a Spring bean and cannot become a runtime dependency
 * until identity, persistence, TLS, backup and recovery prerequisites are approved.
 */
final class FlagdFeatureProviderAdapter implements PlatformFeatureProvider {
    @Override
    public FeatureFlagDecision evaluate(FeatureFlagEvaluationRequest request) {
        throw new IllegalStateException("flagd adapter is not enabled");
    }
}
