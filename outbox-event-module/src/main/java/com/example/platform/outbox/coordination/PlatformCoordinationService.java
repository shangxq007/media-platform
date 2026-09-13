package com.example.platform.outbox.coordination;

import com.example.platform.sandbox.execution.TaskCapability;

import com.example.platform.outbox.coordination.*;
import com.example.platform.outbox.app.PostgresNotificationService;
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
@org.springframework.modulith.NamedInterface("coordination")
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

    /** One local job/task intent for an immutable delivered fact; external execution remains at-least-once. */
    @Transactional
    public PlatformJob createJobWithTaskOnce(String deliveryKey,JobType jobType,String aggregateType,String aggregateId,
            String tenantId,String projectId,String payloadJson,String taskType,TaskCapability capability) {
        if(deliveryKey==null||deliveryKey.isBlank())throw new IllegalArgumentException("Delivery key required");
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenantId);
        String id="pjob_"+UUID.nameUUIDFromBytes((tenantId+"\0"+deliveryKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jobRepo.lockDeliveryKey(id);
        var existing=jobRepo.findById(id);
        if(existing.isPresent()) {
            var job=existing.get();var tasks=taskRepo.listByJob(id);
            if(job.jobType()!=jobType||!Objects.equals(job.aggregateType(),aggregateType)||!Objects.equals(job.aggregateId(),aggregateId)
                ||!Objects.equals(job.tenantId(),tenantId)||!Objects.equals(job.projectId(),projectId)||!Objects.equals(job.payloadJson(),payloadJson)
                ||tasks.size()!=1||!Objects.equals(tasks.getFirst().taskType(),taskType)||tasks.getFirst().capability()!=capability)
                throw new IllegalArgumentException("Delivery key reused for a different task intent");
            return job;
        }
        jobRepo.createWithId(id,jobType,aggregateType,aggregateId,tenantId,projectId,payloadJson);
        createTask(id,taskType,capability,null,0);
        return jobRepo.findById(id).orElseThrow();
    }

    public List<PlatformTask> listTasks(String jobId) {
        return taskRepo.listByJob(jobId);
    }

}
