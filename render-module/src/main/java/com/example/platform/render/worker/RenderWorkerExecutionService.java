package com.example.platform.render.worker;

import com.example.platform.render.app.RenderJobExecutionService;
import com.example.platform.render.infrastructure.RenderJobRepository;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Worker-safe service for executing a single RenderJob by ID.
 * 
 * Extracts tenant/project context from RenderJob and delegates to existing execution service.
 */
@Service
public class RenderWorkerExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerExecutionService.class);

    private final RenderJobRepository renderJobRepository;
    private final RenderJobExecutionService executionService;

    public RenderWorkerExecutionService(
            RenderJobRepository renderJobRepository,
            RenderJobExecutionService executionService) {
        this.renderJobRepository = renderJobRepository;
        this.executionService = executionService;
    }

    /**
     * Execute a single RenderJob by ID.
     * 
     * @param jobId the render job ID
     * @return the job ID if execution succeeds
     * @throws IllegalArgumentException if job not found or invalid
     * @throws IllegalStateException if execution fails
     */
    public String executeOnce(String jobId) {
        log.info("Worker executing RenderJob: {}", jobId);

        // Load job
        Record job = renderJobRepository.requireJobRecord(jobId);
        String tenantId = job.get("tenant_id", String.class);
        String projectId = job.get("project_id", String.class);
        String status = job.get("status", String.class);

        log.info("RenderJob found: tenant={} project={} status={}", tenantId, projectId, status);

        // Validate executable state
        if ("COMPLETED".equals(status)) {
            log.warn("RenderJob {} already COMPLETED, skipping", jobId);
            return jobId;
        }
        if ("CANCELLED".equals(status)) {
            throw new IllegalStateException("RenderJob " + jobId + " is CANCELLED");
        }

        // Execute using existing service
        try {
            String resultJobId = executionService.execute(tenantId, jobId);
            log.info("Worker execution completed: jobId={}", resultJobId);
            return resultJobId;
        } catch (Exception e) {
            log.error("Worker execution failed for job {}: {}", jobId, e.getMessage());
            throw new IllegalStateException("Worker execution failed: " + e.getMessage(), e);
        }
    }
}
