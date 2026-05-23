package com.example.platform.workflow.temporal;

import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = RenderTaskQueue.NAME)
public class RenderActivitiesImpl implements RenderActivities {

    private static final Logger log = LoggerFactory.getLogger(RenderActivitiesImpl.class);

    private final FeatureFlagEvaluator featureFlagEvaluator;
    private final RenderOrchestratorPort orchestratorPort;
    private final DeliveryAfterRenderPort deliveryAfterRenderPort;
    private final boolean deliveryActivityEnabled;

    public RenderActivitiesImpl(FeatureFlagEvaluator featureFlagEvaluator,
            @Autowired(required = false) RenderOrchestratorPort orchestratorPort,
            @Autowired(required = false) DeliveryAfterRenderPort deliveryAfterRenderPort,
            @org.springframework.beans.factory.annotation.Value("${delivery.temporal.activity-enabled:false}")
            boolean deliveryActivityEnabled) {
        this.featureFlagEvaluator = featureFlagEvaluator;
        this.orchestratorPort = orchestratorPort;
        this.deliveryAfterRenderPort = deliveryAfterRenderPort;
        this.deliveryActivityEnabled = deliveryActivityEnabled;
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

    @Override
    public String executeRenderJob(String renderJobId, String tenantId) {
        if (orchestratorPort == null) {
            throw new IllegalStateException("RenderOrchestratorPort not available in worker");
        }
        log.info("Temporal activity executeRenderJob job={} tenant={}", renderJobId, tenantId);
        return orchestratorPort.executeExistingRenderJob(tenantId, renderJobId);
    }

    @Override
    public int deliverArtifacts(String renderJobId, String tenantId) {
        if (!deliveryActivityEnabled || deliveryAfterRenderPort == null) {
            log.debug("Skipping deliverArtifacts activity job={} (disabled)", renderJobId);
            return 0;
        }
        log.info("Temporal activity deliverArtifacts job={} tenant={}", renderJobId, tenantId);
        return deliveryAfterRenderPort.finalizeDeliveriesForRenderJob(renderJobId);
    }
}
