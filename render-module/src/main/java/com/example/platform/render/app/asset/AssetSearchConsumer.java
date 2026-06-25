package com.example.platform.render.app.asset;

import com.example.platform.outbox.app.PlatformCoordinationService;
import com.example.platform.outbox.domain.JobType;
import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.shared.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes asset domain events and triggers search reindex jobs.
 *
 * <p>This consumer does NOT execute the reindex directly. It creates a
 * platform_job with a REINDEX task. The PlatformTaskDispatcher picks up
 * the task and delegates to SearchReindexTaskHandler.</p>
 */
@Component
public class AssetSearchConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssetSearchConsumer.class);
    private final PlatformCoordinationService coordinationService;

    public AssetSearchConsumer(PlatformCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    @EventListener
    public void onAssetEnriched(AssetEnrichedEvent event) {
        triggerReindex(event.assetId(), "", "", "asset.enriched");
    }

    @EventListener
    public void onAssetPublished(AssetPublishedEvent event) {
        triggerReindex(event.assetId(), "", event.projectId(), "asset.published");
    }

    @EventListener
    public void onAssetArchived(AssetArchivedEvent event) {
        triggerReindex(event.assetId(), "", event.projectId(), "asset.archived");
    }

    private void triggerReindex(String assetId, String tenantId, String projectId, String eventType) {
        log.info("Search reindex triggered: asset={} tenant={} event={}", assetId, tenantId, eventType);
        String payload = "{\"assetId\":\"" + assetId + "\",\"tenantId\":\"" + tenantId
                + "\",\"projectId\":\"" + projectId + "\",\"reason\":\"" + eventType + "\"}";
        var job = coordinationService.createJob(JobType.SEARCH_REINDEX, "ASSET", assetId,
                tenantId, projectId, payload);
        coordinationService.createTask(job.id(), "REINDEX", TaskCapability.REINDEX, null, 0);
        log.info("Search reindex job created: job={} task=REINDEX", job.id());
    }
}
