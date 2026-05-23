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

    /**
     * Runs render + storage for an existing {@code render_job} row (must have timeline / ai_script).
     */
    @ActivityMethod
    String executeRenderJob(String renderJobId, String tenantId);

    /**
     * Enqueues AUTO delivery policies and processes outbound delivery jobs (when enabled).
     *
     * @return number of delivery jobs processed
     */
    @ActivityMethod
    int deliverArtifacts(String renderJobId, String tenantId);
}
