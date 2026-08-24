package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.*;
import com.example.platform.outbox.app.PostgresNotificationService;
import com.example.platform.shared.Ids;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery coordination service — records jobs and immutable task delivery intents.
 *
 * <p>This is a generic coordination layer. Domain-specific logic (what PROBE or
 * ASR means) lives in task handlers, not here.</p>
 */
@Service
public class PlatformCoordinationService {

    private static final Logger log = LoggerFactory.getLogger(PlatformCoordinationService.class);

    private final PlatformJobRepository jobRepo;
    private final PlatformTaskRepository taskRepo;
    private final PostgresNotificationService notifyService;

    public PlatformCoordinationService(PlatformJobRepository jobRepo,
                                         PlatformTaskRepository taskRepo,
                                         PostgresNotificationService notifyService) {
        this.jobRepo = jobRepo;
        this.taskRepo = taskRepo;
        this.notifyService = notifyService;
    }

    @Transactional
    public PlatformJob createJob(JobType jobType, String aggregateType, String aggregateId,
                                   String tenantId, String projectId, String payloadJson) {
        PlatformJob job = jobRepo.create(jobType, aggregateType, aggregateId, tenantId, projectId, payloadJson);
        log.info("Created platform job: id={} type={} aggregate={}/{}", job.id(), jobType, aggregateType, aggregateId);
        return job;
    }

    @Transactional
    public PlatformTask createTask(String jobId, String taskType, TaskCapability capability,
                                     String provider, int bitPosition) {
        PlatformTask task = taskRepo.create(jobId, taskType, capability, provider, bitPosition);
        int mask = 1 << bitPosition;
        PlatformJob job = jobRepo.findById(jobId).orElseThrow();
        jobRepo.updateMask(jobId, job.requiredMask() | mask, job.completedMask(), job.failedMask());
        notifyService.notifyTaskCreated();
        log.debug("Created platform task: id={} type={} capability={}", task.id(), taskType, capability);
        return task;
    }

    public List<PlatformTask> listTasks(String jobId) {
        return taskRepo.listByJob(jobId);
    }

}
