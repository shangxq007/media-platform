package com.example.platform.render.app.execution;

import com.example.platform.render.app.environment.EnvironmentRuntimeService;
import com.example.platform.render.domain.environment.ExecutionEnvironment;
import com.example.platform.render.domain.execution.ExecutionJob;
import com.example.platform.render.domain.execution.ExecutionStatus;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Execution Control Plane — single platform entry for job management.
 * Platform owns lifecycle. Environments execute. Never reversed.
 */
@Service
public class ExecutionControlService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionControlService.class);
    private final EnvironmentRuntimeService environmentRuntime;
    private final ExecutionJobRegistry jobRegistry;

    public ExecutionControlService(EnvironmentRuntimeService environmentRuntime,
                                     ExecutionJobRegistry jobRegistry) {
        this.environmentRuntime = environmentRuntime;
        this.jobRegistry = jobRegistry;
    }

    public String submit(ExecutionJob job) {
        Optional<ExecutionEnvironment> env = environmentRuntime.resolve(job.environmentId());
        if (env.isEmpty()) {
            log.warn("No environment found for {}", job.environmentId());
            return null;
        }

        jobRegistry.register(job);
        jobRegistry.updateStatus(job.jobId(), ExecutionStatus.SUBMITTED);
        log.info("Job submitted: id={} env={}", job.jobId(), job.environmentId());

        String execId = env.get().submit(job);
        jobRegistry.updateStatus(job.jobId(), ExecutionStatus.RUNNING);
        return execId;
    }

    public String status(String jobId) {
        return jobRegistry.find(jobId)
                .map(j -> j.status().name())
                .orElse("UNKNOWN");
    }

    public boolean cancel(String jobId) {
        var job = jobRegistry.find(jobId);
        if (job.isEmpty()) return false;
        jobRegistry.updateStatus(jobId, ExecutionStatus.CANCELLED);
        log.info("Job cancelled: id={}", jobId);
        return true;
    }

    public List<ExecutionJob> listJobs() {
        return jobRegistry.listAll();
    }

    public void complete(String jobId) {
        jobRegistry.updateStatus(jobId, ExecutionStatus.COMPLETED);
        log.info("Job completed: id={}", jobId);
    }

    public void fail(String jobId, String reason) {
        jobRegistry.find(jobId).ifPresent(j -> {
            jobRegistry.register(j.withStatus(ExecutionStatus.FAILED));
        });
        log.info("Job failed: id={} reason={}", jobId, reason);
    }
}
