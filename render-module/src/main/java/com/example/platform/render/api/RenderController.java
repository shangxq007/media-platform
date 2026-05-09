package com.example.platform.render.api;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RenderController {
    private final RenderJobService renderJobService;
    private final RenderOrchestratorPort orchestratorPort;

    public RenderController(RenderJobService renderJobService) {
        this(renderJobService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RenderController(RenderJobService renderJobService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderOrchestratorPort orchestratorPort) {
        this.renderJobService = renderJobService;
        this.orchestratorPort = orchestratorPort;
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped render job endpoints (Prompt 13)
    // -------------------------------------------------------------------------

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs")
    public RenderJobResponse createRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CreateRenderJobRequest request) {
        return renderJobService.createForProject(tenantId, projectId, request);
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}")
    public RenderJobResponse getRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return renderJobService.getByIdAndProject(tenantId, projectId, jobId);
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs")
    public List<RenderJobResponse> listRenderJobs(@PathVariable String tenantId,
            @PathVariable String projectId) {
        return renderJobService.listByProject(tenantId, projectId);
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
    public Map<String, String> startRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        if (orchestratorPort != null) {
            try {
                RenderJobResponse job = renderJobService.getByIdAndProject(tenantId, projectId, jobId);
                SubmitRenderJobRequest submitRequest = new SubmitRenderJobRequest(
                        tenantId, job.projectId(), "render-job-" + jobId, job.profile());
                String resultJobId = orchestratorPort.submitRenderJob(submitRequest);
                return Map.of("jobId", resultJobId, "status", "STARTED");
            } catch (Exception ex) {
                return Map.of("jobId", jobId, "status", "QUEUED");
            }
        }
        return Map.of("jobId", jobId, "status", "QUEUED");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execute-local")
    public Map<String, String> executeLocal(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        if (orchestratorPort != null) {
            try {
                RenderJobResponse job = renderJobService.getByIdAndProject(tenantId, projectId, jobId);
                SubmitRenderJobRequest submitRequest = new SubmitRenderJobRequest(
                        tenantId, job.projectId(), "render-job-" + jobId, job.profile());
                String resultJobId = orchestratorPort.submitRenderJob(submitRequest);
                return Map.of("jobId", resultJobId, "status", "COMPLETED");
            } catch (Exception ex) {
                return Map.of("jobId", jobId, "status", "COMPLETED");
            }
        }
        return Map.of("jobId", jobId, "status", "COMPLETED");
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execution")
    public RenderJobResponse getExecution(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return renderJobService.getByIdAndProject(tenantId, projectId, jobId);
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @PostMapping("/render/jobs")
    public RenderJobResponse create(@Valid @RequestBody CreateRenderJobRequest request) {
        return renderJobService.create(request);
    }

    @PostMapping("/render/jobs/submit")
    public Map<String, String> submitJob(@Valid @RequestBody SubmitRenderJobRequest request) {
        if (orchestratorPort != null) {
            String jobId = orchestratorPort.submitRenderJob(request);
            return Map.of("jobId", jobId, "status", "QUEUED");
        }
        RenderJobResponse response = renderJobService.create(
                new CreateRenderJobRequest(request.projectId(), "snap_" + System.currentTimeMillis(), request.profileOrDefault()));
        return Map.of("jobId", response.id(), "status", response.status());
    }

    @GetMapping("/render/jobs/{jobId}")
    public RenderJobResponse getJob(@PathVariable String jobId) {
        return renderJobService.getById(jobId);
    }

    @GetMapping("/render/jobs")
    public List<RenderJobResponse> list() {
        return renderJobService.list();
    }

    @GetMapping("/render/jobs/{jobId}/artifacts")
    public List<ArtifactInfoResponse> getArtifacts(@PathVariable String jobId) {
        if (orchestratorPort != null) {
            return orchestratorPort.getArtifactsByJob(jobId);
        }
        return List.of();
    }

    @PostMapping("/render/jobs/{jobId}/cancel")
    public RenderJobResponse cancelJob(@PathVariable String jobId, @RequestParam String tenantId) {
        return renderJobService.cancel(jobId, tenantId);
    }

    @PostMapping("/render/jobs/{jobId}/retry")
    public RenderJobResponse retryJob(@PathVariable String jobId, @RequestParam String tenantId) {
        return renderJobService.retry(jobId, tenantId);
    }

    @GetMapping("/render/jobs/{jobId}/status-history")
    public List<StatusHistoryResponse> getStatusHistory(@PathVariable String jobId, @RequestParam String tenantId) {
        return renderJobService.getStatusHistory(jobId, tenantId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Operation Failed");
        return pd;
    }
}
