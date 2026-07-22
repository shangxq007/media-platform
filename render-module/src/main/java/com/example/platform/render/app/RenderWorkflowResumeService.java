package com.example.platform.render.app;

import com.example.platform.render.api.port.RenderWorkflowResumePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link RenderWorkflowResumePort} by delegating to {@link RenderJobExecutionService}.
 *
 * <p>This service exists solely to break the circular dependency between
 * {@code LocalRenderSubmitContinuation} and {@code RenderOrchestratorPort}.
 * It depends only on {@code RenderJobExecutionService}, which has no dependency
 * on the orchestrator or the continuation.</p>
 */
@Service
public class RenderWorkflowResumeService implements RenderWorkflowResumePort {

    private final RenderJobExecutionService executionService;

    public RenderWorkflowResumeService(RenderJobExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    @Transactional
    public String executeExistingRenderJob(String tenantId, String jobId) {
        return executionService.execute(tenantId, jobId);
    }
}
