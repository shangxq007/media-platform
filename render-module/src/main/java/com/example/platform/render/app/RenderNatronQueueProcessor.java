package com.example.platform.render.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the in-process Natron worker queue (POC). Production would use Temporal / Redis / dedicated workers.
 */
@Component
@ConditionalOnProperty(prefix = "app.render.worker-queue", name = "consume-enabled", havingValue = "true")
public class RenderNatronQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(RenderNatronQueueProcessor.class);

    private final RenderWorkerQueueService queueService;
    private final RenderOrchestratorService orchestratorService;

    public RenderNatronQueueProcessor(RenderWorkerQueueService queueService,
                                      RenderOrchestratorService orchestratorService) {
        this.queueService = queueService;
        this.orchestratorService = orchestratorService;
    }

    @Scheduled(fixedDelayString = "${app.render.worker-queue.poll-interval-ms:5000}")
    public void processNextQueuedJob() {
        queueService.pollNatron().ifPresent(job -> {
            log.info("Processing queued Natron job {} profile={}", job.jobId(), job.profile());
            try {
                orchestratorService.finishRenderPhase(job.tenantId(), job.jobId());
            } catch (Exception e) {
                log.error("Queued Natron render failed for job {}", job.jobId(), e);
            }
        });
    }
}
