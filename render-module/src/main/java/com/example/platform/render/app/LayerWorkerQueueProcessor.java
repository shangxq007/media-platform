package com.example.platform.render.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drains Remotion / Blender worker queues (L3/L4). */
@Component
@ConditionalOnProperty(prefix = "app.render.worker-queue", name = "consume-enabled", havingValue = "true")
public class LayerWorkerQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(LayerWorkerQueueProcessor.class);

    private final RenderWorkerQueueService queueService;
    private final RenderOrchestratorService orchestratorService;

    public LayerWorkerQueueProcessor(RenderWorkerQueueService queueService,
                                     RenderOrchestratorService orchestratorService) {
        this.queueService = queueService;
        this.orchestratorService = orchestratorService;
    }

    @Scheduled(fixedDelayString = "${app.render.worker-queue.poll-interval-ms:5000}")
    public void processRemotionQueue() {
        queueService.pollRemotion().ifPresent(job -> run(job, "Remotion"));
    }

    @Scheduled(fixedDelayString = "${app.render.worker-queue.poll-interval-ms:5000}")
    public void processBlenderQueue() {
        queueService.pollBlender().ifPresent(job -> run(job, "Blender"));
    }

    private void run(RenderWorkerQueueJob job, String label) {
        log.info("Processing queued {} job {}", label, job.jobId());
        try {
            orchestratorService.finishRenderPhase(job.tenantId(), job.jobId());
        } catch (Exception e) {
            log.error("Queued {} render failed for job {}", label, job.jobId(), e);
        }
    }
}
