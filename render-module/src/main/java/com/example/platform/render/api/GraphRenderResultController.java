package com.example.platform.render.api;

import com.example.platform.render.app.RenderJobService;
import org.springframework.web.bind.annotation.*;

/** Public projection of canonical job state and the exact accepted graph output. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}")
public class GraphRenderResultController {
    private final RenderJobService jobs;
    private final GraphRenderLifecycle lifecycle;
    public GraphRenderResultController(RenderJobService jobs,GraphRenderLifecycle lifecycle){this.jobs=jobs;this.lifecycle=lifecycle;}
    @GetMapping("/result")
    public Result result(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String jobId) {
        var job=jobs.getByIdAndProject(tenantId,projectId,jobId);
        return lifecycle.find(tenantId,projectId,jobId).map(s->new Result(jobId,s.status(),s.execution().attemptId(),s.execution().generation(),s.finalArtifactId(),s.failureCode()))
                .orElseGet(()->new Result(jobId,job.status(),null,null,null,null));
    }
    @GetMapping("/status-history")
    public java.util.List<com.example.platform.render.app.dto.StatusHistoryResponse> history(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String jobId) {
        jobs.getByIdAndProject(tenantId,projectId,jobId);
        return jobs.getStatusHistory(jobId,tenantId);
    }
    @PostMapping("/cancel")
    public com.example.platform.render.app.dto.RenderJobResponse cancel(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String jobId) {
        jobs.getByIdAndProject(tenantId,projectId,jobId);
        return jobs.cancel(jobId,tenantId);
    }
    public record Result(String renderJobId,String status,String attemptId,Long generation,String finalArtifactId,String failureCode) {}
}
