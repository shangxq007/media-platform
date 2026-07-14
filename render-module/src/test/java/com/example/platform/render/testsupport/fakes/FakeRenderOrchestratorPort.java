package com.example.platform.render.testsupport.fakes;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.shared.Ids;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake for {@link RenderOrchestratorPort}.
 *
 * <p>Records submissions and allows pre-configured responses for
 * smoke/integration tests without Mockito.</p>
 */
public class FakeRenderOrchestratorPort implements RenderOrchestratorPort {

    private final Map<String, String> jobTimelines = new ConcurrentHashMap<>();
    private final List<SubmitRenderJobRequest> submittedJobs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> executedJobs = Collections.synchronizedList(new ArrayList<>());

    /**
     * Pre-register a timeline JSON for a job (for loadJobTimelineJson tests).
     */
    public void registerJobTimeline(String jobId, String timelineJson) {
        jobTimelines.put(jobId, timelineJson);
    }

    /**
     * Get all submitted requests (for assertion).
     */
    public List<SubmitRenderJobRequest> getSubmittedJobs() {
        return List.copyOf(submittedJobs);
    }

    /**
     * Get all executed job IDs (for assertion).
     */
    public List<String> getExecutedJobs() {
        return List.copyOf(executedJobs);
    }

    @Override
    public String submitRenderJob(SubmitRenderJobRequest request) {
        submittedJobs.add(request);
        return Ids.newId("rj-sub");
    }

    @Override
    public String executeExistingRenderJob(String tenantId, String jobId) {
        executedJobs.add(jobId);
        return jobId;
    }

    @Override
    public String finishRenderPhase(String tenantId, String jobId) {
        return jobId;
    }

    @Override
    public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
        return List.of();
    }

    @Override
    public String loadJobTimelineJson(String tenantId, String jobId) {
        return jobTimelines.getOrDefault(jobId, "{}");
    }

    @Override
    public byte[] getArtifactContent(String artifactId) {
        return new byte[0];
    }

}