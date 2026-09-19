package com.example.platform.render.app.asset;

import com.example.platform.outbox.coordination.PlatformCoordinationService;
import com.example.platform.outbox.coordination.JobType;
import com.example.platform.sandbox.execution.TaskCapability;
import com.example.platform.marketplace.api.event.*;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef.MediaAssetSubject;
import com.example.platform.artifact.api.event.AssetEnrichedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes asset domain events and triggers search reindex jobs.
 *
 * <p>This consumer does NOT execute the reindex directly. It creates a
 * platform_job with a REINDEX delivery intent. Execution lifecycle authority is deliberately
 * absent from the outbox coordination module.</p>
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
        try {
            String payload=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new java.util.TreeMap<>(java.util.Map.of(
                "assetId",event.assetId(),"tenantId",event.tenantId(),"projectId",event.projectId(),"reason","asset.enriched")));
            coordinationService.createJobWithTaskOnce(event.factKey(),JobType.SEARCH_REINDEX,"ASSET",event.assetId(),
                event.tenantId(),event.projectId(),payload,"REINDEX",TaskCapability.REINDEX);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid search intent",e);}
    }

    @EventListener
    public void onMarketplaceListingPublished(MarketplaceListingPublishedEvent event) {
        triggerReindex(event.reference(),event.factKey(),"marketplace.listing.published");
    }
    @EventListener
    public void onMarketplaceListingArchived(MarketplaceListingArchivedEvent event) {
        triggerReindex(event.reference(),event.factKey(),"marketplace.listing.archived");
    }
    private void triggerReindex(MarketplaceEventReference ref,String factKey,String eventType) {
        var delivery=com.example.platform.outbox.api.event.OutboxDeliveryContext.require();
        if(!delivery.tenantId().equals(ref.scope().tenantId()))throw new IllegalArgumentException("Marketplace fact tenant mismatch");
        var subject=(MediaAssetSubject)ref.subject();
        try {
            String payload=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    java.util.Map.of("assetId",subject.assetId().value(),"tenantId",ref.scope().tenantId(),"projectId",ref.scope().projectId(),"reason",eventType));
            coordinationService.createJobWithTaskOnce(factKey,JobType.SEARCH_REINDEX,"ASSET",subject.assetId().value(),
                    ref.scope().tenantId(),ref.scope().projectId(),payload,"REINDEX",TaskCapability.REINDEX);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid search intent",e);}
    }
}
