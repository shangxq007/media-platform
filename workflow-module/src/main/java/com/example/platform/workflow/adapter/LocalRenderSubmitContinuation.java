package com.example.platform.workflow.adapter;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderJobSubmitContinuation;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalRenderSubmitContinuation implements RenderJobSubmitContinuation {

    private final RenderOrchestratorPort orchestratorPort;

    public LocalRenderSubmitContinuation(@Lazy RenderOrchestratorPort orchestratorPort) {
        this.orchestratorPort = orchestratorPort;
    }

    @Override
    public String continueAfterSubmit(String tenantId, String jobId, SubmitRenderJobRequest request) {
        return orchestratorPort.executeExistingRenderJob(tenantId, jobId);
    }
}
