package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activities may call external systems and feature flags ({@link com.example.platform.policy.api.FeatureFlagEvaluator}).
 */
@ActivityInterface
public interface RenderActivities {

    /**
     * Resolves which render pipeline variant to use (e.g. Unleash flag {@code render-pipeline-v2}).
     */
    @ActivityMethod
    String decideRenderPipeline(String renderJobId, String tenantId);
}
