package com.example.platform.render.app.asset;

import com.example.platform.outbox.app.PlatformCoordinationService;
import com.example.platform.outbox.domain.*;
import com.example.platform.shared.events.AssetPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes asset published events and triggers marketplace preparation jobs.
 */
@Component
public class MarketplaceConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceConsumer.class);
    private final PlatformCoordinationService coordinator;

    public MarketplaceConsumer(PlatformCoordinationService coordinator) {
        this.coordinator = coordinator;
    }

    @EventListener
    public void onAssetPublished(AssetPublishedEvent event) {
        log.info("Marketplace consumer: asset published asset={}", event.assetId());
        String payload = "{\"assetId\":\"" + event.assetId()
                + "\",\"projectId\":\"" + event.projectId()
                + "\",\"assetType\":\"" + event.assetType() + "\"}";
        var job = coordinator.createJob(JobType.MARKETPLACE_PREPARE, "ASSET", event.assetId(),
                null, event.projectId(), payload);
        coordinator.createTask(job.id(), "VALIDATE", TaskCapability.VALIDATE, null, 0);
        coordinator.createTask(job.id(), "PACKAGE", TaskCapability.PACKAGE, null, 1);
        log.info("Marketplace prepare job created: job={} tasks=VALIDATE,PACKAGE", job.id());
    }
}
