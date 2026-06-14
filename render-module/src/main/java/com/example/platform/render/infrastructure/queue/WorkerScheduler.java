package com.example.platform.render.infrastructure.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker Scheduler - periodically polls the queue for jobs.
 */
@Component
@ConditionalOnProperty(prefix = "app.render.worker-queue", name = "enabled", havingValue = "true")
public class WorkerScheduler {

    private final RenderWorkerService workerService;

    public WorkerScheduler(RenderWorkerService workerService) {
        this.workerService = workerService;
    }

    /**
     * Poll the queue every 5 seconds.
     */
    @Scheduled(fixedDelayString = "${render.worker.poll-interval-ms:5000}")
    public void pollQueue() {
        if (!workerService.isRunning()) {
            return;
        }

        try {
            workerService.processNextJob();
        } catch (Exception e) {
            // Log but don't crash the scheduler
            System.err.println("Error processing job: " + e.getMessage());
        }
    }
}
