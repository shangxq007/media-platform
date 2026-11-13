package com.example.platform.config.firstrender;

import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import com.example.platform.shared.web.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Product lifecycle projection for a First Render job: stable job + revision + result identity. */
@RestController
public class FirstRenderJobLifecycleController {
    private final RenderJobService jobs;
    private final GraphRenderLifecycle lifecycle;
    private final RenderJobRevisionPinningService pinning;

    public FirstRenderJobLifecycleController(RenderJobService jobs, GraphRenderLifecycle lifecycle,
            RenderJobRevisionPinningService pinning) {
        this.jobs = jobs;
        this.lifecycle = lifecycle;
        this.pinning = pinning;
    }

    @GetMapping("/api/first-render/jobs/{jobId}")
    public Lifecycle lifecycle(@PathVariable String jobId) {
        var job = jobs.getById(jobId);
        var tenantId = TenantContext.get();
        var revisionId = pinning.getPinnedRevisionId(jobId);
        var result = lifecycle.find(tenantId, job.projectId(), jobId);
        return new Lifecycle(jobId, revisionId, job.projectId(), job.status(),
                result.map(GraphRenderLifecycle.Status::finalArtifactId).orElse(null),
                result.map(GraphRenderLifecycle.Status::failureCode).orElse(null),
                result.map(r -> r.execution().attemptId()).orElse(null),
                result.map(r -> r.execution().generation()).orElse(null));
    }

    @GetMapping("/api/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/lifecycle")
    public Lifecycle scopedLifecycle(@PathVariable String tenantId, @PathVariable String projectId,
            @PathVariable String jobId) {
        var job = jobs.getByIdAndProject(tenantId, projectId, jobId);
        var revisionId = pinning.getPinnedRevisionId(jobId);
        var result = lifecycle.find(tenantId, projectId, jobId);
        return new Lifecycle(jobId, revisionId, job.projectId(), job.status(),
                result.map(GraphRenderLifecycle.Status::finalArtifactId).orElse(null),
                result.map(GraphRenderLifecycle.Status::failureCode).orElse(null),
                result.map(r -> r.execution().attemptId()).orElse(null),
                result.map(r -> r.execution().generation()).orElse(null));
    }

    public record Lifecycle(String renderJobId, String timelineRevisionId, String projectId,
            String status, String finalArtifactId, String failureCode,
            String attemptId, Long generation) {}
}
