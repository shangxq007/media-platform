package com.example.platform.render.api;

import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.RenderAcceptanceContextService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.api.context.AcceptedExecutionContext;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Canonical product reads; submission/dispatch are exclusively the formal revision-based services. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/render-jobs")
public class ProjectRenderReadController {
    private final RenderJobService jobs;
    private final RenderAcceptanceContextService contexts;
    public ProjectRenderReadController(RenderJobService jobs,RenderAcceptanceContextService contexts){this.jobs=jobs;this.contexts=contexts;}
    @GetMapping
    public List<RenderJobResponse> list(@PathVariable String tenantId,@PathVariable String projectId){return jobs.listByProject(tenantId,projectId);}
    @GetMapping("/{jobId}")
    public RenderJobResponse get(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String jobId){return jobs.getByIdAndProject(tenantId,projectId,jobId);}
    @GetMapping("/{jobId}/execution-context")
    public AcceptedExecutionContext context(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String jobId){jobs.getByIdAndProject(tenantId,projectId,jobId);return contexts.get(tenantId,projectId,jobId);}
}
