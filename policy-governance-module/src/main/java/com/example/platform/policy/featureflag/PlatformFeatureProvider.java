package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;
import com.example.platform.policy.featureflag.domain.FeatureFlagEvaluationRequest;
import java.util.List;

/**
 * The platform-owned, provider-neutral evaluation boundary.
 *
 * <p>Implementations resolve flags from the platform control plane. Adapters
 * for external evaluators must remain behind this interface and must never be
 * exposed as a product or media capability provider.</p>
 */
public interface PlatformFeatureProvider {
    FeatureFlagDecision evaluate(FeatureFlagEvaluationRequest request);

    default List<FeatureFlagDecision> evaluateBatch(List<FeatureFlagEvaluationRequest> requests) {
        return requests.stream().map(this::evaluate).toList();
    }
}
