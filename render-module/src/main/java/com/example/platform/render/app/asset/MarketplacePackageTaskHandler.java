package com.example.platform.render.app.asset;

import com.example.platform.outbox.app.TaskHandler;
import com.example.platform.outbox.app.TaskExecutionContext;
import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.render.domain.asset.marketplace.MarketplaceListing;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Packages an asset for marketplace — builds a listing draft and persists it.
 */
@Component
public class MarketplacePackageTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketplacePackageTaskHandler.class);
    private final MarketplaceListingBuilder builder;
    private final MarketplaceListingRepository listingRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public MarketplacePackageTaskHandler(MarketplaceListingBuilder builder,
                                           MarketplaceListingRepository listingRepo) {
        this.builder = builder;
        this.listingRepo = listingRepo;
    }

    @Override
    public TaskCapability capability() {
        return TaskCapability.PACKAGE;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        String assetId = extractField(context.payload(), "assetId");
        String projectId = extractField(context.payload(), "projectId");
        String tenantId = extractField(context.payload(), "tenantId");
        if (tenantId == null || tenantId.isBlank()) tenantId = "system";
        log.info("MarketplacePackageHandler: building listing for asset={} tenant={}", assetId, tenantId);

        MarketplaceListing draft = builder.buildDraft(assetId, tenantId, projectId);
        listingRepo.upsert(draft);

        log.info("MarketplacePackageHandler: listing persisted id={} asset={} status={}",
                draft.id(), assetId, draft.status());
    }

    private String extractField(String payload, String field) {
        try {
            return (String) mapper.readValue(payload, Map.class).getOrDefault(field, "");
        } catch (Exception e) { return ""; }
    }
}
