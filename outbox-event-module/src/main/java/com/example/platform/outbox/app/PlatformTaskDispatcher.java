package com.example.platform.outbox.app;

import com.example.platform.outbox.domain.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scheduled dispatcher for platform tasks.
 *
 * <p>Polls for PENDING tasks every 3 seconds, leases them, resolves the
 * appropriate handler by capability, executes, and completes or fails.</p>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class PlatformTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PlatformTaskDispatcher.class);
    private static final int BATCH_SIZE = 20;
    private static final int LEASE_TIMEOUT_MINUTES = 15;

    private final PlatformCoordinationService coordinationService;
    private final TaskHandlerRegistry handlerRegistry;
    private final PlatformTaskRepository taskRepo;
    private final PlatformJobRepository jobRepo;

    public PlatformTaskDispatcher(PlatformCoordinationService coordinationService,
                                    TaskHandlerRegistry handlerRegistry,
                                    PlatformTaskRepository taskRepo,
                                    PlatformJobRepository jobRepo) {
        this.coordinationService = coordinationService;
        this.handlerRegistry = handlerRegistry;
        this.taskRepo = taskRepo;
        this.jobRepo = jobRepo;
    }

    /**
     * Main dispatch loop — runs every 3 seconds.
     */
    @Scheduled(fixedDelay = 3000)
    public void dispatch() {
        for (TaskCapability cap : TaskCapability.values()) {
            List<PlatformTask> pending = taskRepo.listPendingByCapability(cap, BATCH_SIZE);
            for (PlatformTask task : pending) {
                dispatchTask(task);
            }
        }
    }

    /**
     * Recovery loop — resets stale LEASED tasks to PENDING.
     */
    @Scheduled(fixedDelay = 60_000)
    public void recoverStaleLeases() {
        int reset = taskRepo.resetStaleLeases(LEASE_TIMEOUT_MINUTES);
        if (reset > 0) {
            log.info("Reset {} stale task leases (timeout: {} min)", reset, LEASE_TIMEOUT_MINUTES);
        }
    }

    private void dispatchTask(PlatformTask task) {
        boolean leased = taskRepo.lease(task.id());
        if (!leased) {
            log.debug("Task {} already leased by another dispatcher", task.id());
            return;
        }
        log.info("Task LEASED: id={} capability={} attempt={}/{}",
                task.id(), task.capability(), task.attemptCount() + 1, task.maxAttempts());

        TaskHandler handler = handlerRegistry.resolve(task.capability());
        if (handler == null) {
            coordinationService.failTask(task.id(),
                    "No handler registered for capability: " + task.capability());
            log.error("Task {} FAILED: no handler for capability {}", task.id(), task.capability());
            return;
        }

        PlatformJob job = jobRepo.findById(task.jobId()).orElse(null);
        if (job == null) {
            coordinationService.failTask(task.id(), "Parent job not found: " + task.jobId());
            return;
        }

        try {
            log.info("Task STARTED: id={} capability={}", task.id(), task.capability());
            TaskExecutionContext ctx = TaskExecutionContext.of(job, task);
            handler.execute(ctx);
            coordinationService.completeTask(task.id(), "completed");
            log.info("Task COMPLETED: id={}", task.id());
        } catch (Exception e) {
            log.warn("Task FAILED: id={} capability={} error={}", task.id(), task.capability(), e.getMessage());
            coordinationService.failTask(task.id(), e.getMessage());
        }

        PlatformJob updated = jobRepo.findById(task.jobId()).orElse(null);
        if (updated != null) {
            if (updated.status() == JobStatus.COMPLETED) {
                log.info("Job COMPLETED: id={} type={}", updated.id(), updated.jobType());
            } else if (updated.status() == JobStatus.FAILED) {
                log.warn("Job FAILED: id={} type={}", updated.id(), updated.jobType());
            }
        }
    }
}
