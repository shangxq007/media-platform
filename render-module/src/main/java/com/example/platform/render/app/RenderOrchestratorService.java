package com.example.platform.render.app;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderJobSubmitContinuation;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pure facade for render orchestration operations.
 *
 * <p>This service holds no domain logic and no DSLContext. It delegates
 * to focused services:
 * <ul>
 *   <li>{@link RenderJobSubmissionService} — job creation and submit</li>
 *   <li>{@link RenderJobExecutionService} — job execution and finish</li>
 *   <li>{@link RenderArtifactQueryService} — artifact queries</li>
 *   <li>{@link RenderJobTimelineQueryService} — timeline JSON loading</li>
 * </ul>
 */
@Service
public class RenderOrchestratorService implements RenderOrchestratorPort {

    private final RenderJobSubmissionService submissionService;
    private final RenderJobExecutionService executionService;
    private final RenderArtifactQueryService artifactQueryService;
    private final RenderJobTimelineQueryService timelineQueryService;
    private final RenderJobSubmitContinuation submitContinuation;

    public RenderOrchestratorService(
            RenderJobSubmissionService submissionService,
            RenderJobExecutionService executionService,
            RenderArtifactQueryService artifactQueryService,
            RenderJobTimelineQueryService timelineQueryService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderJobSubmitContinuation submitContinuation) {
        this.submissionService = submissionService;
        this.executionService = executionService;
        this.artifactQueryService = artifactQueryService;
        this.timelineQueryService = timelineQueryService;
        this.submitContinuation = submitContinuation;
    }

    @Override
    @Transactional
    public String submitRenderJob(SubmitRenderJobRequest request) {
        String jobId = submissionService.submit(request);
        if (submitContinuation != null) {
            return submitContinuation.continueAfterSubmit(request.tenantId(), jobId, request);
        }
        return executionService.execute(request.tenantId(), jobId);
    }

    @Override
    @Transactional
    public String executeExistingRenderJob(String tenantId, String jobId) {
        return executionService.execute(tenantId, jobId);
    }

    @Override
    @Transactional
    public String finishRenderPhase(String tenantId, String jobId) {
        return executionService.finishRenderPhase(tenantId, jobId);
    }

    @Override
    public String loadJobTimelineJson(String tenantId, String jobId) {
        return timelineQueryService.loadJobTimelineJson(tenantId, jobId);
    }

    @Override
    public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
        return artifactQueryService.getArtifactsByJob(jobId);
    }
}
