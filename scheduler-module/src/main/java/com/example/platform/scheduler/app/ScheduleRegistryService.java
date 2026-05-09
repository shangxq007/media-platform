package com.example.platform.scheduler.app;

import com.example.platform.scheduler.domain.JobStatus;
import com.example.platform.scheduler.domain.ScheduledJobDefinition;
import com.example.platform.scheduler.domain.ScheduledJobRun;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduleRegistryService {

    private final ConcurrentHashMap<String, ScheduledJobDefinition> jobDefinitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledJobRun> jobRuns = new ConcurrentHashMap<>();
    private final ArrayList<OutboxRetryPort> outboxRetryPorts = new ArrayList<>();

    public Map<String, Object> overview() {
        long activeJobs = jobDefinitions.values().stream().filter(ScheduledJobDefinition::enabled).count();
        long totalRuns = jobRuns.size();
        long pendingRuns = jobRuns.values().stream().filter(r -> r.status() == JobStatus.PENDING).count();
        long failedRuns = jobRuns.values().stream().filter(r -> r.status() == JobStatus.FAILED).count();
        return Map.of(
                "module", "scheduler-module",
                "status", "active",
                "description", "统一调度模块，负责周期任务、清理任务与补偿任务登记。",
                "registeredJobs", jobDefinitions.size(),
                "activeJobs", activeJobs,
                "totalRuns", totalRuns,
                "pendingRuns", pendingRuns,
                "failedRuns", failedRuns
        );
    }

    public ScheduledJobDefinition registerJob(ScheduledJobDefinition definition) {
        jobDefinitions.put(definition.id(), definition);
        return definition;
    }

    public ScheduledJobDefinition registerJob(String name, String cronExpression, int maxRetries) {
        String id = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ScheduledJobDefinition definition = new ScheduledJobDefinition(
                id, name, cronExpression, true, maxRetries, Instant.now());
        return registerJob(definition);
    }

    public List<ScheduledJobDefinition> listJobs() {
        return List.copyOf(jobDefinitions.values());
    }

    public ScheduledJobDefinition findJob(String jobId) {
        return jobDefinitions.get(jobId);
    }

    public ScheduledJobRun recordRun(ScheduledJobRun run) {
        jobRuns.put(run.id(), run);
        return run;
    }

    public ScheduledJobRun startRun(String jobDefinitionId) {
        String id = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ScheduledJobRun run = new ScheduledJobRun(
                id, jobDefinitionId, JobStatus.RUNNING, Instant.now(), null, 0, null);
        return recordRun(run);
    }

    public ScheduledJobRun completeRun(String runId) {
        ScheduledJobRun run = jobRuns.get(runId);
        if (run != null) {
            ScheduledJobRun completed = run.withStatus(JobStatus.COMPLETED).withFinishedAt(Instant.now());
            return recordRun(completed);
        }
        return null;
    }

    public ScheduledJobRun failRun(String runId, String errorMessage) {
        ScheduledJobRun run = jobRuns.get(runId);
        if (run != null) {
            ScheduledJobRun failed = run.withStatus(JobStatus.FAILED)
                    .withFinishedAt(Instant.now())
                    .withErrorMessage(errorMessage);
            return recordRun(failed);
        }
        return null;
    }

    public List<ScheduledJobRun> findPendingRuns() {
        return jobRuns.values().stream()
                .filter(r -> r.status() == JobStatus.PENDING)
                .toList();
    }

    public List<ScheduledJobRun> findFailedRuns() {
        return jobRuns.values().stream()
                .filter(r -> r.status() == JobStatus.FAILED)
                .toList();
    }

    public List<ScheduledJobRun> findRunsByJob(String jobDefinitionId) {
        return jobRuns.values().stream()
                .filter(r -> r.jobDefinitionId().equals(jobDefinitionId))
                .toList();
    }

    public int retryFailedRuns(int maxRetries) {
        List<ScheduledJobRun> failedRuns = findFailedRuns();
        int retriedCount = 0;
        for (ScheduledJobRun run : failedRuns) {
            ScheduledJobDefinition definition = jobDefinitions.get(run.jobDefinitionId());
            if (definition == null) {
                continue;
            }
            if (run.retryCount() < definition.maxRetries() && run.retryCount() < maxRetries) {
                ScheduledJobRun retried = new ScheduledJobRun(
                        run.id(),
                        run.jobDefinitionId(),
                        JobStatus.PENDING,
                        Instant.now(),
                        null,
                        run.retryCount() + 1,
                        null);
                recordRun(retried);
                retriedCount++;
            } else {
                ScheduledJobRun deadLetter = run.withStatus(JobStatus.DEAD_LETTER);
                recordRun(deadLetter);
            }
        }
        return retriedCount;
    }

    public void registerOutboxRetryPort(OutboxRetryPort port) {
        outboxRetryPorts.add(port);
    }

    public int retryPendingOutboxEvents(int batchSize) {
        int totalRetried = 0;
        for (OutboxRetryPort port : outboxRetryPorts) {
            totalRetried += port.retryPendingOutboxEvents(batchSize);
        }
        return totalRetried;
    }

    public List<ScheduledJobRun> listRuns() {
        return List.copyOf(jobRuns.values());
    }
}
