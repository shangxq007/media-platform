package com.example.platform.workflow.adapter;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderJobSubmitContinuation;
import com.example.platform.workflow.port.RenderExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "temporal")
public class TemporalRenderSubmitContinuation implements RenderJobSubmitContinuation {

    private static final Logger log = LoggerFactory.getLogger(TemporalRenderSubmitContinuation.class);

    private final RenderExecutionPort renderExecutionPort;

    public TemporalRenderSubmitContinuation(RenderExecutionPort renderExecutionPort) {
        this.renderExecutionPort = renderExecutionPort;
    }

    @Override
    public String continueAfterSubmit(String tenantId, String jobId, SubmitRenderJobRequest request) {
        String prompt = request.prompt() != null ? request.prompt() : "";
        log.info("Scheduling Temporal render workflow job={} tenant={}", jobId, tenantId);
        return renderExecutionPort.execute(
                jobId,
                tenantId,
                request.projectId(),
                prompt,
                request.profileOrDefault());
    }
}
