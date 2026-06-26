package com.example.platform.render.app.execution;

import com.example.platform.render.domain.execution.ExecutionJob;
import com.example.platform.render.domain.execution.ExecutionStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory execution job registry. No persistence.
 * Platform-owned — environments never update this directly.
 */
@Component
public class ExecutionJobRegistry {

    private final Map<String, ExecutionJob> jobs = new ConcurrentHashMap<>();

    public void register(ExecutionJob job) {
        jobs.put(job.jobId(), job);
    }

    public Optional<ExecutionJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public void updateStatus(String jobId, ExecutionStatus status) {
        find(jobId).ifPresent(job -> jobs.put(jobId, job.withStatus(status)));
    }

    public List<ExecutionJob> listAll() {
        return new ArrayList<>(jobs.values());
    }
}
