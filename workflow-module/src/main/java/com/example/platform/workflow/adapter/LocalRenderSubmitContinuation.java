package com.example.platform.workflow.adapter;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderJobSubmitContinuation;
import com.example.platform.render.api.port.RenderWorkflowResumePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalRenderSubmitContinuation implements RenderJobSubmitContinuation {

    private final RenderWorkflowResumePort workflowResumePort;

    public LocalRenderSubmitContinuation(RenderWorkflowResumePort workflowResumePort) {
        this.workflowResumePort = workflowResumePort;
    }

    @Override
    public String continueAfterSubmit(String tenantId, String jobId, SubmitRenderJobRequest request) {
        return workflowResumePort.executeExistingRenderJob(tenantId, jobId);
    }
}
