package com.example.platform.workflow.temporal;

import com.example.platform.policy.api.FeatureFlagEvaluator;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ActivityImpl(taskQueues = RenderTaskQueue.NAME)
public class RenderActivitiesImpl implements RenderActivities {

    private final FeatureFlagEvaluator featureFlagEvaluator;

    public RenderActivitiesImpl(FeatureFlagEvaluator featureFlagEvaluator) {
        this.featureFlagEvaluator = featureFlagEvaluator;
    }

    @Override
    public String decideRenderPipeline(String renderJobId, String tenantId) {
        String key = tenantId != null && !tenantId.isBlank() ? tenantId : "anonymous";
        boolean v2 = featureFlagEvaluator.isEnabled(
                "render-pipeline-v2",
                key,
                Map.of(
                        "tenantId", tenantId != null ? tenantId : "",
                        "renderJobId", renderJobId != null ? renderJobId : ""),
                false);
        return v2 ? "v2" : "v1";
    }
}
