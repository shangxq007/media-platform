package com.example.platform.render.infrastructure.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Worker Scheduler - periodically polls the queue for jobs.
 */
@ConditionalOnProperty(prefix = "app.render.worker-queue", name = "enabled", havingValue = "true")
public class WorkerScheduler {

    private final RenderWorkerService workerService;

    public WorkerScheduler(RenderWorkerService workerService) {
        this.workerService = workerService;
    }

    /**
     * Poll the queue every 5 seconds.
     */
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
